package com.monnixcode.workspace

import android.content.Context
import android.os.Build
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

/**
 * Installs a verified Termux-compatible bootstrap into the app-private runtime.
 *
 * The embedded path is selected by ABI and verified at build time. The legacy
 * tar.xz path remains available for maintainers. This class never creates fake
 * shell binaries.
 */
object BootstrapInstaller {
    private const val ROOT = "monnixcode-runtime"
    private const val INSTALL_MARKER = "usr/etc/monnixcode-bootstrap-installed"
    private const val INSTALL_METADATA = "bootstrap-install.json"
    private const val MAX_ENTRY_COUNT = 100_000
    private const val MAX_ZIP_ENTRY_COUNT = 10_000
    private const val MAX_DECOMPRESSED_BYTES = 256L * 1024L * 1024L
    private const val COPY_BUFFER_SIZE = 64 * 1024

    private class ExtractionBudget(private val maxBytes: Long) {
        var totalBytes: Long = 0
            private set

        fun consume(count: Int) {
            val nextTotal = totalBytes + count.toLong()
            if (nextTotal > maxBytes) {
                throw SecurityException(
                    "Bootstrap decompressed data exceeds ${maxBytes / (1024 * 1024)} MiB"
                )
            }
            totalBytes = nextTotal
        }
    }

    data class InstallProgress(
        val percent: Int,
        val phase: String,
        val message: String
    )

