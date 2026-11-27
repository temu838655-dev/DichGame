package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Màn hình nhật ký riêng; không bao giờ xuất nội dung lỗi lên lớp phụ đề trong game. */
class TranslationLogActivity : Activity() {
    private lateinit var content: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@TranslationLogActivity, 16), Ui.dp(this@TranslationLogActivity, 14),
                Ui.dp(this@TranslationLogActivity, 16), Ui.dp(this@TranslationLogActivity, 12))
            setBackgroundColor(Ui.BG)
        }
        root.addView(TextView(this).apply {
            text = "Nhật ký lỗi & API"; textSize = 22f; setTextColor(Ui.TEXT)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply {
            textSize = 13f
            setPadding(0, Ui.dp(this@TranslationLogActivity, 8), 0, Ui.dp(this@TranslationLogActivity, 10))
        }
        root.addView(status)
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(action("Làm mới") { refresh() }, LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f))
        actions.addView(action("Sao chép") { copyLog() }, LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f))
        actions.addView(action("Chia sẻ") { shareLog() }, LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f))
        actions.addView(action("Xóa") { confirmClear() }, LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f))
        root.addView(actions)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = TextView(this).apply {
            textSize = 11.5f; setTextColor(Color.parseColor("#D5DCE8"))
            typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true)
            setPadding(Ui.dp(this@TranslationLogActivity, 10), Ui.dp(this@TranslationLogActivity, 12),
                Ui.dp(this@TranslationLogActivity, 10), Ui.dp(this@TranslationLogActivity, 12))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun action(label: String, run: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; setOnClickListener { run() }
    }

    private fun refresh() {
        val color = when (TranslationLog.health()) {
            TranslationLog.Health.HEALTHY -> Color.parseColor("#25B979")
            TranslationLog.Health.FALLBACK -> Color.parseColor("#E5A52D")
            TranslationLog.Health.FAILED -> Color.parseColor("#E05252")
        }
        status.text = TranslationLog.statusLabel() + " · " + TranslationLog.healthDetail() +
            "\nKhóa API luôn được che; không ghi câu thoại hoặc payload request."
        status.setTextColor(color)
        content.text = TranslationLog.read().ifBlank {
            "Chưa có nhật ký. Khi có lỗi OCR, API hoặc TTS, sự kiện sẽ xuất hiện tại đây."
        }
    }

    private fun copyLog() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("DichGame runtime log", TranslationLog.read()))
        status.text = "Đã sao chép nhật ký."
    }

    private fun shareLog() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "DichGame runtime log")
            putExtra(Intent.EXTRA_TEXT, TranslationLog.read())
        }
        startActivity(Intent.createChooser(intent, "Chia sẻ nhật ký lỗi"))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setTitle("Xóa nhật ký?")
            .setMessage("Chỉ xóa file nhật ký lỗi trên thiết bị. Không ảnh hưởng khóa API, hồ sơ game hoặc bản dịch đã lưu.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ -> TranslationLog.clear(); content.postDelayed({ refresh() }, 250) }
            .show()
    }
}
