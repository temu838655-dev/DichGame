package com.dichgame.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Màn hình "Cách dịch": chọn bộ dịch (trên máy / CloudAPI / Gemini), nhập khóa, thử kết nối. */
class TranslateActivity : Activity() {
    private lateinit var p: Prefs

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        render()
    }

    private fun render() {
        screen("Cách dịch") {
            val c = Ui.card(this@TranslateActivity)
            c.addView(Ui.section(this@TranslateActivity, "🌐", "Cách dịch"))
            c.addView(modeSegment())
            c.addView(Ui.tv(this@TranslateActivity,
                if (p.narrate) "Đọc to phụ đề có sẵn, không dịch. Hợp với phim Vietsub, game Việt hóa"
                else "Đọc chữ trên màn hình rồi dịch sang tiếng Việt.", 13f, Ui.SUB).apply {
                setPadding(0, Ui.dp(this@TranslateActivity, 10), 0, Ui.dp(this@TranslateActivity, 6))
            })
            if (!p.narrate && p.lang != "vi") c.addView(packRow())
            c.addView(engineRow("⚙", "Dịch trên máy", null, "Miễn phí, nhưng kém chính xác", Prefs.ENGINE_LOCAL))
            c.addView(Ui.divider(this@TranslateActivity), Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1))
            c.addView(engineRow("🔑", "CloudAPI", "AI HỖ TRỢ", "AI đám mây, cần khóa", Prefs.ENGINE_CLOUD))
            c.addView(Ui.divider(this@TranslateActivity), Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1))
            c.addView(engineRow("✨", "Google Gemini", "AI HỖ TRỢ", "Miễn phí nhưng nhanh hết lượt, cần khóa", Prefs.ENGINE_GEMINI))
            put(c, 0, 12)

            if (p.engine != Prefs.ENGINE_LOCAL) put(aiCard(), 0, 12)
            else put(Ui.tv(this@TranslateActivity,
                "Đang dùng bộ dịch trên máy: chạy offline sau lần tải mô hình đầu tiên. Chọn CloudAPI hoặc Google Gemini để dịch hay hơn, giữ đúng tên và xưng hô.",
                13f, Ui.SUB), 0, 0)
        }
    }

    /** Hàng "Gói ngôn ngữ X → Việt" với nút tải trước mô hình dịch trên máy, để lần đầu chơi khỏi chờ. */
    private fun packRow(): View {
        val row = Ui.hbox(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10))
        row.addView(Ui.tv(this, "Gói ngôn ngữ ${Prefs.langName(p.lang).removePrefix("Tiếng ")} → Việt", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        val b = Ui.button(this, "Đang kiểm tra...") {}
        b.textSize = 13f
        row.addView(b)
        var busy = false
        fun state(done: Boolean) { busy = false; b.text = if (done) "Đã tải ✓" else "Tải về"; b.isEnabled = !done }
        TextTranslator.isDownloaded(p.lang) { state(it) }
        b.setOnClickListener {
            if (busy || !b.isEnabled || b.text == "Đang kiểm tra...") return@setOnClickListener
            busy = true
            b.text = "Đang tải..."
            val t = TextTranslator(this, p, p.lang)
            t.prepare { ok ->
                if (ok) state(true) else {
                    state(false)
                    android.widget.Toast.makeText(this, "Không tải được. Kiểm tra mạng rồi thử lại.", android.widget.Toast.LENGTH_SHORT).show()
                }
                t.close()
            }
        }
        return row
    }

    private fun modeSegment(): View {
        val seg = Ui.hbox(this)
        seg.background = Ui.bg(this, Ui.CARD2, 22)
        fun item(label: String, on: Boolean, click: () -> Unit) = TextView(this).apply {
            text = label; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(if (on) Color.parseColor("#2A1A08") else Ui.TEXT)
            if (on) background = Ui.goldBg(this@TranslateActivity, 20)
            setPadding(Ui.dp(this@TranslateActivity, 12), Ui.dp(this@TranslateActivity, 11), Ui.dp(this@TranslateActivity, 12), Ui.dp(this@TranslateActivity, 11))
            setOnClickListener { click() }
        }
        seg.addView(item("Dịch sang tiếng Việt", !p.narrate) { p.narrate = false; render() }, Ui.lp(0, WRAP_CONTENT, 1f))
        seg.addView(item("Thuyết minh", p.narrate) { p.narrate = true; render() }, Ui.lp(0, WRAP_CONTENT, 1f))
        return seg
    }

    private fun engineRow(icon: String, title: String, badge: String?, sub: String, engine: String): View {
        val row = Ui.hbox(this)
        row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12))
        row.addView(Ui.tv(this, icon, 22f).apply {
            gravity = Gravity.CENTER
            background = Ui.bg(this@TranslateActivity, Ui.CARD2, 22)
        }, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)))
        val col = Ui.vbox(this)
        val t = Ui.hbox(this)
        t.addView(Ui.tv(this, title, 16f, Ui.TEXT, true))
        if (badge != null) t.addView(Ui.badge(this, badge, Ui.OK), Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@TranslateActivity, 8) })
        col.addView(t)
        col.addView(Ui.tv(this, sub, 13f, Ui.SUB))
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@TranslateActivity, 12) })
        val on = p.engine == engine
        row.addView(Ui.tv(this, if (on) "◉" else "○", 26f, if (on) Ui.GOLD else Ui.SUB))
        row.setOnClickListener { p.engine = engine; render() }
        return row
    }

    private fun aiCard(): View {
        val cloud = p.engine == Prefs.ENGINE_CLOUD
        val c = Ui.card(this)
        val url: EditText? = if (cloud) Ui.field(this, "Địa chỉ CloudAPI (vd https://api.example.com/v1)", p.cloudUrl) else null
        val key = Ui.field(this, if (cloud) "Khóa API CloudAPI" else "Khóa API Google Gemini", if (cloud) p.cloudKey else p.geminiKey, true)
        val model = Ui.field(this, "Model", if (cloud) p.cloudModel else p.geminiModel)
        val status = Ui.tv(this, "", 13f, Ui.SUB)
        if (url != null) c.addView(url, Ui.lpm(this, 0, 8))
        c.addView(key, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Lưu và kiểm tra") {
            if (cloud) { p.cloudUrl = url!!.text.toString(); p.cloudKey = key.text.toString(); p.cloudModel = model.text.toString() }
            else { p.geminiKey = key.text.toString(); p.geminiModel = model.text.toString() }
            status.setTextColor(Ui.SUB)
            if (!Ai.enabled(p)) { status.setTextColor(Ui.DANGER); status.text = if (cloud) "Hãy nhập địa chỉ và khóa API." else "Hãy nhập khóa API."; return@button }
            status.text = "Đang kiểm tra..."
            Thread {
                val r = Ai.test(p)
                runOnUiThread {
                    status.setTextColor(if (r.startsWith("OK")) Ui.OK else Ui.DANGER)
                    status.text = if (r.startsWith("OK")) "Kết nối được. Thử dịch \"Hello, how are you?\" → ${r.removePrefix("OK: ")}" else r
                }
            }.start()
        }, Ui.lpm(this, 0, 8, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        c.addView(status, Ui.lpm(this, 0, 8))
        c.addView(model, Ui.lpm(this, 0, 12))
        c.addView(Ui.divider(this), Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1))
        c.addView(ctxRow(), Ui.lpm(this, 12, 0))
        return c
    }

    private fun ctxRow(): View {
        val row = Ui.hbox(this)
        row.addView(Ui.tv(this, "Số câu trước AI xem để hiểu mạch truyện", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        val v = Ui.tv(this, p.ctxCount.toString(), 16f, Ui.TEXT, true)
        v.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0)
        fun step(t: String, d: Int) = Ui.tv(this, t, 20f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER
            background = Ui.bg(this@TranslateActivity, Ui.CARD2, 12)
            setOnClickListener { p.ctxCount = p.ctxCount + d; v.text = p.ctxCount.toString() }
        }
        row.addView(v)
        row.addView(step("−", -1), Ui.lp(Ui.dp(this, 44), Ui.dp(this, 40)))
        row.addView(step("+", 1), Ui.lp(Ui.dp(this, 44), Ui.dp(this, 40)).apply { leftMargin = Ui.dp(this@TranslateActivity, 6) })
        return row
    }
}