    /**
     * Preserves the current layout-only behavior for first launch.
     * Use [installArchive] or [installAsset] to install a real bootstrap.
     */
    @Synchronized
    fun install(context: Context) {
        if (status(context) == "verified") return
        if (installBundled(context)) return
        val root = runtimeRoot(context)
        val manifest = File(root, "bootstrap/manifest.json")
        if (!manifest.exists()) {
            manifest.parentFile?.mkdirs()
            context.assets.open("bootstrap/manifest.json").use { input ->
                manifest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        listOf("usr/bin", "usr/etc", "usr/var/lib", "home", "tmp").forEach {
            File(root, it).mkdirs()
        }
        File(root, "usr/etc/monnixcode-bootstrap-installed").writeText(
            "MONNIXCODE bootstrap layout installed\n",
            StandardCharsets.UTF_8
        )
    }

    /** Installs the ABI-matched Bootstrap archive bundled into the APK. */
    @Synchronized
    fun installBundled(context: Context): Boolean {
        if (status(context) == "verified") return true
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        val archive = when (abi) {
            "arm64-v8a" -> "bootstrap/archives/bootstrap-aarch64.zip"
            "armeabi-v7a" -> "bootstrap/archives/bootstrap-arm.zip"
            "x86" -> "bootstrap/archives/bootstrap-i686.zip"
            "x86_64" -> "bootstrap/archives/bootstrap-x86_64.zip"
            else -> return false
        }
        val sha256 = when (abi) {
            "arm64-v8a" -> "388acf91cc247e7062d80cd8aa791fe72f360515fb78dc54ff45742120facae7"
            "armeabi-v7a" -> "ff77211b7e71366b494d6b7d04211081b94de4a291ec5f783bc4ce77333a0c92"
            "x86" -> "bb397db1a71c65ce8a0928b0c3015c0926f523726214a182e098570d15789afe"
            "x86_64" -> "fb95765f2ffb4cf5648891199dedb820ed19a63334352fb0bc69b7b7c917e002"
            else -> return false
        }
        return try {
            context.assets.open(archive).use { input ->
                installPrefixZip(context, input, sha256)
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Verifies and installs a local tar.xz archive.
     *
     * The archive is first extracted into a staging directory. The current
     * runtime is replaced only after extraction and required-file validation pass.
     */
    @Synchronized
    @Throws(IOException::class, SecurityException::class)
    fun installArchive(
        context: Context,
        archive: File,
        expectedSha256: String,
        requiredFiles: Collection<String> = DEFAULT_REQUIRED_FILES,
        onProgress: (InstallProgress) -> Unit = {}
    ) {
        require(archive.isFile) { "Bootstrap archive does not exist: ${archive.path}" }
        val normalizedExpected = normalizeSha256(expectedSha256)
        require(normalizedExpected.length == 64 && normalizedExpected.all { it in HEX }) {
            "expectedSha256 must be a 64-character hexadecimal SHA-256 digest"
        }

        onProgress(InstallProgress(0, "verify", "กำลังตรวจสอบ SHA-256…"))
        val actual = sha256(archive) { percent ->
            onProgress(InstallProgress(percent.coerceIn(1, 20), "verify", "กำลังคำนวณ SHA-256…"))
        }
        if (!MessageDigest.isEqual(
                normalizedExpected.toByteArray(StandardCharsets.US_ASCII),
                actual.toByteArray(StandardCharsets.US_ASCII)
            )
        ) {
            throw SecurityException("Bootstrap SHA-256 mismatch")
        }

        onProgress(InstallProgress(25, "verify", "ตรวจสอบ SHA-256 ผ่านแล้ว"))
        installVerifiedStream(context, FileInputStream(archive), actual, requiredFiles, onProgress)
    }

    /**
     * Copies an archive from APK assets to a private temporary file, then uses
     * the same checksum and atomic installation path as [installArchive].
     */
    @Synchronized
    @Throws(IOException::class, SecurityException::class)
    fun installAsset(
        context: Context,
        assetPath: String,
        expectedSha256: String,
        requiredFiles: Collection<String> = DEFAULT_REQUIRED_FILES,
        onProgress: (InstallProgress) -> Unit = {}
    ) {
        val cacheDir = File(context.cacheDir, "bootstrap-downloads").apply { mkdirs() }
        val temporary = File(cacheDir, "${UUID.randomUUID()}.tar.xz")
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            installArchive(context, temporary, expectedSha256, requiredFiles, onProgress)
        } finally {
            temporary.delete()
        }
    }

    fun status(context: Context): String {
        val root = runtimeRoot(context)
        val marker = File(root, INSTALL_MARKER)
        val metadata = File(root, INSTALL_METADATA)
        return when {
            marker.exists() && metadata.exists() -> "verified"
            marker.exists() -> "layout-installed"
            else -> "not-installed"
        }
    }

    fun runtimeRoot(context: Context): File = File(context.filesDir, ROOT)

    private fun installVerifiedStream(
        context: Context,
        source: InputStream,
        sha256: String,
        requiredFiles: Collection<String>,
        onProgress: (InstallProgress) -> Unit
    ) {
        val runtimeParent = context.filesDir
        val target = runtimeRoot(context)
        val staging = File(runtimeParent, ".${ROOT}.staging-${UUID.randomUUID()}")
        val backup = File(runtimeParent, ".${ROOT}.backup-${UUID.randomUUID()}")
        var oldRuntimeMoved = false
        var installed = false

        try {
            staging.mkdirs()
            onProgress(InstallProgress(30, "extract", "กำลังแตกไฟล์ tar.xz…"))
            extractTarXz(source, staging, onProgress)
            onProgress(InstallProgress(88, "validate", "กำลังตรวจสอบไฟล์ bootstrap…"))
            validateRequiredFiles(staging, requiredFiles)
            File(staging, INSTALL_MARKER).apply {
                parentFile?.mkdirs()
                writeText("MONNIXCODE verified bootstrap installed\n", StandardCharsets.UTF_8)
            }
            File(staging, INSTALL_METADATA).writeText(
                "{\n" +
                    "  \"sha256\": \"$sha256\",\n" +
                    "  \"installedAtEpochMs\": ${System.currentTimeMillis()}\n" +
                    "}\n",
                StandardCharsets.UTF_8
            )
            onProgress(InstallProgress(94, "activate", "กำลังเปิดใช้งาน runtime แบบ atomic…"))

            // renameTo is atomic when source and destination are on the same filesystem.
            if (target.exists() && !target.renameTo(backup)) {
                throw IOException("Could not move the existing bootstrap to backup")
            }
            oldRuntimeMoved = backup.exists()
            if (!staging.renameTo(target)) {
                if (oldRuntimeMoved) backup.renameTo(target)
                throw IOException("Could not atomically activate the new bootstrap")
            }
            installed = true
            onProgress(InstallProgress(100, "complete", "ติดตั้ง Bootstrap สำเร็จ"))
        } finally {
            source.close()
            if (!installed) staging.deleteRecursively()
            if (installed) backup.deleteRecursively()
            else if (oldRuntimeMoved && !target.exists()) backup.renameTo(target)
        }
    }

    private fun installPrefixZip(context: Context, source: InputStream, sha256: String) {
        val runtimeParent = context.filesDir
        val target = runtimeRoot(context)
        val staging = File(runtimeParent, ".${ROOT}.staging-${UUID.randomUUID()}")
        val backup = File(runtimeParent, ".${ROOT}.backup-${UUID.randomUUID()}")
        var oldRuntimeMoved = false
        var installed = false
        try {
            staging.mkdirs()
            val prefix = File(staging, "usr").apply { mkdirs() }
            extractPrefixZip(source, prefix)
            restoreRequiredExecutableModes(staging)
            validateRequiredFiles(staging, DEFAULT_REQUIRED_FILES)
            File(staging, INSTALL_MARKER).apply {
                parentFile?.mkdirs()
                writeText("MONNIXCODE embedded Bootstrap installed\n", StandardCharsets.UTF_8)
            }
            File(staging, INSTALL_METADATA).writeText(
                "{\n" +
                    "  \"sha256\": \"$sha256\",\n" +
                    "  \"source\": \"termux-packages embedded archive\",\n" +
                    "  \"installedAtEpochMs\": ${System.currentTimeMillis()}\n" +
                    "}\n",
                StandardCharsets.UTF_8
            )
            if (target.exists() && !target.renameTo(backup)) {
                throw IOException("Could not move the existing bootstrap to backup")
            }
            oldRuntimeMoved = backup.exists()
            if (!staging.renameTo(target)) {
                if (oldRuntimeMoved) backup.renameTo(target)
                throw IOException("Could not atomically activate embedded bootstrap")
            }
            installed = true
        } finally {
            source.close()
            if (!installed) staging.deleteRecursively()
            if (installed) backup.deleteRecursively()
            else if (oldRuntimeMoved && !target.exists()) backup.renameTo(target)
        }
    }

    private fun extractPrefixZip(source: InputStream, prefix: File) {
        val symlinks = mutableListOf<Pair<String, String>>()
        val budget = ExtractionBudget(MAX_DECOMPRESSED_BYTES)
        var entries = 0
        ZipArchiveInputStream(BufferedInputStream(source, COPY_BUFFER_SIZE)).use { zip ->
            while (true) {
                val entry = zip.nextZipEntry ?: break
                if (++entries > MAX_ZIP_ENTRY_COUNT) {
                    throw SecurityException("Bootstrap ZIP contains too many entries")
                }
                if (entry.name == "SYMLINKS.txt") {
                    symlinks += readTextWithBudget(zip, budget)
                        .lineSequence()
                        .mapNotNull { line ->
                            val parts = line.split('←', limit = 2)
                            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                        }
                        .toList()
                    continue
                }
                val output = securePath(prefix, entry.name)
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { copyWithBudget(zip, it, budget) }
                    if (entry.unixMode != 0) applyMode(output, entry.unixMode)
                }
            }
        }
        symlinks.forEach { (target, link) ->
            val linkFile = securePath(prefix, link)
            createSafeSymlink(prefix, linkFile, target)
        }
    }

    private fun createSafeSymlink(root: File, link: File, target: String) {
        val safeTarget = normalizeSymlinkTarget(root, link, target)
        val resolved = File(link.parentFile, safeTarget).canonicalFile
        if (!isInside(root.canonicalFile, resolved)) {
            throw SecurityException("Bootstrap symlink escapes prefix: $target")
        }
        link.parentFile?.mkdirs()
        if (link.exists() || Files.isSymbolicLink(link.toPath())) link.delete()
        Os.symlink(safeTarget, link.path)
    }

    private fun extractTarXz(
        source: InputStream,
        destination: File,
        onProgress: (InstallProgress) -> Unit
    ) {
        val budget = ExtractionBudget(MAX_DECOMPRESSED_BYTES)
        XZInputStream(BufferedInputStream(source, COPY_BUFFER_SIZE)).use { xz ->
            TarArchiveInputStream(BufferedInputStream(xz, COPY_BUFFER_SIZE)).use { tar ->
                var entries = 0
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    if (++entries > MAX_ENTRY_COUNT) {
                        throw SecurityException("Bootstrap archive contains too many entries")
                    }
                    val output = securePath(destination, entry.name)
                    onProgress(
                        InstallProgress(
                            (30 + (entries.coerceAtMost(58))),
                            "extract",
                            "กำลังแตกไฟล์: ${entry.name}"
                        )
                    )
                    when {
                        entry.isDirectory -> {
                            output.mkdirs()
                            applyMode(output, entry.mode)
                        }
                        entry.isSymbolicLink -> {
                            createSafeSymlink(destination, output, entry)
                        }
                        entry.isFile -> {
                            output.parentFile?.mkdirs()
                            FileOutputStream(output).use { copyWithBudget(tar, it, budget) }
                            applyMode(output, entry.mode)
                        }
                        else -> {
                            throw SecurityException("Unsupported tar entry type: ${entry.name}")
                        }
                    }
                }
            }
        }
    }

    private fun copyWithBudget(input: InputStream, output: FileOutputStream, budget: ExtractionBudget) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            budget.consume(count)
            output.write(buffer, 0, count)
        }
    }

