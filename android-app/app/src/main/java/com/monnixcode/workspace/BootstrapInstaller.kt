package com.monnixcode.workspace

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
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
 * The archive must be a tar.xz produced for the device ABI. This class does not
 * create or fake shell binaries; it only verifies and installs an archive supplied
 * by the caller.
 */
object BootstrapInstaller {
    private const val ROOT = "monnixcode-runtime"
    private const val INSTALL_MARKER = "usr/etc/monnixcode-bootstrap-installed"
    private const val INSTALL_METADATA = "bootstrap-install.json"
    private const val MAX_ENTRY_COUNT = 100_000
    private const val COPY_BUFFER_SIZE = 64 * 1024

    /**
     * Preserves the current layout-only behavior for first launch.
     * Use [installArchive] or [installAsset] to install a real bootstrap.
     */
    @Synchronized
    fun install(context: Context) {
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
        requiredFiles: Collection<String> = DEFAULT_REQUIRED_FILES
    ) {
        require(archive.isFile) { "Bootstrap archive does not exist: ${archive.path}" }
        val normalizedExpected = normalizeSha256(expectedSha256)
        require(normalizedExpected.length == 64 && normalizedExpected.all { it in HEX }) {
            "expectedSha256 must be a 64-character hexadecimal SHA-256 digest"
        }

        val actual = sha256(archive)
        if (!MessageDigest.isEqual(
                normalizedExpected.toByteArray(StandardCharsets.US_ASCII),
                actual.toByteArray(StandardCharsets.US_ASCII)
            )
        ) {
            throw SecurityException("Bootstrap SHA-256 mismatch")
        }

        installVerifiedStream(context, FileInputStream(archive), actual, requiredFiles)
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
        requiredFiles: Collection<String> = DEFAULT_REQUIRED_FILES
    ) {
        val cacheDir = File(context.cacheDir, "bootstrap-downloads").apply { mkdirs() }
        val temporary = File(cacheDir, "${UUID.randomUUID()}.tar.xz")
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            installArchive(context, temporary, expectedSha256, requiredFiles)
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
        requiredFiles: Collection<String>
    ) {
        val runtimeParent = context.filesDir
        val target = runtimeRoot(context)
        val staging = File(runtimeParent, ".${ROOT}.staging-${UUID.randomUUID()}")
        val backup = File(runtimeParent, ".${ROOT}.backup-${UUID.randomUUID()}")
        var oldRuntimeMoved = false
        var installed = false

        try {
            staging.mkdirs()
            extractTarXz(source, staging)
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
        } finally {
            source.close()
            if (!installed) staging.deleteRecursively()
            if (installed) backup.deleteRecursively()
            else if (oldRuntimeMoved && !target.exists()) backup.renameTo(target)
        }
    }

    private fun extractTarXz(source: InputStream, destination: File) {
        XZInputStream(BufferedInputStream(source, COPY_BUFFER_SIZE)).use { xz ->
            TarArchiveInputStream(BufferedInputStream(xz, COPY_BUFFER_SIZE)).use { tar ->
                var entries = 0
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    if (++entries > MAX_ENTRY_COUNT) {
                        throw SecurityException("Bootstrap archive contains too many entries")
                    }
                    val output = securePath(destination, entry.name)
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
                            FileOutputStream(output).use { tar.copyTo(it, COPY_BUFFER_SIZE) }
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

    private fun createSafeSymlink(root: File, link: File, entry: TarArchiveEntry) {
        val target = entry.linkName
        requireSafeSymlinkTarget(target)
        val resolvedTarget = File(link.parentFile, target).canonicalFile
        val canonicalRoot = root.canonicalFile
        if (!isInside(canonicalRoot, resolvedTarget)) {
            throw SecurityException("Symlink escapes bootstrap root: ${entry.name}")
        }
        link.parentFile?.mkdirs()
        if (link.exists() || Files.isSymbolicLink(link.toPath())) link.delete()
        Os.symlink(target, link.path)
    }

    private fun validateRequiredFiles(root: File, requiredFiles: Collection<String>) {
        requiredFiles.forEach { relative ->
            val file = securePath(root, relative)
            if (!file.exists()) throw IOException("Required bootstrap file is missing: $relative")
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
        require(path.isNotBlank() && !path.startsWith('/')) { "Unsafe archive path: $path" }
        val normalized = path.replace('\\', '/')
        require(normalized.split('/').none { it == ".." || it.isEmpty() && normalized != "/" }) {
            "Unsafe archive path: $path"
        }
    }

    private fun requireSafeSymlinkTarget(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\u0000')) {
            "Unsafe symlink target: $path"
        }
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSha256(value: String): String =
        value.trim().lowercase().replace(" ", "").replace("\n", "")

    private const val HEX = "0123456789abcdef"

    private val DEFAULT_REQUIRED_FILES = listOf(
        "usr/bin/sh",
        "usr/etc"
    )
}
