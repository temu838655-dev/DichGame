package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Trang "Cách dịch": chế độ, ngôn ngữ trong game, bộ dịch (trên máy / CloudAPI / Gemini), khóa API và thử kết nối. */
class TranslateActivity : Activity() {
    private lateinit var p: Prefs

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        render()
    }

    private fun watch(et: EditText, save: (String) -> Unit) {
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { save(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun render() {
        val a = this
        screen("Cách dịch") {
            group(null, 14, 4) {
                addView(modeSegment())
                addView(Ui.tv(a,
                    if (p.narrate) "Chỉ đọc nguyên văn phụ đề tiếng Việt, không dịch; phù hợp phim Vietsub và game Việt hóa."
                    else "Dịch chữ trên màn hình sang tiếng Việt. AI Tự động là tùy chọn trong chế độ Dịch.", 13f, Ui.SUB).apply {
                    setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0)
                })
            }
            if (!p.narrate) {
                group("TÙY CHỌN DỊCH", 14) {
                    addView(Ui.switchRow(a, "✦ AI Tự động", "Tự nhận diện tên game khi đủ chứng cứ, ưu tiên bản Việt hóa và bộ nhớ rồi mới gọi Gemini.", p.aiAuto) {
                        p.aiAuto = it
                        render()
                    })
                }
            }
            if (p.aiAuto) {
                group("AI TỰ ĐỘNG", 14) {
                    addView(Ui.tv(a, "Tự nhận biết ngôn ngữ từ chữ OCR khi đủ bằng chứng. Ban đầu dùng cấu hình nguồn hiện tại; tên game chỉ tự nhận diện khi tựa đầy đủ xuất hiện rõ ít nhất hai lượt quét.", 13f, Ui.SUB))
                    addView(Ui.switchRow(a, "Ghi nhớ bản dịch AI", "Lưu câu AI đã kiểm chứng riêng cho từng game; tránh dịch lại.", p.learnTranslations) {
                        p.learnTranslations = it
                    })
                    addView(Ui.switchRow(a, "Giọng riêng từng nhân vật", "Tự lưu giọng theo hồ sơ game khi dùng Gemini TTS.", p.characterVoices) {
                        p.characterVoices = it
                    })
                }
            }
            if (!p.narrate && !p.aiAuto) {
                group("NGÔN NGỮ TRONG GAME", 14) {
                    val src = Prefs.LANGS.filter { it.first != "vi" }
                    addView(Ui.dropRow(a, "Ngôn ngữ chữ trong game", { Prefs.langLabel(p.lang) }, { src.map { Prefs.langLabel(it.first) } }) {
                        p.lang = src[it].first
                        render()
                    })
                    if (p.lang != "vi") {
                        divide()
                        addView(packRow())
                    }
                }
            }
            if (p.aiAuto) {
                group("GEMINI VÀ KHÓA DỰ PHÒNG") {
                    geminiBlock(this)
                    addView(Ui.tv(a, "Chế độ này ưu tiên bản Việt hóa và câu đã nhớ trước khi gọi Gemini. Hết lượt 401/403/429 sẽ tự thử khóa kế tiếp; các khóa trong cùng dự án Google có thể chung hạn mức. Nếu chưa có khóa, ứng dụng thử dịch trên máy khi được hỗ trợ.", 13f, Ui.SUB), Ui.lpm(a, 8, 2))
                }
                group("CLOUDAPI DỰ PHÒNG") {
                    addView(Ui.switchRow(a, "Dùng CloudAPI khi Gemini lỗi", "Gửi câu thoại và ngữ cảnh tới dịch vụ HTTPS bạn cấu hình (ví dụ Groq). Có thể phát sinh phí.", p.autoCloudFallback) {
                        p.autoCloudFallback = it
                        render()
                    })
                    if (p.autoCloudFallback) cloudBlock(this, true)
                }
                group("NGỮ CẢNH AI") { addView(ctxRow()) }
            } else if (!p.narrate) {
                group("BỘ DỊCH", 0) {
                addView(engineRow("Dịch trên máy", null, "Miễn phí, chạy offline, kém chính xác hơn AI", Prefs.ENGINE_LOCAL))
                divide(16)
                addView(engineRow("CloudAPI", "AI", "AI đám mây theo chuẩn OpenAI, cần địa chỉ và khóa", Prefs.ENGINE_CLOUD))
                divide(16)
                addView(engineRow("Google Gemini", "AI", "Miễn phí nhưng nhanh hết lượt, cần khóa", Prefs.ENGINE_GEMINI))
                }
                when (p.engine) {
                Prefs.ENGINE_GEMINI -> {
                    group("KHÓA GOOGLE GEMINI") { geminiBlock(this) }
                    group("TÙY CHỌN AI") { addView(ctxRow()) }
                }
                Prefs.ENGINE_CLOUD -> {
                    group("CLOUDAPI") { cloudBlock(this) }
                    group("TÙY CHỌN AI") { addView(ctxRow()) }
                }
                else -> put(Ui.tv(a,
                    "Đang dùng bộ dịch trên máy: chạy offline sau lần tải mô hình đầu tiên. Chọn CloudAPI hoặc Google Gemini để dịch hay hơn, giữ đúng tên và xưng hô.",
                    13f, Ui.SUB).apply { setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0) }, 12, 0)
                }
            }
        }
    }

    /** Hàng "Gói ngôn ngữ X → Việt" với nút tải trước mô hình dịch trên máy, để lần đầu chơi khỏi chờ. */
    private fun packRow(): View {
        val row = Ui.hbox(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2))
        row.addView(Ui.tv(this, "Gói ${Prefs.langName(p.lang).removePrefix("Tiếng ")} → Việt", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
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
                    Toast.makeText(this, "Không tải được. Kiểm tra mạng rồi thử lại.", Toast.LENGTH_SHORT).show()
                }
                t.close()
            }
        }
        return row
    }

    private fun modeSegment(): View =
        Ui.segmented(this, listOf("Dịch", "Thuyết minh"), if (p.narrate) 1 else 0) { i ->
            p.narrate = i == 1
            render()
        }

    private fun engineRow(title: String, badge: String?, sub: String, engine: String): View {
        val row = Ui.hbox(this)
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14))
        val col = Ui.vbox(this)
        val t = Ui.hbox(this)
        t.addView(Ui.tv(this, title, 16f, Ui.TEXT, true))
        if (badge != null) t.addView(Ui.badge(this, badge, Ui.OK), Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@TranslateActivity, 8) })
        col.addView(t)
        col.addView(Ui.tv(this, sub, 12.5f, Ui.SUB))
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
        val on = p.engine == engine
        row.addView(Ui.tv(this, if (on) "✓" else "", 22f, Ui.GOLD, true).apply { gravity = Gravity.CENTER }, Ui.lp(Ui.dp(this, 28), WRAP_CONTENT))
        row.isClickable = true
        Ui.ripple(row, 22)
        row.setOnClickListener { p.engine = engine; render() }
        return row
    }

    private fun mask(k: String): String = if (k.length <= 10) "••••••" else k.take(4) + "…" + k.takeLast(4)

    /** Khóa Gemini: danh sách khóa (khóa dự phòng tự dùng khi khóa trước hết lượt), thêm khóa, model, thử kết nối. */
    private fun geminiBlock(c: LinearLayout) {
        val a = this
        val keys = p.geminiStoredKeys()
        if (keys.isNotEmpty()) {
            val enabledCount = keys.count { p.isGeminiKeyEnabled(it) }
            val usableCount = keys.count { p.isGeminiKeyEnabled(it) && KeyRing.status(it, p.geminiModel).state == KeyRing.State.OK }
            val cooldownCount = keys.count { p.isGeminiKeyEnabled(it) && KeyRing.status(it, p.geminiModel).state == KeyRing.State.QUOTA }
            val invalidCount = keys.count { p.isGeminiKeyEnabled(it) && KeyRing.status(it, p.geminiModel).state == KeyRing.State.BAD }
            c.addView(
                Ui.tv(a, "Tổng ${keys.size} khóa · bật ${enabledCount} · khả dụng ${usableCount} · tạm nghỉ ${cooldownCount} · cần kiểm tra ${invalidCount} · tắt ${keys.size - enabledCount}", 12.5f, Ui.SUB),
                Ui.lpm(a, 0, 8)
            )
        }
        if (keys.isEmpty()) {
            c.addView(Ui.tv(a, "Chưa có khóa. Lấy khóa miễn phí rồi dán vào ô bên dưới.", 13f, Ui.SUB), Ui.lpm(a, 0, 8))
        }
        keys.forEachIndexed { i, k ->
            val row = Ui.hbox(a)
            row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8))
            val col = Ui.vbox(a)
            col.addView(Ui.tv(a, "Khóa ${i + 1}", 15f, Ui.TEXT, true))
            col.addView(Ui.tv(a, mask(k), 12.5f, Ui.SUB))
            row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
            val enabled = p.isGeminiKeyEnabled(k)
            val st = KeyRing.status(k, p.geminiModel)
            val (label, color) = if (!enabled) "ĐÃ TẮT" to Ui.SUB else when (st.state) {
                KeyRing.State.BAD -> "CẦN KIỂM TRA" to Ui.DANGER
                KeyRing.State.QUOTA -> "TẠM NGHỈ · ${AiRetry.humanWait(st.leftMs)}" to Ui.DANGER
                KeyRing.State.OK -> if (st.active) "ĐANG DÙNG" to Ui.OK else "SẴN SÀNG" to Ui.GOLD
            }
            row.addView(Ui.badge(a, label, color))
            row.addView(Ui.tv(a, if (enabled) "TẮT" else "BẬT", 12f, Ui.GOLD, true).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6))
                setOnClickListener {
                    if (p.setGeminiKeyEnabled(k, !enabled)) render()
                    else Toast.makeText(a, "Không lưu được trạng thái khóa. Hãy thử lại.", Toast.LENGTH_SHORT).show()
                }
            })
            row.addView(Ui.tv(a, "✎", 18f, Ui.TEXT, true).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6))
                setOnClickListener {
                    val edit = Ui.field(a, "Khóa Gemini", k, true)
                    AlertDialog.Builder(a)
                        .setTitle("Sửa khóa ${i + 1}")
                        .setView(edit)
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Lưu") { _, _ ->
                            val updated = edit.text.toString().trim()
                            if (updated.isBlank()) {
                                Toast.makeText(a, "Khóa không được để trống.", Toast.LENGTH_SHORT).show()
                            } else if (updated != k) {
                                val next = keys.toMutableList().also { it[i] = updated }
                                if (p.setGeminiKeys(next)) {
                                    KeyRing.forget(k)
                                    render()
                                } else {
                                    Toast.makeText(a, "Không lưu được khóa mã hóa. Khóa cũ vẫn được giữ.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .show()
                }
            })
            row.addView(Ui.tv(a, "✕", 18f, Ui.DANGER, true).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6))
                setOnClickListener {
                    if (p.setGeminiKeys(keys.filterIndexed { j, _ -> j != i })) render()
                    else Toast.makeText(a, "Không xóa được khóa. Dữ liệu cũ vẫn được giữ.", Toast.LENGTH_LONG).show()
                }
            })
            c.addView(row)
            c.divide()
        }
        val add = Ui.field(a, "Dán khóa Gemini (bắt đầu bằng AIza…)", "", true)
        c.addView(add, Ui.lpm(a, 10, 8))
        c.addView(Ui.button(a, "＋  Thêm khóa") {
            val v = add.text.toString().trim()
            if (v.isEmpty()) { Toast.makeText(a, "Hãy dán khóa vào ô trên.", Toast.LENGTH_SHORT).show(); return@button }
            if (p.setGeminiKeys(p.geminiStoredKeys() + v)) render()
            else Toast.makeText(a, "Không lưu được khóa mã hóa. Hãy kiểm tra bộ nhớ thiết bị.", Toast.LENGTH_LONG).show()
        }, Ui.lpm(a, 0, 4))
        c.addView(Ui.tv(a, "Thêm nhiều khóa: có thể sửa, bật hoặc tắt riêng từng khóa. Khi một khóa hết lượt (lỗi 429) hoặc bị từ chối, app tự chuyển sang khóa kế tiếp và tạm bỏ khóa đó tới giờ hồi (hết lượt theo ngày thì tới nửa đêm giờ Thái Bình Dương). Lưu ý: Google tính hạn mức theo dự án, các khóa tạo trong cùng một dự án dùng chung hạn mức nên không thêm lượt; hãy tạo khóa ở dự án khác.", 12.5f, Ui.SUB), Ui.lpm(a, 6, 4))
        c.addView(Ui.button(a, "Lấy khóa miễn phí ở aistudio.google.com") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
            catch (_: Exception) { Toast.makeText(a, "Không mở được trình duyệt.", Toast.LENGTH_SHORT).show() }
        }, Ui.lpm(a, 8, 4))
        c.divide()
        val model = Ui.field(a, "Model", p.geminiModel)
        watch(model) { p.geminiModel = it }
        c.addView(Ui.tv(a, "Model", 15f, Ui.TEXT, true), Ui.lpm(a, 12, 6))
        c.addView(model)
        val status = Ui.tv(a, "", 13f, Ui.SUB)
        c.addView(Ui.button(a, "Kiểm tra kết nối", true) { runTest(status) }, Ui.lpm(a, 12, 6))
        c.addView(status, Ui.lpm(a, 4, 0))
    }

    private fun cloudBlock(c: LinearLayout, fallback: Boolean = false) {
        val a = this
        val url = Ui.field(a, "Địa chỉ CloudAPI (vd https://api.example.com/v1)", p.cloudUrl)
        val key = Ui.field(a, "Khóa API CloudAPI", p.cloudKey, true)
        val model = Ui.field(a, "Model", p.cloudModel)
        watch(url) { p.cloudUrl = it }
        watch(key) { p.cloudKey = it }
        watch(model) { p.cloudModel = it }
        c.addView(url, Ui.lpm(a, 0, 8))
        c.addView(key, Ui.lpm(a, 0, 8))
        c.addView(model, Ui.lpm(a, 0, 8))
        val status = Ui.tv(a, "", 13f, Ui.SUB)
        c.addView(Ui.button(a, "Kiểm tra kết nối", true) { runTest(status, fallback) }, Ui.lpm(a, 4, 6))
        c.addView(status, Ui.lpm(a, 4, 0))
    }

    private fun runTest(status: TextView, forceCloud: Boolean = false) {
        status.setTextColor(Ui.SUB)
        if (if (forceCloud) !p.cloudUrl.startsWith("https://", ignoreCase = true) || p.cloudKey.isBlank() || p.cloudModel.isBlank() else !Ai.enabled(p)) {
            status.setTextColor(Ui.DANGER)
            status.text = if (p.engine == Prefs.ENGINE_CLOUD) "Hãy nhập địa chỉ và khóa API." else "Hãy thêm khóa API."
            return
        }
        status.text = "Đang kiểm tra..."
        Thread {
            val r = Ai.test(p, forceCloud)
            runOnUiThread {
                status.setTextColor(if (r.startsWith("OK")) Ui.OK else Ui.DANGER)
                status.text = if (r.startsWith("OK")) "Kết nối được. Thử dịch \"Hello, how are you?\" → ${r.removePrefix("OK: ")}" else r
            }
        }.start()
    }

    private fun ctxRow(): View {
        val row = Ui.hbox(this)
        row.addView(Ui.tv(this, "Số câu trước AI xem để hiểu mạch truyện", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        val v = Ui.tv(this, p.ctxCount.toString(), 16f, Ui.TEXT, true)
        v.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0)
        fun step(t: String, d: Int) = Ui.tv(this, t, 20f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER
            background = Ui.bg(this@TranslateActivity, Ui.CARD2, 14)
            setOnClickListener { p.ctxCount = p.ctxCount + d; v.text = p.ctxCount.toString() }
        }
        row.addView(v)
        row.addView(step("−", -1), Ui.lp(Ui.dp(this, 44), Ui.dp(this, 40)))
        row.addView(step("+", 1), Ui.lp(Ui.dp(this, 44), Ui.dp(this, 40)).apply { leftMargin = Ui.dp(this@TranslateActivity, 6) })
        return row
    }
}