    private fun readTextWithBudget(input: InputStream, budget: ExtractionBudget): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            budget.consume(count)
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun createSafeSymlink(root: File, link: File, entry: TarArchiveEntry) {
        val target = entry.linkName
        val safeTarget = normalizeSymlinkTarget(root, link, target)
        val resolvedTarget = File(link.parentFile, safeTarget).canonicalFile
        val canonicalRoot = root.canonicalFile
        if (!isInside(canonicalRoot, resolvedTarget)) {
            throw SecurityException("Symlink escapes bootstrap root: ${entry.name}")
        }
        link.parentFile?.mkdirs()
        if (link.exists() || Files.isSymbolicLink(link.toPath())) link.delete()
        Os.symlink(safeTarget, link.path)
    }

    private fun validateRequiredFiles(root: File, requiredFiles: Collection<String>) {
        requiredFiles.forEach { relative ->
            val file = securePath(root, relative)
            if (!file.exists()) throw IOException("Required bootstrap file is missing: $relative")
        }
    }

    private fun restoreRequiredExecutableModes(root: File) {
        listOf("usr/bin/sh", "usr/bin/pkg", "usr/bin/apt", "usr/bin/nix").forEach { relative ->
            val file = securePath(root, relative)
            if (file.exists()) {
                try {
                    Os.chmod(file.canonicalFile.path, 0x1C0)
                } catch (_: Exception) {
                    // Validation below still requires the path to exist; platforms that
                    // reject chmod will fail the emulator executable check explicitly.
                }
            }
        }
    }

