package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Trang "Cách dịch": chế độ Dịch / Thuyết minh, danh sách bộ dịch (AI Tự động, Google Gemini, CloudAPI, trên máy),
 * khóa Gemini (thêm, sửa, bật/tắt, vuốt trái để xóa), CloudAPI, số câu ngữ cảnh và ngôn ngữ trong game.
 */
class TranslateActivity : Activity() {
    private lateinit var p: Prefs
    private var built = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        if (BuildConfig.DEBUG) intent.getStringExtra("demo")?.let { demo(it) }
        render()
    }

    /** Chỉ cho bản debug và bài thử tự động (ci/smoke.sh): dựng sẵn từng trạng thái để chụp ảnh, khóa là khóa giả. */
    private fun demo(kind: String) {
        p.narrate = kind == "narrate"
        if (kind == "narrate") return
        p.aiAuto = kind == "auto"
        p.engine = when (kind) {
            "cloud" -> Prefs.ENGINE_CLOUD
            "local" -> Prefs.ENGINE_LOCAL
            else -> Prefs.ENGINE_GEMINI
        }
        if (kind == "gemini" || kind == "auto") {
            val demoKeys = listOf("AIzaDEMO-khoa-1-0000000000", "AIzaDEMO-khoa-2-1111111111", "AIzaDEMO-khoa-3-2222222222")
            if (p.geminiStoredKeys().isEmpty()) p.setGeminiKeys(demoKeys)
            if (p.geminiStoredKeys().take(3) == demoKeys) {
                p.setGeminiKeyEnabled(demoKeys[2], false)
                KeyRing.blockQuota(demoKeys[1], p.geminiModel, 2 * 3_600_000L + 5 * 60_000L)
            }
        }
        if (kind == "cloud") {
            if (p.cloudUrl.isBlank()) p.cloudUrl = "https://api.example.com/v1"
            if (p.cloudKey.isBlank()) p.cloudKey = "demo-khoa-0000000000"
        }
    }

    private fun dp(n: Number) = Ui.dp(this, n)

    // ---------------------------------------------------------------- màn hình

    private fun render() {
        val a = this
        val keep = if (built) findScroll(window.decorView)?.scrollY ?: 0 else 0
        screen("Cách dịch") {
            addView(modeRow(), Ui.lpm(a, 8, 0))
            addView(Ui.tv(a,
                if (p.narrate) "Đang ở chế độ Thuyết minh: chỉ đọc to chữ tiếng Việt trên màn hình, không dịch."
                else "Đang ở chế độ Dịch: dịch chữ trên màn hình sang tiếng Việt.", 13.5f, Ui.SUB).apply {
                setPadding(dp(8), 0, dp(8), 0)
            }, Ui.lpm(a, 10, 0))
            if (p.narrate) return@screen

            heading("Bộ dịch")
            group(null, 0, 0) {
                addView(providerRow(ToolIcon.Kind.BOLT, "#FFB300", "#FF6D00", "AI Tự động",
                    "Tự nhận diện game, ưu tiên bản Việt hóa và bộ nhớ rồi mới gọi Gemini", p.aiAuto) { p.aiAuto = true })
                divide(70)
                addView(providerRow(ToolIcon.Kind.TRANSLATE, "#4C8DFF", "#7B4DFF", "Google Gemini",
                    "Dịch hay, giữ đúng tên và xưng hô; khóa miễn phí nhưng dễ hết lượt",
                    !p.aiAuto && p.engine == Prefs.ENGINE_GEMINI) { choose(Prefs.ENGINE_GEMINI) })
                divide(70)
                addView(providerRow(ToolIcon.Kind.GEAR, "#1FC8A4", "#0E8F7B", "CloudAPI (OpenAI)",
                    "AI chuẩn OpenAI (ChatGPT, Groq…), cần địa chỉ và khóa, có thể trả phí theo lượt",
                    !p.aiAuto && p.engine == Prefs.ENGINE_CLOUD) { choose(Prefs.ENGINE_CLOUD) })
                divide(70)
                addView(providerRow(ToolIcon.Kind.HOME, "#8E99A8", "#5B6573", "Dịch trên máy",
                    "Miễn phí, chạy offline, kém chính xác hơn AI",
                    !p.aiAuto && p.engine == Prefs.ENGINE_LOCAL) { choose(Prefs.ENGINE_LOCAL) })
            }

            if (!p.aiAuto) {
                heading("Ngôn ngữ trong game")
                group(null, 16, 0) {
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

            when {
                p.aiAuto -> {
                    geminiSection()
                    heading("Tùy chọn AI Tự động")
                    group(null, 0, 0) {
                        addView(Ui.switchRow(a, "Ghi nhớ bản dịch AI", "Lưu câu AI đã kiểm chứng riêng cho từng game; tránh dịch lại.", p.learnTranslations) {
                            p.learnTranslations = it
                        }.pad())
                        divide()
                        addView(Ui.switchRow(a, "Giọng riêng từng nhân vật", "Tự lưu giọng theo hồ sơ game khi dùng Gemini TTS.", p.characterVoices) {
                            p.characterVoices = it
                        }.pad())
                    }
                    note("Tự nhận biết ngôn ngữ từ chữ OCR khi đủ bằng chứng. Tên game chỉ tự nhận diện khi tựa đầy đủ xuất hiện rõ ít nhất hai lượt quét. Nếu chưa có khóa, app thử dịch trên máy khi được hỗ trợ.")
                    heading("CloudAPI dự phòng")
                    group(null, 0, 0) {
                        addView(Ui.switchRow(a, "Dùng CloudAPI khi Gemini lỗi", "Gửi câu thoại và ngữ cảnh tới dịch vụ HTTPS bạn cấu hình (ví dụ Groq). Có thể phát sinh phí.", p.autoCloudFallback) {
                            p.autoCloudFallback = it
                            render()
                        }.pad())
                        if (p.autoCloudFallback) {
                            divide()
                            cloudRows(true)
                        }
                    }
                    contextSection()
                }
                p.engine == Prefs.ENGINE_GEMINI -> {
                    geminiSection()
                    contextSection()
                }
                p.engine == Prefs.ENGINE_CLOUD -> {
                    heading("CloudAPI (OpenAI)")
                    group(null, 0, 0) { cloudRows(false) }
                    note("Địa chỉ phải bắt đầu bằng https:// (ví dụ https://api.example.com/v1). Nhà cung cấp tính tiền theo số câu gửi đi và thường cần nạp trước; khóa chỉ lưu trên máy này.")
                    contextSection()
                }
                else -> note("Đang dùng bộ dịch trên máy: chạy offline sau lần tải mô hình đầu tiên. Chọn Google Gemini hoặc CloudAPI để dịch hay hơn, giữ đúng tên và xưng hô.", 14)
            }
        }
        built = true
        restoreScroll(keep)
    }

    private fun choose(engine: String) {
        p.aiAuto = false
        p.engine = engine
    }

    private fun findScroll(v: View): ScrollView? {
        if (v is ScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findScroll(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Vẽ lại cả trang khi đổi lựa chọn nhưng giữ nguyên vị trí cuộn, để khỏi bị nhảy về đầu trang. */
    private fun restoreScroll(y: Int) {
        if (y <= 0) return
        val sv = findScroll(window.decorView) ?: return
        sv.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                sv.viewTreeObserver.removeOnPreDrawListener(this)
                sv.scrollTo(0, y)
                return true
            }
        })
    }

    // ---------------------------------------------------------------- khối giao diện nhỏ

    private fun LinearLayout.heading(t: String, top: Number = 22) {
        addView(Ui.tv(context, t, 17f, Ui.SUB, true).apply { setPadding(dp(8), 0, 0, 0) }, Ui.lpm(context, top, 8))
    }

    private fun LinearLayout.note(t: String, top: Number = 8) {
        addView(Ui.tv(context, t, 13f, Ui.SUB).apply {
            setPadding(dp(8), 0, dp(8), 0)
            setLineSpacing(0f, 1.1f)
        }, Ui.lpm(context, top, 0))
    }

    private fun View.pad(): View = apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }

    private fun modeRow(): View {
        val row = Ui.hbox(this)
        row.background = Ui.bg(this, Ui.CARD, 20, Ui.GOLD, 1)
        row.setPadding(dp(18), dp(16), dp(14), dp(16))
        row.addView(Ui.tv(this, if (p.narrate) "Chuyển sang dịch" else "Chuyển sang thuyết minh", 17f, Ui.GOLD, true), Ui.lp(0, WRAP_CONTENT, 1f))
        row.addView(Ui.tv(this, "›", 26f, Ui.GOLD, true))
        row.isClickable = true
        row.isFocusable = true
        Ui.ripple(row, 26)
        row.setOnClickListener { p.narrate = !p.narrate; render() }
        return row
    }

    private fun providerRow(kind: ToolIcon.Kind, c1: String, c2: String, title: String, sub: String, on: Boolean, pick: () -> Unit): View {
        val row = Ui.hbox(this)
        row.setPadding(dp(14), dp(12), dp(10), dp(12))
        row.addView(Ui.iconTile(this, kind, Color.parseColor(c1), Color.parseColor(c2), 44), Ui.lp(dp(44), dp(44)))
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, title, 17f, Ui.TEXT, true))
        col.addView(Ui.tv(this, sub, 13.5f, Ui.SUB).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(12); rightMargin = dp(6) })
        row.addView(Ui.tv(this, if (on) "✓" else "", 22f, Ui.GOLD, true).apply { gravity = Gravity.CENTER }, Ui.lp(dp(28), WRAP_CONTENT))
        row.isClickable = true
        row.isFocusable = true
        Ui.ripple(row, 22)
        row.setOnClickListener { pick(); render() }
        return row
    }

    /** Hàng "nhãn ... giá trị ›" bấm để sửa giá trị. */
    private fun valueRow(title: String, value: String, placeholder: Boolean = false, onClick: () -> Unit): View {
        val row = Ui.hbox(this)
        row.setPadding(dp(16), dp(14), dp(12), dp(14))
        row.addView(Ui.tv(this, title, 16f, Ui.TEXT, true))
        row.addView(Ui.tv(this, value, 15f, if (placeholder) Ui.SUB else Ui.GOLD).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            gravity = Gravity.END
        }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(16) })
        row.addView(Ui.tv(this, "›", 22f, Ui.SUB).apply { setPadding(dp(8), 0, 0, 0) })
        row.isClickable = true
        row.isFocusable = true
        Ui.ripple(row, 22)
        row.setOnClickListener { onClick() }
        return row
    }

    /** Hàng hành động: [lead] (tùy chọn) bên trái, tiêu đề, dòng phụ và ký hiệu bên phải. */
    private fun actionRow(title: String, sub: String? = null, trail: String? = null, gold: Boolean = false, lead: View? = null, onClick: () -> Unit): View {
        val row = Ui.hbox(this)
        row.setPadding(dp(16), dp(13), dp(16), dp(13))
        if (lead != null) row.addView(lead, Ui.lp(dp(26), dp(26)).apply { rightMargin = dp(12) })
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, title, 16f, if (gold) Ui.GOLD else Ui.TEXT, true))
        if (sub != null) col.addView(Ui.tv(this, sub, 13f, Ui.SUB))
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
        if (trail != null) row.addView(Ui.tv(this, trail, 18f, Ui.SUB))
        row.isClickable = true
        row.isFocusable = true
        Ui.ripple(row, 22)
        row.setOnClickListener { onClick() }
        return row
    }

    private fun plusCircle(): View = TextView(this).apply {
        text = "+"
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#2A1A08"))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.GOLD) }
    }

    private fun mask(k: String): String = if (k.length <= 10) "••••••" else k.take(4) + "…" + k.takeLast(4)

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Không mở được trình duyệt.", Toast.LENGTH_SHORT).show() }
    }

    /** Hộp nhập một giá trị. [reset] có thì hiện thêm nút "Mặc định". */
    private fun fieldDialog(title: String, hint: String, value: String, secret: Boolean, reset: (() -> Unit)? = null, ok: String = "Lưu", onSave: (String) -> Unit) {
        val edit = Ui.field(this, hint, value, secret)
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(edit, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val b = AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("Hủy", null)
            .setPositiveButton(ok) { _, _ -> onSave(edit.text.toString().trim()) }
        if (reset != null) b.setNeutralButton("Mặc định") { _, _ -> reset() }
        val d = b.create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }

    // ---------------------------------------------------------------- Gemini

    private fun LinearLayout.geminiSection() {
        val a = this@TranslateActivity
        val keys = p.geminiStoredKeys()
        heading("Khóa Gemini")
        group(null, 0, 0) {
            if (keys.isEmpty()) {
                addView(Ui.tv(a, "Chưa có khóa. Lấy khóa miễn phí rồi bấm “Thêm khóa”.", 14f, Ui.SUB).pad())
                divide()
            } else {
                val enabledCount = keys.count { p.isGeminiKeyEnabled(it) }
                val usableCount = keys.count { p.isGeminiKeyEnabled(it) && KeyRing.status(it, p.geminiModel).state == KeyRing.State.OK }
                val cooldownCount = keys.count { p.isGeminiKeyEnabled(it) && KeyRing.status(it, p.geminiModel).state == KeyRing.State.QUOTA }
                val invalidCount = keys.count { p.isGeminiKeyEnabled(it) && KeyRing.status(it, p.geminiModel).state == KeyRing.State.BAD }
                val summary = buildString {
                    append("${keys.size} khóa · bật $enabledCount · khả dụng $usableCount")
                    if (cooldownCount > 0) append(" · tạm nghỉ $cooldownCount")
                    if (invalidCount > 0) append(" · cần kiểm tra $invalidCount")
                    if (keys.size > enabledCount) append(" · tắt ${keys.size - enabledCount}")
                }
                addView(Ui.tv(a, summary, 12.5f, Ui.SUB).apply {
                    setPadding(dp(16), dp(12), dp(16), dp(10))
                })
                divide()
                keys.forEachIndexed { i, k ->
                    addView(keyRow(i, k, keys))
                    divide(38)
                }
            }
            addView(actionRow("Thêm khóa", gold = true, lead = plusCircle()) { addKeyDialog() })
            divide()
            addView(actionRow("Lấy khóa miễn phí", "aistudio.google.com", "↗") { openUrl("https://aistudio.google.com/apikey") })
            divide()
            addView(valueRow("Model", p.geminiModel) {
                fieldDialog("Model Gemini", "Tên model", p.geminiModel, false, reset = { p.geminiModel = GEMINI_DEFAULT_MODEL; render() }) { v ->
                    if (v.isBlank()) Toast.makeText(a, "Tên model không được để trống.", Toast.LENGTH_SHORT).show()
                    else { p.geminiModel = v; render() }
                }
            })
            divide()
            testRow(false)
        }
        note("App tự chia câu cho các khóa và đổi sang khóa còn lượt khi gặp lỗi 429, 401 hoặc 403; khóa bị bỏ tạm tới giờ hồi (hết lượt theo ngày thì tới nửa đêm giờ Thái Bình Dương). Bấm vào khóa để sửa, bật hoặc tắt; vuốt khóa sang trái để xóa. Google tính hạn mức theo dự án nên các khóa tạo trong cùng một dự án dùng chung lượt; hãy tạo khóa ở dự án khác.")
    }

    private fun keyRow(i: Int, k: String, keys: List<String>): View {
        val enabled = p.isGeminiKeyEnabled(k)
        val st = KeyRing.status(k, p.geminiModel)
        val (label, color) = if (!enabled) "Đã tắt" to Ui.SUB else when (st.state) {
            KeyRing.State.BAD -> "Cần kiểm tra" to Ui.DANGER
            KeyRing.State.QUOTA -> "Tạm nghỉ · ${AiRetry.humanWait(st.leftMs)}" to Ui.DANGER
            KeyRing.State.OK -> if (st.active) "Đang dùng" to Ui.OK else "Sẵn sàng" to Ui.OK
        }
        val row = Ui.hbox(this)
        row.setPadding(dp(16), dp(12), dp(16), dp(12))
        row.addView(View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        }, Ui.lp(dp(10), dp(10)).apply { rightMargin = dp(12) })
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, "Khóa ${i + 1}", 16f, if (enabled) Ui.TEXT else Ui.SUB, true))
        col.addView(Ui.tv(this, mask(k), 12.5f, Ui.SUB))
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
        row.addView(Ui.tv(this, label, 13f, color, true).apply { gravity = Gravity.END }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(8) })
        row.isClickable = true
        row.isFocusable = true
        Ui.ripple(row, 16)
        row.setOnClickListener { keyActions(i, k, keys, enabled) }
        swipeToDelete(row) { confirmDelete(i, k, keys) }
        return row
    }

    /** Vuốt một khóa sang trái đủ xa thì hỏi xóa; vuốt ngắn hoặc nhả tay thì hàng trượt về chỗ cũ. */
    private fun swipeToDelete(row: View, onCommit: () -> Unit) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val commit = dp(96).toFloat()
        var x0 = 0f
        var y0 = 0f
        var drag = false
        row.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; drag = false; false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    if (!drag && dx < -slop && abs(dx) > abs(dy) * 1.5f) {
                        drag = true
                        v.isPressed = false
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (drag) {
                        val t = dx.coerceIn(-commit * 1.6f, 0f)
                        v.translationX = t
                        v.alpha = 1f - abs(t) / (commit * 2.4f)
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val was = drag
                    drag = false
                    val passed = was && e.actionMasked == MotionEvent.ACTION_UP && v.translationX <= -commit
                    v.animate().translationX(0f).alpha(1f).setDuration(160).start()
                    if (passed) onCommit()
                    was
                }
                else -> false
            }
        }
    }

    private fun keyActions(i: Int, k: String, keys: List<String>, enabled: Boolean) {
        val items = arrayOf("Sửa khóa", if (enabled) "Tắt khóa" else "Bật khóa", "Xóa khóa")
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Khóa ${i + 1}  ·  ${mask(k)}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> editKey(i, k, keys)
                    1 -> {
                        if (p.setGeminiKeyEnabled(k, !enabled)) render()
                        else Toast.makeText(this, "Không lưu được trạng thái khóa. Hãy thử lại.", Toast.LENGTH_SHORT).show()
                    }
                    else -> confirmDelete(i, k, keys)
                }
            }
            .show()
    }

    private fun editKey(i: Int, k: String, keys: List<String>) {
        fieldDialog("Sửa khóa ${i + 1}", "Khóa Gemini", k, true) { updated ->
            if (updated.isBlank()) {
                Toast.makeText(this, "Khóa không được để trống.", Toast.LENGTH_SHORT).show()
            } else if (updated != k) {
                val next = keys.toMutableList().also { it[i] = updated }
                if (p.setGeminiKeys(next)) {
                    KeyRing.forget(k)
                    render()
                } else {
                    Toast.makeText(this, "Không lưu được khóa mã hóa. Khóa cũ vẫn được giữ.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete(i: Int, k: String, keys: List<String>) {
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Xóa khóa ${i + 1}?")
            .setMessage("${mask(k)} sẽ bị xóa khỏi máy này. Không hoàn tác được.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ ->
                if (p.setGeminiKeys(keys.filterIndexed { j, _ -> j != i })) {
                    KeyRing.forget(k)
                    render()
                } else {
                    Toast.makeText(this, "Không xóa được khóa. Dữ liệu cũ vẫn được giữ.", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun addKeyDialog() {
        fieldDialog("Thêm khóa Gemini", "Dán khóa (bắt đầu bằng AIza…)", "", true, ok = "Thêm") { v ->
            when {
                v.isEmpty() -> Toast.makeText(this, "Hãy dán khóa vào ô nhập.", Toast.LENGTH_SHORT).show()
                v in p.geminiStoredKeys() -> Toast.makeText(this, "Khóa này đã có trong danh sách.", Toast.LENGTH_SHORT).show()
                p.setGeminiKeys(p.geminiStoredKeys() + v) -> render()
                else -> Toast.makeText(this, "Không lưu được khóa mã hóa. Hãy kiểm tra bộ nhớ thiết bị.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------------------------------------------------------- CloudAPI

    private fun LinearLayout.cloudRows(fallback: Boolean) {
        addView(valueRow("Địa chỉ", p.cloudUrl.ifBlank { "Chưa nhập" }, p.cloudUrl.isBlank()) {
            fieldDialog("Địa chỉ CloudAPI", "https://api.example.com/v1", p.cloudUrl, false) { p.cloudUrl = it; render() }
        })
        divide()
        addView(valueRow("Khóa API", if (p.cloudKey.isBlank()) "Chưa nhập" else mask(p.cloudKey), p.cloudKey.isBlank()) {
            fieldDialog("Khóa API CloudAPI", "Khóa API", p.cloudKey, true) { p.cloudKey = it; render() }
        })
        divide()
        addView(valueRow("Model", p.cloudModel.ifBlank { "Chưa nhập" }, p.cloudModel.isBlank()) {
            fieldDialog("Model CloudAPI", "Tên model", p.cloudModel, false) { p.cloudModel = it; render() }
        })
        divide()
        testRow(fallback)
    }

    // ---------------------------------------------------------------- thử kết nối, ngữ cảnh, gói ngôn ngữ

    private fun LinearLayout.testRow(forceCloud: Boolean) {
        val a = this@TranslateActivity
        val status = Ui.tv(a, "", 13f, Ui.SUB).apply {
            visibility = View.GONE
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        addView(actionRow("Kiểm tra kết nối", gold = true) { runTest(status, forceCloud) })
        addView(status)
    }

    private fun runTest(status: TextView, forceCloud: Boolean = false) {
        status.visibility = View.VISIBLE
        status.setTextColor(Ui.SUB)
        if (if (forceCloud) !p.cloudUrl.startsWith("https://", ignoreCase = true) || p.cloudKey.isBlank() || p.cloudModel.isBlank() else !Ai.enabled(p)) {
            status.setTextColor(Ui.DANGER)
            status.text = if (p.engine == Prefs.ENGINE_CLOUD) "Hãy nhập địa chỉ (https://) và khóa API." else "Hãy thêm khóa API."
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

    private fun LinearLayout.contextSection() {
        heading("Ngữ cảnh AI")
        group(null, 0, 0) { addView(ctxRow()) }
        note("Số câu trước AI xem để hiểu mạch truyện và giữ cách xưng hô. Nhiều câu hơn thì hiểu truyện tốt hơn nhưng mỗi lần gửi nhiều chữ hơn.")
    }

    private fun ctxRow(): View {
        val row = Ui.hbox(this)
        row.setPadding(dp(16), dp(12), dp(12), dp(12))
        row.addView(Ui.tv(this, "Câu trước gửi kèm", 16f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        val v = Ui.tv(this, p.ctxCount.toString(), 17f, Ui.GOLD, true)
        v.setPadding(dp(12), 0, dp(14), 0)
        fun step(t: String, d: Int) = Ui.tv(this, t, 22f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            Ui.ripple(this, 20)
            setOnClickListener { p.ctxCount = p.ctxCount + d; v.text = p.ctxCount.toString() }
        }
        val pill = Ui.hbox(this)
        pill.background = Ui.bg(this, Ui.CARD2, 20)
        pill.addView(step("−", -1), Ui.lp(dp(48), dp(40)))
        pill.addView(View(this).apply { setBackgroundColor(Color.parseColor("#33FFFFFF")) }, Ui.lp(1, dp(20)))
        pill.addView(step("+", 1), Ui.lp(dp(48), dp(40)))
        row.addView(v)
        row.addView(pill)
        return row
    }

    /** Hàng "Gói ngôn ngữ X → Việt" với nút tải trước mô hình dịch trên máy, để lần đầu chơi khỏi chờ. */
    private fun packRow(): View {
        val row = Ui.hbox(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(6), 0, dp(2))
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

    private companion object {
        const val GEMINI_DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }
}
