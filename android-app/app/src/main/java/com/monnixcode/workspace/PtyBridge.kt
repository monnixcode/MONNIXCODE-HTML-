package com.monnixcode.workspace

import java.io.File
import java.nio.charset.Charset

internal object PtyBridge {
    init {
        System.loadLibrary("monnixcode_pty")
    }

    external fun nativeStart(
        shellPath: String,
        workingDirectory: String,
        environment: Array<String>,
        columns: Int,
        rows: Int
    ): Int

    external fun nativeRead(timeoutMillis: Int): ByteArray
    external fun nativeWrite(input: ByteArray): Int
    external fun nativeResize(columns: Int, rows: Int): Boolean
    external fun nativeStop()
}

/** Owns one real interactive shell session backed by the native PTY. */
internal class NativeShellSession(
    private val shellPath: File,
    private val runtimeRoot: File,
    private val onOutput: (String) -> Unit,
    private val onClosed: (String) -> Unit
) {
    private var reader: Thread? = null
    @Volatile private var running = false

    fun start(): Boolean {
        if (running || !shellPath.isFile || !shellPath.canExecute()) return false
        val prefix = File(runtimeRoot, "usr")
        val home = File(runtimeRoot, "home").apply { mkdirs() }
        val tmp = File(runtimeRoot, "tmp").apply { mkdirs() }
        val environment = arrayOf(
            "MONNIXCODE_ROOT=${runtimeRoot.absolutePath}",
            "PREFIX=${prefix.absolutePath}",
            "HOME=${home.absolutePath}",
            "TMPDIR=${tmp.absolutePath}",
            "PATH=${File(prefix, "bin").absolutePath}:${File(prefix, "sbin").absolutePath}:/system/bin:/system/xbin",
            "TERMUX_PREFIX=${prefix.absolutePath}",
            "TERMUX_HOME=${home.absolutePath}",
            "LD_LIBRARY_PATH=${File(prefix, "lib").absolutePath}",
            "LANG=en_US.UTF-8",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "SHELL=${shellPath.absolutePath}"
        )
        val pid = PtyBridge.nativeStart(
            shellPath.absolutePath,
            home.absolutePath,
            environment,
            120,
            40
        )
        if (pid < 0) return false
        running = true
        reader = Thread({ readLoop() }, "monnixcode-pty-reader").also { it.start() }
        return true
    }

    fun write(command: String) {
        if (!running) return
        PtyBridge.nativeWrite(command.toByteArray(Charset.defaultCharset()))
    }

    fun resize(columns: Int, rows: Int) {
        if (running) PtyBridge.nativeResize(columns, rows)
    }

    fun stop() {
        if (!running) return
        running = false
        PtyBridge.nativeStop()
        reader?.interrupt()
        reader = null
    }

    private fun readLoop() {
        try {
            while (running) {
                val data = PtyBridge.nativeRead(250)
                if (data.isNotEmpty()) onOutput(String(data, Charset.defaultCharset()))
            }
        } catch (error: Throwable) {
            if (running) onClosed(error.message ?: "PTY reader stopped")
        } finally {
            running = false
        }
    }
}