    private fun securePath(root: File, relative: String): File {
        requireSafeRelativePath(relative)
        val rootCanonical = root.canonicalFile
        val result = File(root, relative).canonicalFile
        if (!isInside(rootCanonical, result)) {
            throw SecurityException("Archive path escapes bootstrap root: $relative")
        }
        result.parentFile?.let { parent ->
            if (!isInside(rootCanonical, parent.canonicalFile)) {
                throw SecurityException("Archive parent escapes bootstrap root: $relative")
            }
        }
        return result
    }

    private fun requireSafeRelativePath(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\u0000')) {
            "Unsafe archive path: $path"
        }
        // ZIP directory entries commonly end with '/', e.g. "bin/". Remove
        // only trailing separators for validation; traversal checks remain strict.
        val normalized = path.replace('\\', '/').trimEnd('/')
        require(normalized.isNotBlank() && normalized.split('/').none { it == ".." || it.isEmpty() }) {
            "Unsafe archive path: $path"
        }
    }

    private fun requireSafeSymlinkTarget(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\u0000')) {
            "Unsafe symlink target: $path"
        }
    }

    private fun normalizeSymlinkTarget(root: File, link: File, target: String): String {
        if (!target.startsWith('/')) {
            requireSafeSymlinkTarget(target)
            return target
        }

        val termuxUsr = "/data/data/com.termux/files/usr"
        require(target == termuxUsr || target.startsWith("$termuxUsr/")) {
            "Unsafe symlink target: $target"
        }
        val suffix = target.removePrefix(termuxUsr).trimStart('/')
        val resolved = File(root, if (suffix.isEmpty()) "usr" else "usr/$suffix").canonicalFile
        if (!isInside(root.canonicalFile, resolved)) {
            throw SecurityException("Bootstrap symlink escapes prefix: $target")
        }
        val parent = requireNotNull(link.parentFile) { "Symlink has no parent: $link" }
        val relative = parent.canonicalFile.toPath()
            .relativize(resolved.toPath())
            .toString()
        requireSafeSymlinkTarget(relative)
        return relative
    }

    private fun isInside(root: File, child: File): Boolean {
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        return child.path == root.path || child.path.startsWith(rootPath)
    }

    private fun applyMode(file: File, mode: Int) {
        // Android's Java File API has no portable chmod operation. Best effort is
        // sufficient for archives; executable bits are restored through Os.chmod.
        try {
            Os.chmod(file.path, mode and 0x1FF)
        } catch (_: Exception) {
            // Some filesystems or archive entries may reject chmod; keep extraction safe.
        }
    }

    private fun sha256(file: File, onProgress: (Int) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val total = file.length().coerceAtLeast(1L)
        var processed = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                processed += count
                onProgress(((processed * 100L) / total).toInt().coerceIn(0, 100))
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSha256(value: String): String =
        value.trim().lowercase().replace(" ", "").replace("\n", "")

    private const val HEX = "0123456789abcdef"

    private val DEFAULT_REQUIRED_FILES = listOf(
        "usr/bin/sh",
        "usr/bin/pkg",
        "usr/bin/apt",
        "usr/etc"
    )
}
