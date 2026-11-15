package com.dichgame.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Toast

/**
 * Sửa bản dịch sai: liệt kê các câu vừa dịch (chỉ nằm trong bộ nhớ), người dùng sửa lại rồi lưu vào gói Việt hoá của game đang chọn.
 * Lần sau gặp đúng câu gốc đó, app dùng bản đã sửa (đứng trước dịch AI và dịch trên máy).
 */
class CorrectActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Prefs(this)
        val list = RecentLines.snapshot().take(15)
        screen("Sửa bản dịch") {
            put(Ui.tv(this@CorrectActivity,
                "Các câu vừa dịch (mới nhất ở trên). Sửa bản dịch rồi bấm Lưu: lần sau gặp đúng câu gốc này, app dùng bản bạn sửa cho hồ sơ \"${p.profile}\". " +
                    "Danh sách chỉ nằm trong bộ nhớ, mất khi bắt đầu lần dịch mới hoặc tắt app; chỉ câu bạn bấm Lưu mới được ghi lại.", 13f, Ui.SUB), 0, 12)
            if (list.isEmpty()) {
                put(Ui.tv(this@CorrectActivity, "Chưa có câu nào. Bắt đầu dịch, đợi vài câu hiện ra rồi quay lại đây.", 15f, Ui.TEXT), 0, 12)
            }
            for (e in list) {
                val c = Ui.card(this@CorrectActivity)
                val who = e.speaker?.let { "$it: " }.orEmpty()
                c.addView(Ui.tv(this@CorrectActivity, who + e.orig, 13f, Ui.SUB), Ui.lpm(this@CorrectActivity, 0, 6))
                val f = Ui.field(this@CorrectActivity, "Bản dịch", e.vi)
                f.isSingleLine = false
                f.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                f.minLines = 1
                f.maxLines = 6
                c.addView(f)
                if (e.unsure) c.addView(Ui.tv(this@CorrectActivity, "Bản dịch này app còn nghi ngờ (?).", 12f, Ui.SUB), Ui.lpm(this@CorrectActivity, 4, 0))
                c.addView(Ui.button(this@CorrectActivity, "Lưu bản sửa") {
                    val t = f.text.toString().trim()
                    if (t.isEmpty() || t == e.vi) {
                        Toast.makeText(this@CorrectActivity, "Chưa có gì thay đổi.", Toast.LENGTH_SHORT).show()
                    } else {
                        PackStore.addLines(this@CorrectActivity, p.profile, listOf(e.orig to t))
                        Toast.makeText(this@CorrectActivity, "Đã lưu. Lần sau gặp câu này sẽ dùng bản bạn sửa.", Toast.LENGTH_SHORT).show()
                    }
                }, Ui.lpm(this@CorrectActivity, 8, 0, WRAP_CONTENT))
                put(c, 0, 10)
            }
        }
    }
}
