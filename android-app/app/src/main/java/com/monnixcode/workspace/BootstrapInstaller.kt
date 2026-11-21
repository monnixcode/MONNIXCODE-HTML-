package com.monnixcode.workspace

import android.content.Context
import java.io.File

/**
 * Installs the bundled bootstrap layout on first launch.
 *
 * The manifest is intentionally explicit: a real Linux/Termux bootstrap must be
 * produced from a matching termux-packages build for every ABI. This project
 * does not fabricate binaries or silently ship a non-working shell.
 */
object BootstrapInstaller {
    private const val ROOT = "monnixcode-runtime"

    fun install(context: Context) {
        val root = File(context.filesDir, ROOT)
        val manifest = File(root, "bootstrap/manifest.json")
        if (!manifest.exists()) {
            manifest.parentFile?.mkdirs()
            context.assets.open("bootstrap/manifest.json").use { input -> manifest.outputStream().use { input.copyTo(it) } }
        }
        listOf("usr/bin", "usr/etc", "usr/var/lib", "home", "tmp").forEach {
            File(root, it).mkdirs()
        }
        File(root, "usr/etc/monnixcode-bootstrap-installed").writeText(
            "MONNIXCODE bootstrap layout installed\n"
        )
    }

    fun status(context: Context): String {
        val marker = File(context.filesDir, "$ROOT/usr/etc/monnixcode-bootstrap-installed")
        return if (marker.exists()) "layout-installed" else "not-installed"
    }
}
