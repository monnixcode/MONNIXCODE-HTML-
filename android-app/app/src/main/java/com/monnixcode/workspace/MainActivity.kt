package com.monnixcode.workspace

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** Native MONNIXCODE workspace. No WebView is used by the Android application. */
class MainActivity : Activity() {
    private lateinit var terminalOutput: TextView
    private lateinit var commandInput: EditText
    private lateinit var fileOutput: TextView
    private lateinit var previewOutput: TextView
    private lateinit var terminalPanel: View
    private lateinit var filesPanel: View
    private lateinit var themeButton: Button
    private val selectedFiles = linkedMapOf<String, Uri>()
    private val filePickerRequest = 4101
    private val themes = listOf(
        Theme("Classic", Color.rgb(11, 14, 19), Color.rgb(230, 232, 237), Color.rgb(99, 217, 255)),
        Theme("Cyberpunk", Color.rgb(16, 9, 26), Color.rgb(249, 234, 255), Color.rgb(255, 59, 212)),
        Theme("Matrix", Color.rgb(2, 10, 5), Color.rgb(185, 255, 201), Color.rgb(0, 255, 102))
    )
    private var themeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BootstrapInstaller.install(this)
        buildNativeUi()
        printLine("MONNIXCODE NATIVE WORKSPACE", "info")
        printLine("Native Android UI · WebView disabled", "muted")
        printLine("Type help or use the buttons below.", "ok")
    }

    private fun buildNativeUi() {
        val theme = themes[themeIndex]
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(theme.background)
        }
        val headerImage = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            try {
                assets.open("web/assets/monnixcode-header.png").use { setImageBitmap(BitmapFactory.decodeStream(it)) }
            } catch (_: Exception) {
                visibility = View.GONE
            }
        }
        header.addView(headerImage, LinearLayout.LayoutParams(-1, dp(76)))
        val title = label("MONNIXCODE NATIVE WORKSPACE", 13f, theme.text)
        title.gravity = Gravity.CENTER
        header.addView(title, matchWrap())
        root.addView(header, matchWrap())

        val toolbar = horizontalRow(theme.background)
        themeButton = button("Theme: ${theme.name}", theme.accent) { cycleTheme() }
        toolbar.addView(themeButton, weightButton())
        toolbar.addView(button("Bootstrap", theme.accent) {
            startActivity(Intent(this, BootstrapActivity::class.java))
        }, weightButton())
        toolbar.addView(button("Clear", theme.accent) {
            terminalOutput.text = ""
            commandInput.requestFocus()
        }, weightButton())
        root.addView(toolbar, matchWrap())

        val tabs = horizontalRow(theme.background)
        tabs.addView(button("Terminal", theme.accent) { showPanel(true) }, weightButton())
        tabs.addView(button("Files / Folders", theme.accent) { showPanel(false) }, weightButton())
        root.addView(tabs, matchWrap())

        terminalPanel = buildTerminalPanel(theme)
        filesPanel = buildFilesPanel(theme)
        root.addView(terminalPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(filesPanel, LinearLayout.LayoutParams(-1, 0, 1f).apply { height = 0 })
        filesPanel.visibility = View.GONE

        val footer = label("Native mode · Linux shell requires verified Bootstrap + PTY", 10f, Color.rgb(143, 152, 168))
        footer.setPadding(dp(12), dp(7), dp(12), dp(7))
        root.addView(footer, matchWrap())
        setContentView(root)
    }

    private fun buildTerminalPanel(theme: Theme): View {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(theme.background)
        }
        terminalOutput = label("", 13f, theme.text)
        terminalOutput.setPadding(dp(14), dp(14), dp(14), dp(8))
        terminalOutput.setTextIsSelectable(true)
        scroll.addView(terminalOutput, matchWrap())
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val inputRow = horizontalRow(theme.background)
        inputRow.addView(label("user@monnixcode:~$", 11f, Color.rgb(104, 211, 145)), matchWrap())
        commandInput = EditText(this).apply {
            hint = "command"
            setHintTextColor(Color.rgb(143, 152, 168))
            setTextColor(theme.text)
            setSingleLine(true)
            textSize = 13f
            setPadding(dp(8), 0, dp(8), 0)
            setOnEditorActionListener { _, _, _ -> executeCommand(); true }
        }
        inputRow.addView(commandInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        inputRow.addView(button("Run", theme.accent) { executeCommand() }, matchWrap())
        panel.addView(inputRow, matchWrap())
        return panel
    }

    private fun buildFilesPanel(theme: Theme): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(theme.background)
        }
        val actions = horizontalRow(theme.background)
        actions.addView(button("Choose files", theme.accent) { chooseFiles() }, weightButton())
        actions.addView(button("Clear", theme.accent) {
            selectedFiles.clear()
            renderFiles()
            previewOutput.text = "เลือกไฟล์เพื่อดู preview"
        }, weightButton())
        panel.addView(actions, matchWrap())

        fileOutput = label("ยังไม่มีไฟล์ใน workspace", 12f, theme.text)
        fileOutput.setPadding(dp(10), dp(12), dp(10), dp(12))
        fileOutput.setOnClickListener { openSelectedFilePreview() }
        panel.addView(fileOutput, LinearLayout.LayoutParams(-1, 0, 1f))

        previewOutput = label("เลือกไฟล์เพื่อดู preview", 11f, Color.rgb(203, 211, 223))
        previewOutput.setPadding(dp(10), dp(10), dp(10), dp(10))
        panel.addView(previewOutput, LinearLayout.LayoutParams(-1, dp(150)))
        return panel
    }

    private fun executeCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isEmpty()) return
        printLine("user@monnixcode:~$ $command", "command")
        commandInput.text.clear()
        val args = command.split(Regex("\\s+"))
        when (args.firstOrNull()) {
            "clear", "cls" -> terminalOutput.text = ""
            "help" -> listOf(
                "help, clear, pwd, whoami, date, uname, echo <text>",
                "ls, git status, git help, open <file>",
                "Bootstrap button opens the native verified installer.",
                "Full Linux commands require a verified runtime and PTY."
            ).forEach { printLine(it, "info") }
            "pwd" -> printLine("/data/data/com.monnixcode.workspace/files/monnixcode-runtime/home", "ok")
            "whoami" -> printLine("u0_a${android.os.Process.myUid()}", "ok")
            "date" -> printLine(DateFormat.getDateTimeInstance().format(Date()), "ok")
            "uname" -> printLine("MONNIXCODE Android native workspace", "info")
            "echo" -> printLine(args.drop(1).joinToString(" "), "ok")
            "ls" -> {
                if (selectedFiles.isEmpty()) printLine("workspace is empty; use Choose files.", "muted")
                else selectedFiles.keys.forEach { printLine(it, "ok") }
            }
            "git" -> when (args.getOrNull(1)) {
                "status" -> printLine("On native workspace; ${selectedFiles.size} file(s) selected.", "info")
                "help" -> printLine("Native UI supports status only. Use Termux for full git operations.", "warn")
                else -> printLine("git: use git status or git help", "warn")
            }
            "open" -> {
                val name = args.drop(1).joinToString(" ")
                if (selectedFiles.containsKey(name)) previewFile(name) else printLine("open: file not found", "err")
            }
            else -> printLine("${args.firstOrNull()}: command not found. Try help.", "err")
        }
    }

    private fun chooseFiles() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, filePickerRequest)
    }

    @Deprecated("Use Activity Result APIs when the native surface expands")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != filePickerRequest || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
        uris.distinct().forEach { uri -> selectedFiles[displayName(uri)] = uri }
        renderFiles()
    }

    private fun renderFiles() {
        fileOutput.text = if (selectedFiles.isEmpty()) {
            "ยังไม่มีไฟล์ใน workspace"
        } else {
            selectedFiles.keys.joinToString("\n") { "▤  $it" } + "\n\nแตะรายการเพื่อ preview"
        }
        fileOutput.setOnClickListener { selectedFiles.keys.firstOrNull()?.let { previewFile(it) } }
    }

    private fun openSelectedFilePreview() = selectedFiles.keys.firstOrNull()?.let { previewFile(it) }

    private fun previewFile(name: String) {
        val uri = selectedFiles[name] ?: return
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val text = input.readBytes().toString(Charsets.UTF_8)
                previewOutput.text = text.take(200_000)
            }
        } catch (error: Exception) {
            previewOutput.text = "Cannot preview $name: ${error.message}"
        }
    }

    private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()

    private fun printLine(text: String, style: String) {
        if (!::terminalOutput.isInitialized) return
        val prefix = when (style) {
            "ok" -> "[OK] "
            "info" -> "[INFO] "
            "warn" -> "[WARN] "
            "err" -> "[ERR] "
            "command" -> ""
            else -> ""
        }
        terminalOutput.append(prefix + text + "\n")
    }

    private fun showPanel(showTerminal: Boolean) {
        terminalPanel.visibility = if (showTerminal) View.VISIBLE else View.GONE
        filesPanel.visibility = if (showTerminal) View.GONE else View.VISIBLE
    }

    private fun cycleTheme() {
        themeIndex = (themeIndex + 1) % themes.size
        themeButton.text = "Theme: ${themes[themeIndex].name}"
        Toast.makeText(this, "Theme: ${themes[themeIndex].name}", Toast.LENGTH_SHORT).show()
    }

    private fun horizontalRow(background: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setBackgroundColor(background)
    }

    private fun button(text: String, accent: Int, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 11f
        setTextColor(Color.WHITE)
        setOnClickListener { action() }
        setBackgroundColor(Color.rgb(31, 39, 51))
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun weightButton() = LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Theme(val name: String, val background: Int, val text: Int, val accent: Int)
}
