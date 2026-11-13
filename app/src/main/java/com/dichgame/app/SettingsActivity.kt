package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/** Cài đặt: Giọng đọc, Phụ đề, Hồ sơ game, Ngôn ngữ. */
class SettingsActivity : Activity() {
    private lateinit var p: Prefs
    private var speaker: Speaker? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        speaker = Speaker(this, p)
        speaker?.onAiError = { m -> Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
        render(intent?.getStringExtra("goto"))
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("selftest", false) == true) SelfTest.voice(applicationContext)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: android.content.Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 7) render("profile")
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }

    private fun render(target: String? = null) {
        screen("Cài đặt") {
            put(voiceCard(), 0, 12)
            put(subtitleCard(), 0, 12)
            val prof = profileCard()
            put(prof, 0, 12)
            put(languageCard(), 0, 12)
            put(aboutCard(), 0, 12)
            if (target == "profile") post { (parent as? ScrollView)?.smoothScrollTo(0, prof.top) }
        }
    }

    // ---------------- Giới thiệu và hỗ trợ ----------------

    private fun aboutCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "💬", "Giới thiệu và hỗ trợ"))
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() } catch (_: Exception) { "?" }
        c.addView(Ui.tv(this, "Phiên bản $ver" + (if (BuildConfig.BUILD_NO > 0) " (bản dựng ${BuildConfig.BUILD_NO})" else ""), 14f, Ui.SUB), Ui.lpm(this, 0, 10))
        val status = Ui.tv(this, "", 13.5f, Ui.SUB)
        c.addView(Ui.button(this, "Kiểm tra bản mới") { checkUpdate(status) }, Ui.lpm(this, 0, 6))
        c.addView(status, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Báo lỗi / yêu cầu game") { report() }, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Trang dự án trên GitHub") { openUrl("https://github.com/" + Update.REPO) }, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Giấy phép và ghi công") { startActivity(android.content.Intent(this, LicensesActivity::class.java)) }, Ui.lpm(this, 0, 0))
        return c
    }

    private fun openUrl(u: String) {
        try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
        catch (_: Exception) { Toast.makeText(this, "Không mở được trình duyệt.", Toast.LENGTH_SHORT).show() }
    }

    private fun checkUpdate(status: android.widget.TextView) {
        status.text = "Đang kiểm tra..."
        Thread {
            var info: Update.Info? = null
            var err = false
            try { info = Update.latest() } catch (_: Exception) { err = true }
            val got = info
            runOnUiThread {
                when {
                    err -> status.text = "Không kiểm tra được. Kiểm tra mạng rồi thử lại."
                    got == null -> status.text = "Chưa có bản phát hành nào trên GitHub."
                    BuildConfig.BUILD_NO == 0 -> status.text = "Bản này tự build nên không so sánh được. Bản mới nhất trên GitHub là bản dựng ${got.no}."
                    got.no > BuildConfig.BUILD_NO -> { status.text = "Có bản mới: bản dựng ${got.no}."; offerUpdate(got) }
                    else -> status.text = "Bạn đang dùng bản mới nhất."
                }
            }
        }.start()
    }

    private fun offerUpdate(i: Update.Info) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Có bản mới")
            .setMessage("Bản dựng ${i.no} đã có trên GitHub. Tải file về rồi mở để cài đè, cài đặt và hồ sơ game của bạn được giữ nguyên.")
            .setPositiveButton("Tải về") { _, _ -> openUrl(i.apk ?: i.page) }
            .setNegativeButton("Để sau", null).show()
    }

    /** Mở trang tạo báo cáo trên GitHub, điền sẵn thông tin máy. Bạn xem lại rồi mới gửi. */
    private fun report() {
        val body = "Mô tả (lỗi gặp phải, hoặc tên game cần hỗ trợ):\n\n\n---\n" +
            "Phiên bản: " + (try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }) + "\n" +
            "Android: " + android.os.Build.VERSION.RELEASE + "\n" +
            "Máy: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n" +
            "Ngôn ngữ game: " + Prefs.langName(p.lang) + "\n" +
            "Game: " + p.game
        openUrl("https://github.com/" + Update.REPO + "/issues/new?title=" + android.net.Uri.encode("[Lỗi/Yêu cầu game] ") + "&body=" + android.net.Uri.encode(body))
    }

    // ---------------- Giọng đọc ----------------

    /** Khối "Giọng offline": tải về, xóa, và thông tin giấy phép. */
    private fun offlineBlock(): View {
        val box = Ui.vbox(this)
        val pack = Voices.pack(p.offlineVoice) ?: Voices.PACKS[0]
        val has = Voices.installed(this, pack.id)
        box.addView(Ui.tv(this, pack.name, 15f, Ui.TEXT, true))
        box.addView(Ui.tv(this, pack.desc, 13f, Ui.SUB), Ui.lpm(this, 2, 8))
        if (has) {
            box.addView(Ui.tv(this, "✔ Đã tải về máy (${Voices.sizeMb(this, pack.id)} MB)", 13.5f, Ui.OK), Ui.lpm(this, 0, 8))
            box.addView(Ui.button(this, "Xóa giọng khỏi máy") {
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Xóa giọng đọc?")
                    .setMessage("Giọng sẽ bị xóa khỏi máy, muốn dùng lại phải tải lần nữa. Trong lúc đó app đọc bằng giọng cơ bản.")
                    .setPositiveButton("Xóa") { _, _ -> speaker?.release(); Voices.delete(this, pack.id); render() }
                    .setNegativeButton("Giữ lại", null).show()
            }, Ui.lpm(this, 0, 6, WRAP_CONTENT))
        } else {
            box.addView(Ui.tv(this, "Chưa tải nên app đang đọc bằng giọng cơ bản. Cần khoảng ${pack.downloadMb} MB, nên dùng Wi-Fi.", 13f, Ui.SUB), Ui.lpm(this, 0, 8))
            box.addView(Ui.button(this, "⬇  Tải giọng (${pack.downloadMb} MB)", true) { downloadVoice(pack) }, Ui.lpm(this, 0, 6, WRAP_CONTENT))
        }
        box.addView(Ui.tv(this, "Giấy phép giọng: ${pack.license}. ${pack.credit} Số, ký hiệu được đọc thành chữ; tên riêng và chữ nước ngoài được đọc theo kiểu tiếng Việt.", 12f, Ui.SUB), Ui.lpm(this, 6, 4))
        return box
    }

    private fun downloadVoice(pack: VoicePack) {
        val cancel = AtomicBoolean(false)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val msg = Ui.tv(this, "Đang tải 0%...", 14f, Ui.TEXT)
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad, pad, pad / 2)
        box.addView(msg, Ui.lpm(this, 0, 10))
        box.addView(bar, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
        val d = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Đang tải giọng đọc")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Hủy") { _, _ -> cancel.set(true) }
            .create()
        d.show()
        val app = applicationContext
        Thread {
            val err = Voices.download(app, pack, cancel) { pct ->
                runOnUiThread { bar.progress = pct; msg.text = "Đang tải $pct%..." }
            }
            runOnUiThread {
                try { if (d.isShowing) d.dismiss() } catch (_: Exception) {}
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (err == null) {
                    Toast.makeText(this, "Đã tải xong giọng đọc.", Toast.LENGTH_SHORT).show()
                    speaker?.prepare()
                    render()
                } else {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun voiceCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "🎙", "Giọng đọc"))
        c.addView(Ui.switchRow(this, "Đọc to bản dịch", "Tắt nếu chỉ muốn xem chữ.", p.speak) { p.speak = it })
        c.addView(Ui.segmented(this, listOf("Cơ bản", "Giọng AI", "Offline"), when (p.voiceEngine) { "ai" -> 1; "offline" -> 2; else -> 0 }) { i ->
            p.voiceEngine = when (i) { 1 -> "ai"; 2 -> "offline"; else -> "system" }
            if (i == 2) speaker?.prepare()
            render()
        }, Ui.lpm(this, 8, 8))
        if (p.voiceEngine == "offline") {
            c.addView(offlineBlock(), Ui.lpm(this, 4, 6))
        } else if (p.voiceEngine == "ai") {
            c.addView(Ui.dropRow(this, "Giọng AI",
                { Prefs.AI_VOICES.firstOrNull { it.first == p.aiVoice }?.let { "${it.first} · ${it.second}" } ?: p.aiVoice },
                { Prefs.AI_VOICES.map { "${it.first} · ${it.second}" } }
            ) { i -> p.aiVoice = Prefs.AI_VOICES[i].first })
            val key = Ui.field(this, "Khóa Google Gemini", p.geminiKey, true)
            val model = Ui.field(this, "Model giọng AI", p.ttsModel)
            val st = Ui.tv(this, "", 13f, Ui.SUB)
            c.addView(key, Ui.lpm(this, 6, 6))
            c.addView(model, Ui.lpm(this, 0, 6))
            c.addView(Ui.button(this, "Lưu và kiểm tra giọng AI") {
                p.geminiKey = key.text.toString()
                p.ttsModel = model.text.toString()
                if (p.geminiKey.isEmpty()) { st.setTextColor(Ui.DANGER); st.text = "Hãy nhập khóa Gemini."; return@button }
                st.setTextColor(Ui.SUB); st.text = "Đang kiểm tra..."
                Thread {
                    val r = AiSpeech.test(p)
                    runOnUiThread {
                        st.setTextColor(if (r == null) Ui.OK else Ui.DANGER)
                        st.text = r ?: "Giọng AI dùng được."
                    }
                }.start()
            }, Ui.lpm(this, 0, 6, WRAP_CONTENT))
            c.addView(st, Ui.lpm(this, 0, 6))
            c.addView(Ui.tv(this, "Giọng AI tự lùi về giọng của máy khi mất mạng hoặc hết lượt. Mỗi câu đọc tốn một lượt gọi Gemini.", 12.5f, Ui.SUB), Ui.lpm(this, 0, 4))
        } else {
            c.addView(Ui.dropRow(this, "Chọn giọng",
                { p.voice.ifEmpty { "Mặc định" } },
                { listOf("Mặc định") + (speaker?.voices() ?: emptyList()) }
            ) { i ->
                val list = listOf("") + (speaker?.voices() ?: emptyList())
                p.voice = list.getOrElse(i) { "" }
                speaker?.applyVoice()
            })
        }
        c.addView(Ui.seekRow(this, "Tốc độ đọc", { v -> String.format("%.1f", v / 10f).replace('.', ',') + "x" }, 5, 30, p.rate10) { p.rate10 = it }, Ui.lpm(this, 6, 10))
        c.addView(Ui.switchRow(this, "Đọc theo cảm xúc",
            "Nhận ra câu giận, buồn, vui hay sợ qua câu chữ để đổi nhịp và giọng đọc.", p.emotion) { p.emotion = it }, Ui.lpm(this, 4, 10))
        c.addView(Ui.switchRow(this, "Tự đọc nhanh khi câu dồn lại",
            "Câu thoại mới tới lúc câu cũ chưa đọc xong thì đọc nhanh hơn để kịp hình.", p.autoFast) { p.autoFast = it }, Ui.lpm(this, 0, 10))
        c.addView(Ui.switchRow(this, "Đọc qua loa TV",
            if (p.tvPaired) "Điện thoại im lặng, tiếng phát ra từ TV." + (if (p.voiceEngine != "system") " Giọng offline và giọng AI do điện thoại tạo rồi gửi sang TV, TV không cần tải gì thêm." else "")
            else "Cần ghép TV trước (mục Phụ đề trên TV).", p.tvSpeak) {
            p.tvSpeak = it
            if (it && !p.tvPaired) Toast.makeText(this, "Chưa ghép TV nên vẫn đọc trên điện thoại.", Toast.LENGTH_SHORT).show()
        }, Ui.lpm(this, 0, 12))
        c.addView(Ui.button(this, "▶  Nghe thử") {
            val text = "Xin chào, đây là giọng đọc bản dịch trong game."
            val onTv = p.tvSpeak && p.tvPaired && p.playOn == "tv" && speaker?.makesAudio() == true
            if (onTv) {
                // tiếng phát ở loa TV: điện thoại tạo âm thanh rồi gửi sang
                speaker?.synthForTv(text) { pcm, speed ->
                    val err = if (pcm == null) "giọng đọc lỗi" else TvLink.postAudio(p.tvIp, p.tvToken, pcm, speed)
                    runOnUiThread { Toast.makeText(this, if (err == null) "Đã gửi tiếng sang TV." else "Không gửi được sang TV: $err", Toast.LENGTH_LONG).show() }
                }
            } else speaker?.speak(text)
        }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
        return c
    }

    // ---------------- Phụ đề ----------------

    private fun subtitleCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "💬", "Phụ đề"))
        c.addView(Ui.switchRow(this, "Hiện câu gốc ${Prefs.srcNameOf(p.lang)}", null, p.showOrig) { p.showOrig = it })
        c.addView(Ui.switchRow(this, "Hiện câu thoại trước", "Hiện câu vừa dịch bằng chữ nhỏ phía trên phụ đề hiện tại (trên điện thoại).", p.showPrev) { p.showPrev = it }, Ui.lpm(this, 10, 0))
        c.addView(Ui.seekRow(this, "Cỡ chữ phụ đề", { v -> "$v%" }, 50, 200, p.subScale) { p.subScale = it }, Ui.lpm(this, 14, 6))
        c.addView(Ui.dropRow(this, "Kiểu chữ", { Prefs.STYLES[p.style] }, { Prefs.STYLES }) { p.style = it })
        c.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1))

        c.addView(Ui.tv(this, "Cách đọc chữ trên hình", 15f, Ui.TEXT, true), Ui.lpm(this, 12, 0))
        c.addView(Ui.segmented(this, listOf("Kỹ", "Nhanh"), if (p.slow) 0 else 1) { p.slow = it == 0; render() }, Ui.lpm(this, 6, 0))
        c.addView(Ui.tv(this, "Kỹ: chờ chữ hiện đủ rồi mới dịch, ít dịch nhầm câu đang hiện dở. Nhanh: dịch ngay khi thấy chữ.", 12.5f, Ui.SUB), Ui.lpm(this, 6, 0))
        val rates = listOf(3, 4, 6, 8)
        val rateSel = rates.indexOfFirst { kotlin.math.abs(1000 / it - p.scanMs) <= 20 }
        c.addView(Ui.tv(this, "Mỗi giây đọc phụ đề mấy lần", 15f, Ui.TEXT, true), Ui.lpm(this, 14, 0))
        c.addView(Ui.segmented(this, rates.map { it.toString() }, rateSel) { p.scanMs = 1000 / rates[it]; render() }, Ui.lpm(this, 6, 0))
        c.addView(Ui.tv(this, "Đọc nhiều lần đỡ sót câu thoại nhưng máy nóng và hao pin hơn. Mặc định 1 lần mỗi giây.", 12.5f, Ui.SUB), Ui.lpm(this, 6, 8))
        c.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1))

        val adv = Ui.vbox(this)
        adv.visibility = View.GONE
        adv.addView(Ui.seekRow(this, "Tần suất quét (chỉnh tinh)",
            { v -> "${v * 100} ms" }, 1, 30, (p.scanMs / 100).coerceIn(1, 30)) { p.scanMs = it * 100 }, Ui.lpm(this, 12, 0))
        adv.addView(Ui.tv(this, "Quét nhanh đỡ sót câu thoại, quét chậm đỡ nóng máy.", 12.5f, Ui.SUB), Ui.lpm(this, 2, 10))
        adv.addView(Ui.switchRow(this, "Dịch menu tự động", "Đứng yên ở menu là tự dịch, tốn pin hơn.", p.autoMenu) { p.autoMenu = it })
        val head = Ui.dropRow(this, "Nâng cao: máy nóng hoặc sót câu thoại", { "" }, { emptyList() }) {}
        head.setOnClickListener { adv.visibility = if (adv.visibility == View.GONE) View.VISIBLE else View.GONE }
        c.addView(head)
        c.addView(adv)
        return c
    }

    // ---------------- Hồ sơ game ----------------

    private fun profileCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "🎮", "Hồ sơ game"))
        c.addView(Ui.dropRow(this, "Hồ sơ", { p.profile }, { p.profiles }) { i ->
            p.profile = p.profiles[i]
            render()
        })
        c.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1))
        val name = Ui.hbox(this)
        name.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10))
        name.addView(Ui.tv(this, "Tên", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        name.addView(Ui.tv(this, p.profile, 15f, Ui.SUB))
        c.addView(name)
        c.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1))
        c.addView(Ui.tv(this, "Đang chơi game", 15f, Ui.TEXT, true), Ui.lpm(this, 12, 6))
        val game = Ui.field(this, "vd: Alan Wake 2, kinh dị", p.game)
        game.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { p.game = s?.toString().orEmpty() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        c.addView(game)
        c.addView(Ui.tv(this, "Việt hóa và dịch theo game (giữ đúng tên, xưng hô nhân vật) khi dùng bộ dịch AI.", 12.5f, Ui.SUB), Ui.lpm(this, 6, 10))
        val btns = Ui.hbox(this)
        c.addView(Ui.button(this, "🕹  Chọn trong Kho game", true) {
            @Suppress("DEPRECATION")
            startActivityForResult(android.content.Intent(this, KhoGameActivity::class.java), 7)
        }, Ui.lpm(this, 0, 8))
        btns.addView(Ui.button(this, "Danh sách hồ sơ") { gameList() }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
        if (p.profile != Prefs.DEFAULT_PROFILE) {
            btns.addView(Ui.button(this, "Xóa hồ sơ này") {
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setMessage("Xóa hồ sơ \"${p.profile}\"?")
                    .setPositiveButton("Xóa") { _, _ -> p.deleteProfile(p.profile); render() }
                    .setNegativeButton("Hủy", null).show()
            }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@SettingsActivity, 10) })
        }
        c.addView(btns)
        c.addView(Ui.divider(this), Ui.lpm(this, 14, 0))
        c.addView(Ui.dropRow(this, "Phong cách dịch", { Prefs.TR_STYLES[p.trStyle].first }, { Prefs.TR_STYLES.map { it.first } }) { i ->
            p.trStyle = i
            if (i == Prefs.CUSTOM_STYLE) promptDialog() else render()
        })
        c.addView(Ui.tv(this, "Phong cách và prompt riêng áp dụng cho dịch AI; dịch trên máy không hỗ trợ prompt nhưng vẫn dùng gói Việt hoá. Mỗi hồ sơ lưu vùng quét riêng.", 12.5f, Ui.SUB), Ui.lpm(this, 2, 6))
        if (p.trStyle == Prefs.CUSTOM_STYLE) c.addView(Ui.button(this, "Sửa prompt riêng") { promptDialog() }, Ui.lpm(this, 4, 6))
        val terms = p.glossary.lines().count { it.contains("=") }
        c.addView(Ui.tv(this, "Gói Việt hoá", 15f, Ui.TEXT, true), Ui.lpm(this, 12, 2))
        c.addView(Ui.tv(this, (if (p.packOn) "Đang dùng gói có sẵn của game" else "Gói có sẵn đang tắt") + (if (terms > 0) " · $terms thuật ngữ của bạn" else "") +
            ". Giữ đúng tên riêng, xưng hô, nhịp và cảm xúc nhân vật.", 12.5f, Ui.SUB), Ui.lpm(this, 2, 10))
        val row = Ui.hbox(this)
        row.addView(Ui.button(this, "＋  Thêm hồ sơ") { addProfile() }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
        row.addView(Ui.button(this, "📦  Gói Việt hoá") { startActivity(android.content.Intent(this, PackActivity::class.java)) }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@SettingsActivity, 10) })
        c.addView(row)
        return c
    }

    private fun promptDialog() {
        val et = Ui.field(this, "Viết chỉ dẫn dịch của riêng bạn", p.customPrompt)
        et.minLines = 6
        et.gravity = android.view.Gravity.TOP
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, "Ví dụ: Dịch như lính đánh thuê lì lợm, xưng tao – mày với đồng đội, gọi sếp là ông chủ, câu cộc, không văn vẻ.", 12.5f, Ui.SUB))
        box.addView(et)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Prompt riêng").setView(box)
            .setPositiveButton("Lưu") { _, _ -> p.customPrompt = et.text.toString(); render() }
            .setNegativeButton("Hủy", null).show()
    }

    private fun gameList() {
        val ps = p.profiles
        Ui.pick(this, "Danh sách game", ps.map { n -> if (p.gameOf(n).isEmpty()) n else "$n · ${p.gameOf(n)}" }) { i ->
            p.profile = ps[i]
            render()
        }
    }

    private fun addProfile() {
        val et = Ui.field(this, "Tên hồ sơ (vd: Elden Ring)", "")
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Thêm hồ sơ")
            .setView(box)
            .setPositiveButton("Thêm") { _, _ ->
                if (p.addProfile(et.text.toString())) render()
                else Toast.makeText(this, "Tên trống hoặc đã có.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null).show()
    }

    // ---------------- Ngôn ngữ ----------------

    private fun languageCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "🌍", "Ngôn ngữ"))
        val app = Ui.hbox(this)
        app.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12))
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, "Ngôn ngữ app", 13f, Ui.SUB))
        col.addView(Ui.tv(this, "🇻🇳  Tiếng Việt", 17f, Ui.TEXT, true))
        app.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
        app.addView(Ui.tv(this, "›", 26f, Ui.SUB))
        app.setOnClickListener { Toast.makeText(this, "Hiện app chỉ có giao diện tiếng Việt.", Toast.LENGTH_SHORT).show() }
        c.addView(app)
        c.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1))
        val src = Prefs.LANGS.filter { it.first != "vi" }
        c.addView(Ui.dropRow(this, "Ngôn ngữ trong game", { Prefs.langLabel(p.lang) }, { src.map { Prefs.langLabel(it.first) } }) { p.lang = src[it].first })
        return c
    }
}
