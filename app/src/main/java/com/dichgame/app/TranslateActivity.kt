package com.dichgame.app

import android.app.Activity
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
                    addView(Ui.tv(a, "Chọn tiếng Anh hoặc tiếng Trung theo chữ của game. Tên game chỉ tự nhận diện khi tựa đầy đủ xuất hiện rõ ít nhất hai lượt quét.", 13f, Ui.SUB))
                    addView(Ui.switchRow(a, "Ghi nhớ bản dịch AI", "Lưu câu AI đã kiểm chứng riêng cho từng game; tránh dịch lại.", p.learnTranslations) {
                        p.learnTranslations = it
                    })
                    addView(Ui.switchRow(a, "Giọng riêng từng nhân vật", "Tự lưu giọng theo hồ sơ game khi dùng Gemini TTS.", p.characterVoices) {
                        p.characterVoices = it
                    })
                }
            }
            if (!p.narrate) {
                group("NGÔN NGỮ TRONG GAME", 14) {
                    val src = Prefs.LANGS.filter { it.first != "vi" }
                    addView(Ui.dropRow(a, "Ngôn ngữ chữ trong game", { Prefs.langLabel(p.lang) }, { src.map { Prefs.langLabel(it.first) } }) {
                        p.lang = src[it].first
                        render()
                    })
                    divide()
                    addView(packRow())
                }
            }
            if (!p.narrate) group("KHO DỮ LIỆU TRỰC TUYẾN") { addView(communityBlock()) }
            if (p.aiAuto) {
                group("GEMINI VÀ KHÓA DỰ PHÒNG") {
                    geminiBlock(this)
                    addView(Ui.tv(a, "Chế độ này ưu tiên bản Việt hóa và câu đã nhớ trước khi gọi Gemini. Hết lượt 401/403/429 sẽ tự thử khóa kế tiếp; các khóa trong cùng dự án Google có thể chung hạn mức. Nếu chưa có khóa, ứng dụng thử dịch trên máy khi được hỗ trợ.", 13f, Ui.SUB), Ui.lpm(a, 8, 2))
                }
                group("NGỮ CẢNH AI") { addView(ctxRow()) }
            } else if (!p.narrate) {
                group("BỘ DỊCH", 0) {
                addView(engineRow("Dịch trên máy", null, "Miễn phí, chạy offline, kém chính xác hơn AI", Prefs.ENGINE_LOCAL))
                divide(16)
                addView(engineRow("OpenAI", "AI", "AI của ChatGPT, dùng API key, trả phí theo lượt", Prefs.ENGINE_OPENAI))
                divide(16)
                addView(engineRow("CloudAPI", "AI", "Máy chủ tương thích OpenAI, tự đổi khóa dự phòng", Prefs.ENGINE_CLOUD))
                divide(16)
                addView(engineRow("Google Gemini", "AI", "Miễn phí nhưng nhanh hết lượt, cần khóa", Prefs.ENGINE_GEMINI))
                }
                when (p.engine) {
                Prefs.ENGINE_GEMINI -> {
                    group("KHÓA GOOGLE GEMINI") { geminiBlock(this) }
                    group("TÙY CHỌN AI") { addView(ctxRow()) }
                }
                Prefs.ENGINE_OPENAI -> {
                    group("KHÓA OPENAI") { providerBlock(this, true) }
                    group("TÙY CHỌN AI") { addView(ctxRow()) }
                }
                Prefs.ENGINE_CLOUD -> {
                    group("CLOUDAPI") { cloudBlock(this) }
                    group("TÙY CHỌN AI") { addView(ctxRow()) }
                }
                else -> put(Ui.tv(a,
                    "Đang dùng bộ dịch trên máy: chạy offline sau lần tải mô hình đầu tiên. Chọn OpenAI, CloudAPI hoặc Google Gemini để dịch hay hơn, giữ đúng tên và xưng hô.",
                    13f, Ui.SUB).apply { setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0) }, 12, 0)
                }
            }
        }
    }

    private fun communityBlock(): View {
        val box=Ui.vbox(this)
        val a=this
        box.addView(Ui.tv(a,"Máy chủ riêng (Cloudflare D1). Mặc định tắt. Khi bật, tên game, ngôn ngữ, tên nhân vật và câu gốc được gửi để tra bản dịch. Không gửi ảnh, âm thanh, API key AI hay mã PSN.",13f,Ui.SUB))
        val url=Ui.field(a,"https://...workers.dev",p.communityUrl)
        val token=Ui.field(a,"Mã truy cập kho dữ liệu",p.communityToken,true)
        box.addView(url,Ui.lpm(a,6,0)); box.addView(token,Ui.lpm(a,6,0))
        box.addView(Ui.button(a,"Lưu máy chủ") {
            val u=url.text.toString().trim(); val key=token.text.toString().trim()
            if(!CommunityCloud.validUrl(u)||key.length !in 24..500) {
                Toast.makeText(a,"Cần URL HTTPS gốc và mã truy cập hợp lệ.",Toast.LENGTH_LONG).show()
            } else {
                p.communityEnabled=false; p.communityContribute=false
                p.communityUrl=u; p.communityToken=key; render()
            }
        },Ui.lpm(a,6,0))
        box.addView(Ui.switchRow(a,"Tra cứu bản dịch dùng chung","Ưu tiên dữ liệu đã duyệt; lỗi mạng thì dùng cách dịch hiện tại.",p.communityEnabled) {
            if(it && (!CommunityCloud.validUrl(p.communityUrl)||p.communityToken.length !in 24..500)) {
                Toast.makeText(a,"Lưu thông tin máy chủ trước.",Toast.LENGTH_SHORT).show(); render()
            } else { p.communityEnabled=it; if(!it) p.communityContribute=false else CommunityCloud.syncCurrent(applicationContext,p); render() }
        })
        if(p.communityEnabled) {
            box.addView(Ui.switchRow(a,"Đóng góp bản dịch AI","Gửi câu gốc và bản dịch đã qua kiểm tra để quản trị viên duyệt. Không gửi từ điển riêng hoặc prompt riêng. Chỉ bật nếu có quyền chia sẻ.",p.communityContribute) { enabled ->
                if(!enabled) p.communityContribute=false else android.app.AlertDialog.Builder(a).setTitle("Cho phép gửi lời thoại?")
                    .setMessage("Chỉ đóng góp lời thoại game mà bạn được phép chia sẻ. Không gửi hội thoại cá nhân. Có thể tắt bất cứ lúc nào; dữ liệu đã gửi cần quản trị viên xóa.")
                    .setNegativeButton("Không") { _,_->render() }.setPositiveButton("Đồng ý") { _,_->p.communityContribute=true;render() }.setOnCancelListener { render() }.show()
            })
            val count=CommunityCloud.offlineCount(a,p,PackStore.gameName(p),p.lang)
            val status=Ui.tv(a,"Đã lưu offline: $count câu. Tự tải bổ sung khi mở app hoặc chọn game.",12.5f,Ui.SUB);box.addView(status)
            box.addView(Ui.button(a,"Kiểm tra kết nối") { CommunityCloud.test(p) { ok->status.text=if(ok) "Kết nối thành công" else "Không kết nối được. Kiểm tra URL và mã." } },Ui.lpm(a,6,0))
            box.addView(Ui.button(a,"Cập nhật danh mục game") { CommunityCloud.refreshCatalog(a,p) { n->status.text=if(n>=0) "Đã tải $n game." else "Chưa cập nhật được." } },Ui.lpm(a,6,0))
            box.addView(Ui.button(a,"Tải gói Việt hóa game đang chọn") { 
                val game=PackStore.gameName(p)
                CommunityCloud.downloadPack(a,p,game,p.profile,p.lang) { n->status.text=when { n>=0 -> "Đã lưu offline $n câu, giữ nguyên bản sửa tay."; n == -2 -> "Gói đang được đồng bộ."; else -> "Chưa tải xong. Dữ liệu offline cũ vẫn dùng được." } }
            },Ui.lpm(a,6,0))
        }
        return box
    }

    /** Trạng thái hai bộ dịch offline; app tự tải ngay khi mở lần đầu. */
    private fun packRow(): View {
        val box = Ui.vbox(this)
        val en = Ui.tv(this, "Tiếng Anh → Việt · Đang kiểm tra…", 14f, Ui.TEXT)
        val zh = Ui.tv(this, "Tiếng Trung → Việt · Đang kiểm tra…", 14f, Ui.TEXT)
        val status = Ui.tv(this, "", 12.5f, Ui.SUB)
        val retry = Ui.button(this, "Tải lại gói còn thiếu") {}
        box.addView(en, Ui.lpm(this, 6, 2))
        box.addView(zh, Ui.lpm(this, 6, 2))
        box.addView(status, Ui.lpm(this, 6, 2))
        box.addView(retry, Ui.lpm(this, 6, 2))
        fun refresh() {
            TextTranslator.isDownloaded("en") { ok -> en.text = "Tiếng Anh → Việt · " + if (ok) "Sẵn sàng ✓" else "Chưa tải đủ" }
            TextTranslator.isDownloaded("zh") { ok -> zh.text = "Tiếng Trung → Việt · " + if (ok) "Sẵn sàng ✓" else "Chưa tải đủ" }
        }
        fun load(force: Boolean) {
            retry.isEnabled = false
            status.text = "Đang chuẩn bị bộ dịch offline (lần đầu cần Internet)…"
            OfflineLanguagePacks.ensure(force) { ok ->
                if (!isFinishing && !isDestroyed) {
                    refresh()
                    status.text = if (ok) "Đã sẵn sàng · dịch offline không cần tải lại." else "Chưa tải đủ. Kiểm tra mạng rồi thử lại."
                    retry.isEnabled = !ok
                }
            }
        }
        retry.setOnClickListener { load(true) }
        refresh()
        load(false)
        return box
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
        val keys = p.geminiKeys()
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
            val st = KeyRing.status(k, p.geminiModel)
            val (label, color) = when (st.state) {
                KeyRing.State.BAD -> "KHÓA SAI" to Ui.DANGER
                KeyRing.State.QUOTA -> "HẾT LƯỢT · ${AiRetry.humanWait(st.leftMs)}" to Ui.DANGER
                KeyRing.State.OK -> if (st.active) "ĐANG DÙNG" to Ui.OK else "SẴN SÀNG" to Ui.GOLD
            }
            row.addView(Ui.badge(a, label, color))
            row.addView(Ui.tv(a, "✕", 18f, Ui.DANGER, true).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6))
                setOnClickListener { p.setGeminiKeys(keys.filterIndexed { j, _ -> j != i }); render() }
            })
            c.addView(row)
            c.divide()
        }
        val add = Ui.field(a, "Dán khóa Gemini (bắt đầu bằng AIza…)", "", true)
        c.addView(add, Ui.lpm(a, 10, 8))
        c.addView(Ui.button(a, "＋  Thêm khóa") {
            val v = add.text.toString().trim()
            if (v.isEmpty()) { Toast.makeText(a, "Hãy dán khóa vào ô trên.", Toast.LENGTH_SHORT).show(); return@button }
            p.setGeminiKeys(p.geminiKeys() + v)
            render()
        }, Ui.lpm(a, 0, 4))
        c.addView(Ui.tv(a, "Thêm nhiều khóa: khi một khóa hết lượt (lỗi 429) hoặc bị từ chối, app tự chuyển sang khóa kế tiếp và tạm bỏ khóa đó tới giờ hồi (hết lượt theo ngày thì tới nửa đêm giờ Thái Bình Dương). Lưu ý: Google tính hạn mức theo dự án, các khóa tạo trong cùng một dự án dùng chung hạn mức nên không thêm lượt; hãy tạo khóa ở dự án khác.", 12.5f, Ui.SUB), Ui.lpm(a, 6, 4))
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

    private fun cloudBlock(c: LinearLayout) = providerBlock(c, false)

    private fun providerBlock(c: LinearLayout, open: Boolean) {
        val a = this
        val provider = if (open) "OpenAI" else "CloudAPI"
        fun keys() = if (open) p.openAiKeys() else p.cloudKeys()
        fun save(list: List<String>) { if (open) p.setOpenAiKeys(list) else p.setCloudKeys(list) }
        val scope = if (open) Ai.openAiScope(p) else Ai.cloudScope(p)
        if (!open) {
            val url = Ui.field(a, "Địa chỉ CloudAPI (vd https://api.example.com/v1)", p.cloudUrl)
            watch(url) { p.cloudUrl = it }
            c.addView(url, Ui.lpm(a, 0, 8))
        }
        val list = keys()
        if (list.isEmpty()) c.addView(Ui.tv(a, "Chưa có khóa $provider.", 13f, Ui.SUB))
        list.forEachIndexed { index, key ->
            val row = Ui.hbox(a)
            row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8))
            val col = Ui.vbox(a)
            col.addView(Ui.tv(a, "Khóa ${index + 1}", 15f, Ui.TEXT, true))
            col.addView(Ui.tv(a, mask(key), 12.5f, Ui.SUB))
            row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
            val st = KeyRing.status(key, scope)
            val (label, color) = when (st.state) {
                KeyRing.State.BAD -> "KHÓA SAI" to Ui.DANGER
                KeyRing.State.QUOTA -> "TẠM NGHỈ · ${AiRetry.humanWait(st.leftMs)}" to Ui.DANGER
                KeyRing.State.OK -> if (st.active) "ĐANG DÙNG" to Ui.OK else "SẴN SÀNG" to Ui.GOLD
            }
            row.addView(Ui.badge(a, label, color))
            row.addView(Ui.tv(a, "✕", 18f, Ui.DANGER, true).apply {
                setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6))
                setOnClickListener { save(keys().filter { it != key }); render() }
            })
            c.addView(row); c.divide()
        }
        val add = Ui.field(a, "Dán khóa $provider", "", true)
        c.addView(add, Ui.lpm(a, 10, 8))
        c.addView(Ui.button(a, "＋  Thêm khóa") {
            val value = add.text.toString().trim()
            if (value.isBlank()) { Toast.makeText(a, "Hãy dán khóa vào ô trên.", Toast.LENGTH_SHORT).show(); return@button }
            save(keys() + value); render()
        }, Ui.lpm(a, 0, 4))
        c.addView(Ui.tv(a, "Tự đổi khóa khi khóa bị từ chối hoặc hết lượt; tạm nghỉ theo hạn mức. Các khóa chung dự án/tài khoản không làm tăng hạn mức.", 12.5f, Ui.SUB), Ui.lpm(a, 6, 4))
        if (open) c.addView(Ui.button(a, "Quản lý khóa OpenAI") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/api-keys"))) }
            catch (_: Exception) { Toast.makeText(a, "Không mở được trình duyệt.", Toast.LENGTH_SHORT).show() }
        }, Ui.lpm(a, 8, 4))
        val model = Ui.field(a, "Model", if (open) p.openAiModel else p.cloudModel)
        watch(model) { if (open) p.openAiModel = it else p.cloudModel = it }
        c.addView(model, Ui.lpm(a, 8, 8))
        val status = Ui.tv(a, "", 13f, Ui.SUB)
        c.addView(Ui.button(a, "Kiểm tra kết nối", true) { runTest(status) }, Ui.lpm(a, 4, 6))
        c.addView(status, Ui.lpm(a, 4, 0))
        c.addView(Ui.button(a, "Làm mới trạng thái khóa") { render() }, Ui.lpm(a, 8, 0))
    }

    private fun runTest(status: TextView) {
        status.setTextColor(Ui.SUB)
        if (!Ai.enabled(p)) {
            status.setTextColor(Ui.DANGER)
            status.text = if (p.engine == Prefs.ENGINE_CLOUD) "Hãy nhập địa chỉ và khóa API." else "Hãy thêm khóa API."
            return
        }
        status.text = "Đang kiểm tra..."
        Thread {
            val r = Ai.test(p)
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
