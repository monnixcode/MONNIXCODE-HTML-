package com.monnixcode.workspace

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/** Native status and progress screen for verified Bootstrap installation. */
class BootstrapActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var checksumInput: EditText
    private lateinit var installButton: Button
    private lateinit var chooseButton: Button
    private var selectedArchive: Uri? = null
    private var isInstalling = false
    private val chooseArchiveRequest = 5101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateStatus(BootstrapInstaller.status(this), "เลือกไฟล์ bootstrap.tar.xz และกรอก SHA-256")

        intent.getStringExtra(EXTRA_ARCHIVE_PATH)?.let { path ->
            selectedArchive = Uri.fromFile(File(path))
            updateSelectedFile(File(path).name)
        }
        intent.getStringExtra(EXTRA_SHA256)?.let { checksumInput.setText(it) }
        refreshButtonState()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            setBackgroundColor(Color.rgb(11, 14, 19))
        }

        val title = TextView(this).apply {
            text = "MONNIXCODE / BOOTSTRAP"
            textSize = 20f
            setTextColor(Color.rgb(99, 217, 255))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Verified Termux runtime installer"
            textSize = 13f
            setTextColor(Color.rgb(143, 152, 168))
            setPadding(0, dp(4), 0, dp(16))
        }
        root.addView(subtitle, matchWrap())

        statusView = label("สถานะ: …", 17f, Color.WHITE)
        root.addView(statusView, matchWrap())

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
        }
        root.addView(progressBar, LinearLayout.LayoutParams(-1, dp(12)).apply {
            topMargin = dp(14)
            bottomMargin = dp(10)
        })

        detailView = label("", 13f, Color.rgb(143, 152, 168))
        detailView.setHorizontallyScrolling(false)
        root.addView(detailView, matchWrap())

        val fileLabel = label("Bootstrap archive (.tar.xz)", 14f, Color.rgb(230, 232, 237))
        fileLabel.setPadding(0, dp(24), 0, dp(6))
        root.addView(fileLabel, matchWrap())

        chooseButton = Button(this).apply {
            text = "เลือกไฟล์ archive"
            setOnClickListener { chooseArchive() }
        }
        root.addView(chooseButton, matchWrap())

        checksumInput = EditText(this).apply {
            hint = "SHA-256 checksum (64 hex characters)"
            textSize = 13f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(143, 152, 168))
            setBackgroundColor(Color.rgb(21, 26, 35))
            setPadding(dp(12), 0, dp(12), 0)
            addTextChangedListener(SimpleTextWatcher { refreshButtonState() })
        }
        root.addView(checksumInput, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(10)
        })

        installButton = Button(this).apply {
            text = "ติดตั้ง Bootstrap"
            isAllCaps = false
            setOnClickListener { installSelectedArchive() }
        }
        root.addView(installButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(14)
        })

        val note = label(
            "ระบบจะตรวจ SHA-256 ก่อนแตกไฟล์ และติดตั้งผ่าน staging directory แบบ atomic\n" +
                "ต้องใช้ archive ที่ตรงกับ ABI ของอุปกรณ์เท่านั้น",
            12f,
            Color.rgb(143, 152, 168)
        )
        note.setPadding(0, dp(18), 0, 0)
        root.addView(note, matchWrap())

        setContentView(ScrollView(this).apply {
            addView(root)
            isFillViewport = true
        })
    }

    private fun chooseArchive() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-xz"
        }
        startActivityForResult(intent, chooseArchiveRequest)
    }

    private fun installSelectedArchive() {
        val uri = selectedArchive ?: return
        val checksum = checksumInput.text.toString().trim()
        if (!checksum.matches(Regex("[0-9a-fA-F]{64}"))) {
            checksumInput.error = "ต้องเป็น SHA-256 จำนวน 64 ตัวอักษร"
            return
        }
        setInstalling(true)
        updateStatus("กำลังติดตั้ง", "กำลังเตรียม archive…")

        Thread {
            val temporary = File(cacheDir, "selected-bootstrap-${System.currentTimeMillis()}.tar.xz")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temporary).use { output -> input.copyTo(output) }
                } ?: error("ไม่สามารถเปิดไฟล์ archive ได้")

                BootstrapInstaller.installArchive(
                    context = this,
                    archive = temporary,
                    expectedSha256 = checksum,
                    onProgress = { progress ->
                        mainHandler.post {
                            progressBar.progress = progress.percent
                            updateStatus(progress.phase, progress.message)
                        }
                    }
                )
                mainHandler.post {
                    progressBar.progress = 100
                    updateStatus("สำเร็จ", "ติดตั้ง Bootstrap แล้ว: ${BootstrapInstaller.runtimeRoot(this).path}")
                    Toast.makeText(this, "Bootstrap ติดตั้งสำเร็จ", Toast.LENGTH_SHORT).show()
                    setInstalling(false)
                }
            } catch (error: Throwable) {
                mainHandler.post {
                    progressBar.progress = 0
                    updateStatus("ติดตั้งไม่สำเร็จ", error.message ?: error.javaClass.simpleName)
                    Toast.makeText(this, "Bootstrap installation failed", Toast.LENGTH_LONG).show()
                    setInstalling(false)
                }
            } finally {
                temporary.delete()
            }
        }.start()
    }

    private fun setInstalling(value: Boolean) {
        isInstalling = value
        chooseButton.isEnabled = !value
        installButton.isEnabled = !value && selectedArchive != null
        checksumInput.isEnabled = !value
        if (!value && selectedArchive == null) progressBar.progress = 0
    }

    private fun refreshButtonState() {
        if (::installButton.isInitialized) {
            installButton.isEnabled = !isInstalling && selectedArchive != null &&
                checksumInput.text.toString().trim().matches(Regex("[0-9a-fA-F]{64}"))
        }
    }

    private fun updateSelectedFile(name: String) {
        chooseButton.text = "เลือกแล้ว: $name"
        refreshButtonState()
    }

    private fun updateStatus(status: String, detail: String) {
        statusView.text = "สถานะ: $status"
        detailView.text = detail
    }

    @Deprecated("Use Activity Result APIs when this project migrates the native surface")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != chooseArchiveRequest || resultCode != RESULT_OK) return
        data?.data?.let {
            selectedArchive = it
            updateSelectedFile(it.lastPathSegment ?: "bootstrap.tar.xz")
        }
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.START
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

    companion object {
        const val EXTRA_ARCHIVE_PATH = "com.monnixcode.workspace.EXTRA_ARCHIVE_PATH"
        const val EXTRA_SHA256 = "com.monnixcode.workspace.EXTRA_SHA256"
    }
}
