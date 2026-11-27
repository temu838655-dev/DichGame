package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cài đặt, gom thành một trang chính với bốn mục (Cách dịch, Giọng đọc, Phụ đề, Hồ sơ game) và trang "Giới thiệu".
 * Mỗi mục mở một trang con (cùng Activity này, extra "page"): hub, voice, subs, profile, about. "Cách dịch" ở [TranslateActivity].
 */
class SettingsActivity : Activity() {
    private lateinit var p: Prefs
    private var speaker: Speaker? = null
    private var page = "hub"
    private var advOpen = false
    private var skipResume = true

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        AiErrorLog.initialize(applicationContext)
        page = intent?.getStringExtra("page") ?: if (intent?.getStringExtra("goto") == "profile") "profile" else "hub"
        speaker = Speaker(this, p)
        render()
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("selftest", false) == true) SelfTest.voice(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        // quay lại từ trang con: làm mới các giá trị đang chọn hiện ở mỗi hàng
        if (!skipResume && page == "hub") render()
        skipResume = false
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 7) render()
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }

    private fun render() {
        when (page) {
            "voice" -> voicePage()
            "subs" -> subsPage()
            "profile" -> profilePage()
            "about" -> aboutPage()
            "logs" -> logPage()
            else -> hubPage()
        }
    }

    private fun open(pg: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", pg))
    }

    private fun LinearLayout.row(v: View, top: Number = 10, bottom: Number = 10) {
        addView(v, Ui.lpm(context, top, bottom))
    }

    private fun note(s: String): TextView = Ui.tv(this, s, 12.5f, Ui.SUB)

    // ---------------- Trang chính ----------------

    private fun hubPage() {
        val a = this
        screen("Cài đặt") {
            put(Hub.card(a, p), 4, 0)
            val ver = try { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" }
            group("KHÁC", 0) {
                addView(Ui.navRow(a, null, "Nhật ký lỗi AI", "Lịch sử dịch, OCR, thuyết minh và trạng thái từng khóa Gemini") { open("logs") })
                addView(Ui.navRow(a, Ui.iconTile(a, ToolIcon.Kind.HELP, Color.parseColor("#9AA3B2"), Color.parseColor("#5C6678")),
                    "Giới thiệu và hỗ trợ", "Phiên bản $ver · cập nhật, báo lỗi, giấy phép") { open("about") })
            }
        }
    }

    // ---------------- Giọng đọc ----------------

    private fun rateText(v: Int) = String.format("%.1f", v / 10f).replace('.', ',') + "x"

    private fun voicePage() {
        val a = this
        screen("Giọng đọc") {
            group(null, 16, 4) {
                if (p.narrate) {
                    addView(Ui.tv(a, "Thuyết minh luôn đọc to phụ đề tiếng Việt có sẵn, không dịch.", 13f, Ui.SUB))
                } else {
                    addView(Ui.switchRow(a, "Đọc to bản dịch", "Tắt nếu chỉ muốn xem chữ.", p.speak) { p.speak = it })
                }
                row(Ui.segmented(a, listOf("Cơ bản", "Offline", "AI"),
                    when (p.voiceEngine) { "offline" -> 1; "ai" -> 2; else -> 0 }) { i ->
                    speaker?.stop()
                    p.voiceEngine = when (i) { 1 -> "offline"; 2 -> "ai"; else -> "system" }
                    speaker?.prepare()
                    render()
                }, 14, 6)
                voiceEngineBlock(this)
            }
            group("TỐC ĐỘ ĐỌC") {
                val vt = Ui.tv(a, rateText(p.rate10), 14f, Ui.SUB)
                val head = Ui.hbox(a)
                head.addView(Ui.tv(a, "Tốc độ đọc", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
                head.addView(vt)
                addView(head)
                addView(Ui.seekIcons(a, "🐢", "🐇", 5, 30, p.rate10) { p.rate10 = it; vt.text = rateText(it) }, Ui.lpm(a, 8, 0))
            }
            group("TÙY CHỌN") {
                row(Ui.switchRow(a, "Tự đọc nhanh khi câu dồn lại",
                    "Câu thoại mới tới lúc câu cũ chưa đọc xong thì đọc nhanh hơn để kịp hình.", p.autoFast) { p.autoFast = it }, 2, 12)
                divide()
                row(Ui.switchRow(a, "Đọc theo cảm xúc",
                    "Nhận ra câu giận, buồn, vui hay sợ qua câu chữ để đổi nhịp và giọng đọc.", p.emotion) { p.emotion = it }, 12, 12)
                divide()
                row(Ui.switchRow(a, "Đọc qua loa TV",
                    if (p.tvPaired) "Điện thoại im lặng, tiếng phát ra từ TV." + (if (p.voiceEngine != "system") " Giọng AI do điện thoại tạo rồi gửi sang TV, TV không cần tải gì thêm." else "")
                    else "Cần ghép TV trước (mục Phụ đề trên TV).", p.tvSpeak) {
                    p.tvSpeak = it
                    if (it && !p.tvPaired) Toast.makeText(a, "Chưa ghép TV nên vẫn đọc trên điện thoại.", Toast.LENGTH_SHORT).show()
                }, 12, 2)
            }
            put(Ui.button(a, "▶  Nghe thử", true) { listen() }, 16, 0)
        }
    }

    private fun listen() {
        val text = "Xin chào, đây là giọng đọc bản dịch trong game."
        val onTv = p.tvSpeak && p.tvPaired && p.playOn == "tv" && speaker?.makesAudio() == true
        if (onTv) {
            // tiếng phát ở loa TV: điện thoại tạo âm thanh rồi gửi sang
            speaker?.synthForTv(text) { pcm, speed ->
                val err = if (pcm == null) "giọng đọc lỗi" else TvLink.postAudio(p.tvIp, p.tvToken, pcm, speed)
                runOnUiThread { Toast.makeText(this, if (err == null) "Đã gửi tiếng sang TV." else "Không gửi được sang TV: $err", Toast.LENGTH_LONG).show() }
            }
        } else speaker?.speak(text, interrupt = true)
    }

    private fun voiceEngineBlock(c: LinearLayout) {
        val a = this
        when (p.voiceEngine) {
            "offline" -> c.addView(offlineBlock())
             "ai" -> {
                c.addView(Ui.dropRow(a, "Giọng Gemini",
                    { Prefs.aiVoiceLabel(p.aiVoice) },
                    { Prefs.AI_VOICES.map { it.second } }
                ) { i -> p.aiVoice = Prefs.AI_VOICES[i].first })
                c.divide()
                c.row(Ui.switchRow(a, "Giọng riêng cho nhân vật",
                    "Tự chọn giọng nam/nữ theo thẻ nhân vật, hoặc gán riêng một tên giọng.", p.characterVoices) {
                    p.characterVoices = it
                    render()
                }, 6, 6)
                if (p.characterVoices) {
                    c.addView(Ui.dropRow(a, "Giọng nam",
                        { Prefs.aiVoiceLabel(p.maleAiVoice) },
                        { Prefs.MALE_AI_VOICES.map { it.second } }
                    ) { i -> p.maleAiVoice = Prefs.MALE_AI_VOICES[i].first })
                    c.addView(Ui.dropRow(a, "Giọng nữ",
                        { Prefs.aiVoiceLabel(p.femaleAiVoice) },
                        { Prefs.FEMALE_AI_VOICES.map { it.second } }
                    ) { i -> p.femaleAiVoice = Prefs.FEMALE_AI_VOICES[i].first })
                    c.row(Ui.navRow(a, null, "Danh sách nhân vật và gán giọng",
                        "${p.detectedSpeakers().size} nhân vật đã nhận diện · chọn giọng thủ công") {
                        startActivity(Intent(a, PackActivity::class.java))
                    }, 6, 6)
                    c.row(note("Có thể thêm nhân vật thủ công. App tự chọn giọng ổn định khi phát hiện tên người nói; không đoán giới tính chỉ từ tên."), 6, 6)
                }
                c.divide()
                val n = p.geminiKeys().size
                c.row(Ui.navRow(a, null, "Khóa Gemini",
                    if (n == 0) "Chưa có khóa · bấm để thêm" else "$n khóa · dùng chung với mục Cách dịch") {
                    startActivity(Intent(a, TranslateActivity::class.java))
                }, 2, 2)
                val model = Ui.field(a, "Model giọng AI", p.ttsModel)
                model.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { p.ttsModel = s?.toString().orEmpty() }
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                })
                c.row(model, 6, 6)
                val st = Ui.tv(a, "", 13f, Ui.SUB)
                c.row(Ui.button(a, "Kiểm tra giọng Gemini") {
                    if (p.geminiKeys().isEmpty()) { st.setTextColor(Ui.DANGER); st.text = "Hãy thêm khóa Gemini ở mục Cách dịch."; return@button }
                    st.setTextColor(Ui.SUB); st.text = "Đang kiểm tra..."
                    Thread {
                        val r = AiSpeech.test(p)
                        runOnUiThread {
                            st.setTextColor(if (r == null) Ui.OK else Ui.DANGER)
                            st.text = r ?: "Giọng Gemini dùng được."
                        }
                    }.start()
                }, 4, 4)
                c.addView(st)
                c.row(note("Gemini thử các khóa còn lại khi hết lượt hoặc khóa lỗi; chỉ dùng giọng máy nếu mọi khóa đều thất bại. Câu chưa có âm thanh lưu đệm mới cần gọi Gemini."), 6, 0)
            }
            else -> c.addView(Ui.dropRow(a, "Chọn giọng",
                { p.voice.ifEmpty { "Mặc định" } },
                { listOf("Mặc định") + (speaker?.voices() ?: emptyList()) }
            ) { i ->
                val list = listOf("") + (speaker?.voices() ?: emptyList())
                p.voice = list.getOrElse(i) { "" }
                speaker?.applyVoice()
            })
        }
    }

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
                AlertDialog.Builder(this, R.style.DgDialog)
                    .setTitle("Xóa giọng đọc?")
                    .setMessage("Giọng sẽ bị xóa khỏi máy, muốn dùng lại phải tải lần nữa. Trong lúc đó app đọc bằng giọng cơ bản.")
                    .setPositiveButton("Xóa") { _, _ -> speaker?.release(); Voices.delete(this, pack.id); render() }
                    .setNegativeButton("Giữ lại", null).show()
            }, Ui.lpm(this, 0, 6))
        } else {
            box.addView(Ui.tv(this, "Chưa tải nên app đang đọc bằng giọng cơ bản. Cần khoảng ${pack.downloadMb} MB, nên dùng Wi-Fi.", 13f, Ui.SUB), Ui.lpm(this, 0, 8))
            box.addView(Ui.button(this, "⬇  Tải giọng (${pack.downloadMb} MB)", true) { downloadVoice(pack) }, Ui.lpm(this, 0, 6))
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
        val d = AlertDialog.Builder(this, R.style.DgDialog)
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

    // ---------------- Phụ đề ----------------

    private fun subsPage() {
        val a = this
        screen("Phụ đề") {
            group(null, 16, 4) {
                row(Ui.switchRow(a, "Chỉ nghe, không hiện phụ đề",
                    "Đọc lời dịch tiếng Việt, ẩn phụ đề trên điện thoại và TV.", p.listenOnly) { p.listenOnly = it }, 2, 12)
                divide()
                row(Ui.switchRow(a, "Dịch bằng tay cầm",
                    "Giữ Create/Share 0,6 giây rồi thả để dịch toàn màn hình. Bấm nhanh vẫn gửi Share.", p.controllerTranslate) { p.controllerTranslate = it }, 12, 12)
                divide()
                row(Ui.switchRow(a, "Hiện câu gốc ${Prefs.srcNameOf(p.lang)}", null, p.showOrig) { p.showOrig = it }, 2, 12)
                divide()
                row(Ui.switchRow(a, "Hiện câu thoại trước",
                    "Hiện câu vừa dịch bằng chữ nhỏ phía trên phụ đề hiện tại (trên điện thoại).", p.showPrev) { p.showPrev = it }, 12, 12)
                divide()
                row(Ui.seekRow(a, "Cỡ chữ phụ đề", { v -> "$v%" }, 50, 200, p.subScale) { p.subScale = it }, 12, 6)
                divide()
                addView(Ui.dropRow(a, "Kiểu chữ", { Prefs.STYLES[p.style] }, { Prefs.STYLES }) { p.style = it })
            }
            group("NÂNG CAO", 0) { advancedBlock(this) }
            group("CÔNG CỤ", 0) {
                addView(Ui.navRow(a, null, "Sửa bản dịch vừa rồi", "Chỉnh câu dịch sai, app nhớ cho lần sau") {
                    startActivity(Intent(a, CorrectActivity::class.java))
                })
                divide(16)
                addView(Ui.navRow(a, null, "Số đo lần dịch gần nhất", "Số đếm thật trên máy, không lưu nội dung lời thoại") { showReport() })
            }
            group("NHẬN DẠNG LỜI THOẠI BẰNG ÂM THANH") { asrBlock(this) }
        }
    }

    private fun showReport() {
        val rep = SessionStats.lastReport.ifEmpty { getSharedPreferences("dg", MODE_PRIVATE).getString("lastreport", "").orEmpty() }
            .ifEmpty { "Chưa có lần dịch nào. Bấm bắt đầu dịch một lần, dừng lại rồi quay lại đây." }
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Số đo lần dịch gần nhất")
            .setMessage(rep + "\n\nĐây là số đếm thật trên máy của bạn từ lần dịch vừa rồi; app không lưu nội dung lời thoại.")
            .setPositiveButton("Đóng", null)
            .show()
    }

    /** Khối "Nâng cao" gập được: cách đọc chữ, tần suất, hiệu năng, bỏ qua hình đứng yên, tự nhận ngôn ngữ. */
    private fun advancedBlock(c: LinearLayout) {
        val a = this
        val adv = Ui.vbox(a)
        val pad = Ui.dp(a, 16)
        adv.setPadding(pad, 0, pad, pad)
        adv.visibility = if (advOpen) View.VISIBLE else View.GONE

        adv.addView(Ui.tv(a, "Cách đọc chữ trên hình", 15f, Ui.TEXT, true), Ui.lpm(a, 4, 0))
        adv.addView(Ui.segmented(a, listOf("Kỹ", "Nhanh"), if (p.slow) 0 else 1) { p.slow = it == 0; render() }, Ui.lpm(a, 6, 0))
        adv.addView(note("Kỹ: chờ chữ hiện đủ rồi mới dịch, ít dịch nhầm câu đang hiện dở. Nhanh: dịch ngay khi thấy chữ."), Ui.lpm(a, 6, 0))

        val rates = listOf(2.0, 2.5, 3.0, 4.0, 6.0, 8.0)
        val rateSel = rates.indexOfFirst { kotlin.math.abs((1000.0 / it).toInt() - p.scanMs) <= 20 }
        adv.addView(Ui.tv(a, "Mỗi giây đọc phụ đề mấy lần", 15f, Ui.TEXT, true), Ui.lpm(a, 14, 0))
        adv.addView(Ui.segmented(a, rates.map { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace(".", ",") }, rateSel) {
            p.scanMs = (1000.0 / rates[it]).toInt(); render()
        }, Ui.lpm(a, 6, 0))
        adv.addView(note("Đọc nhiều lần đỡ sót câu thoại nhưng máy nóng và hao pin hơn. Mặc định 2,5 lần mỗi giây (400 ms)."), Ui.lpm(a, 6, 0))

        adv.addView(Ui.tv(a, "Chế độ hiệu năng", 15f, Ui.TEXT, true), Ui.lpm(a, 14, 0))
        adv.addView(Ui.segmented(a, listOf("Tiết kiệm pin", "Cân bằng", "Chất lượng"), p.perf) { p.perf = it; render() }, Ui.lpm(a, 6, 0))
        adv.addView(note("Tiết kiệm pin: quét thưa gấp đôi, đỡ nóng máy nhưng có thể sót câu hiện rất nhanh. Chất lượng: quét dày hơn (nhanh hơn khoảng 40%), tốn pin hơn."), Ui.lpm(a, 6, 4))

        adv.divide()
        adv.addView(Ui.switchRow(a, "Bỏ qua khi hình đứng yên",
            "Hình trong khung y hệt lần đọc trước thì không đọc lại; vẫn cứ vài lần đọc một lần để không sót thay đổi nhỏ.", p.skipStatic) { p.skipStatic = it }, Ui.lpm(a, 12, 0))
        adv.addView(Ui.switchRow(a, "Tự nhận ra game đổi ngôn ngữ",
            "Chữ trong game đổi sang ngôn ngữ Latin khác (ví dụ Anh sang Pháp) thì tự chuyển bộ dịch. Chuyển giữa chữ Nhật, Hàn, Trung, Nga... vẫn cần chọn tay.", p.autoLang) { p.autoLang = it }, Ui.lpm(a, 12, 0))
        adv.addView(Ui.switchRow(a, "Dịch menu tự động", "Đứng yên ở menu là tự dịch, tốn pin hơn.", p.autoMenu) { p.autoMenu = it }, Ui.lpm(a, 12, 0))
        adv.addView(Ui.seekRow(a, "Tần suất quét (chỉnh tinh)",
            { v -> "${v * 100} ms" }, 1, 30, (p.scanMs / 100).coerceIn(1, 30)) { p.scanMs = it * 100 }, Ui.lpm(a, 14, 0))
        adv.addView(note("Quét nhanh đỡ sót câu thoại, quét chậm đỡ nóng máy."), Ui.lpm(a, 2, 0))

        val head = Ui.hbox(a)
        head.setPadding(pad, Ui.dp(a, 14), pad, Ui.dp(a, 14))
        head.addView(Ui.tv(a, "Cách đọc chữ, hiệu năng, máy nóng hoặc sót câu", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        val arrow = Ui.tv(a, if (advOpen) "▴" else "▾", 16f, Ui.GOLD)
        head.addView(arrow)
        head.isClickable = true
        Ui.ripple(head, 22)
        head.setOnClickListener {
            advOpen = !advOpen
            adv.visibility = if (advOpen) View.VISIBLE else View.GONE
            arrow.text = if (advOpen) "▴" else "▾"
        }
        c.addView(head)
        c.addView(adv)
    }

    // ---------------- Nhận dạng lời thoại từ âm thanh game ----------------

    private fun asrBlock(card: LinearLayout) {
        val directory = java.io.File(filesDir, "whisper-tiny")
        val files = listOf("encoder.onnx", "decoder.onnx", "tokens.txt").map { java.io.File(directory, it) }
        val installed = WhisperTinyModelInstaller(directory).isInstalled()
        card.addView(Ui.tv(
            this,
            if (installed) "✔ Đã có bộ nhận dạng Whisper Tiny đa ngôn ngữ trên máy."
            else "Chưa tải model. Cần khoảng 104 MB và thêm dung lượng trống để cài đặt.",
            13f,
            if (installed) Ui.OK else Ui.SUB
        ), Ui.lpm(this, 0, 8))
        card.addView(note("Khi đã tải model, app nghe âm thanh game để bổ sung cho chữ trên màn hình (chữ khó đọc, bị sót, hoặc game không có phụ đề). Chơi PS trong app: lấy âm thanh từ Remote Play, không dùng micro. Xử lý hoàn toàn trên máy, không gửi âm thanh đi đâu và không lưu lại."), Ui.lpm(this, 0, 10))
        card.divide()
        card.row(Ui.switchRow(this, "Nghe âm thanh game chạy trên điện thoại (thử nghiệm)",
            "Cần Android 10 trở lên, quyền ghi âm và model ở trên; áp dụng khi bấm Bắt đầu dịch từ màn hình chính. Thu âm thanh trong của máy; game có thể chặn kiểu thu này.", p.phoneAudio) { p.phoneAudio = it; render() }, 12, 4)
        if (p.phoneAudio) {
            card.row(Ui.switchRow(this, "Game chặn thì nghe qua micro",
                "Chất lượng kém hơn và có thể thu cả giọng bạn hoặc tiếng xung quanh; không dùng được khi đeo tai nghe. Tắt thì chỉ dịch bằng chữ trên màn hình.", p.phoneAudioMic) { p.phoneAudioMic = it }, 8, 4)
        }
        if (installed) {
            card.row(Ui.button(this, "Xóa model Whisper khỏi máy") {
                AlertDialog.Builder(this, R.style.DgDialog)
                    .setTitle("Xóa model nhận dạng?")
                    .setMessage("Bạn cần tải lại khoảng 104 MB nếu muốn dùng nhận dạng lời thoại bằng âm thanh.")
                    .setPositiveButton("Xóa") { _, _ ->
                        files.forEach { runCatching { it.delete() } }
                        render()
                    }
                    .setNegativeButton("Giữ lại", null).show()
            }, 10, 0)
        } else {
            card.row(Ui.button(this, "⬇  Tải model Whisper Tiny (khoảng 104 MB)", true) { downloadWhisperModel() }, 10, 0)
        }
    }

    private fun downloadWhisperModel() {
        val directory = java.io.File(filesDir, "whisper-tiny")
        val installer = WhisperTinyModelInstaller(directory)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val message = Ui.tv(this, "Chuẩn bị tải model...", 13f, Ui.TEXT)
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad, pad, pad / 2)
        box.addView(message, Ui.lpm(this, 0, 8))
        box.addView(progress, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
        val dialog = AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Cài model nhận dạng lời thoại")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Hủy") { _, _ -> installer.cancel() }
            .create()
        dialog.show()

        Thread {
            var error: String? = null
            try {
                installer.install { progressInfo ->
                    val total = WhisperTinyModelInstaller.assets().sumOf { it.size }
                    val completedBefore = WhisperTinyModelInstaller.assets().take(progressInfo.fileIndex - 1).sumOf { it.size }
                    val percent = ((completedBefore + progressInfo.downloadedBytes) * 100L / total).toInt().coerceIn(0, 100)
                    runOnUiThread {
                        progress.progress = percent
                        message.text = "Đang tải ${progressInfo.fileName}: $percent%"
                    }
                }
            } catch (e: Throwable) {
                error = e.message ?: "Không tải được model Whisper."
            }
            val resultError = error
            runOnUiThread {
                runCatching { dialog.dismiss() }
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (resultError == null) {
                    Toast.makeText(this, "Đã cài model Whisper Tiny đa ngôn ngữ.", Toast.LENGTH_LONG).show()
                    render()
                } else {
                    Toast.makeText(this, resultError, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ---------------- Hồ sơ game ----------------

    private fun profilePage() {
        val a = this
        screen("Hồ sơ game") {
            group("HỒ SƠ", 16, 4) {
                addView(Ui.dropRow(a, "Hồ sơ", { p.profile }, {
                    p.profiles.map { n -> if (p.gameOf(n).isEmpty()) n else "$n · ${p.gameOf(n)}" }
                }) { i ->
                    p.profile = p.profiles[i]
                    render()
                })
                val btns = Ui.hbox(a)
                btns.addView(Ui.button(a, "＋  Thêm hồ sơ") { addProfile() }, Ui.lp(0, WRAP_CONTENT, 1f))
                if (p.profile != Prefs.DEFAULT_PROFILE) {
                    btns.addView(Ui.button(a, "Xóa hồ sơ") {
                        AlertDialog.Builder(a, R.style.DgDialog)
                            .setMessage("Xóa hồ sơ \"${p.profile}\"?")
                            .setPositiveButton("Xóa") { _, _ -> p.deleteProfile(p.profile); render() }
                            .setNegativeButton("Hủy", null).show()
                    }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(a, 10) })
                }
                row(btns, 8, 0)
            }
            group("GAME ĐANG CHƠI") {
                val game = Ui.field(a, "vd: Alan Wake 2, kinh dị", p.game)
                game.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { p.game = s?.toString().orEmpty() }
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                })
                addView(game)
                row(note("Việt hóa và dịch theo game (giữ đúng tên, xưng hô nhân vật) khi dùng bộ dịch AI."), 6, 10)
                addView(Ui.button(a, "🕹  Chọn trong Kho game", true) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(a, KhoGameActivity::class.java), 7)
                })
            }
            group("CÁCH DỊCH CỦA HỒ SƠ NÀY", 16) {
                addView(Ui.dropRow(a, "Phong cách dịch", { Prefs.TR_STYLES[p.trStyle].first }, { Prefs.TR_STYLES.map { it.first } }) { i ->
                    p.trStyle = i
                    if (i == Prefs.CUSTOM_STYLE) promptDialog() else render()
                })
                row(note("Phong cách và prompt riêng áp dụng cho dịch AI; dịch trên máy không hỗ trợ prompt nhưng vẫn dùng gói Việt hoá. Mỗi hồ sơ lưu vùng quét riêng."), 2, 6)
                if (p.trStyle == Prefs.CUSTOM_STYLE) row(Ui.button(a, "Sửa prompt riêng") { promptDialog() }, 4, 6)
            }
            val terms = p.glossary.lines().count { it.contains("=") }
            group("GÓI VIỆT HOÁ", 0) {
                addView(Ui.navRow(a, null, "Gói Việt hoá",
                    (if (p.packOn) "Đang dùng gói có sẵn của game" else "Gói có sẵn đang tắt") + (if (terms > 0) " · $terms thuật ngữ của bạn" else "") +
                        ". Giữ đúng tên riêng, xưng hô, nhịp và cảm xúc nhân vật.") {
                    startActivity(Intent(a, PackActivity::class.java))
                })
            }
        }
    }

    private fun promptDialog() {
        val et = Ui.field(this, "Viết chỉ dẫn dịch của riêng bạn", p.customPrompt)
        et.minLines = 6
        et.gravity = Gravity.TOP
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, "Ví dụ: Dịch như lính đánh thuê lì lợm, xưng tao – mày với đồng đội, gọi sếp là ông chủ, câu cộc, không văn vẻ.", 12.5f, Ui.SUB))
        box.addView(et)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Prompt riêng").setView(box)
            .setPositiveButton("Lưu") { _, _ -> p.customPrompt = et.text.toString(); render() }
            .setNegativeButton("Hủy", null).show()
    }

    private fun addProfile() {
        val et = Ui.field(this, "Tên hồ sơ (vd: Elden Ring)", "")
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Thêm hồ sơ")
            .setView(box)
            .setPositiveButton("Thêm") { _, _ ->
                if (p.addProfile(et.text.toString())) render()
                else Toast.makeText(this, "Tên trống hoặc đã có.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null).show()
    }

    // ---------------- Nhật ký lỗi AI ----------------

    private fun logPage() {
        val a = this
        screen("Nhật ký lỗi") {
            group("TRẠNG THÁI KHÓA GEMINI", 16, 4) {
                val keys = p.geminiStoredKeys()
                if (keys.isEmpty()) addView(note("Chưa cấu hình khóa Gemini."))
                keys.forEachIndexed { index, key ->
                    val status = KeyRing.status(key, p.geminiModel)
                    val state = when (status.state) {
                        KeyRing.State.OK -> "Hoạt động"
                        KeyRing.State.QUOTA -> "Cooldown còn ${AiRetry.humanWait(status.leftMs)}"
                        KeyRing.State.BAD -> "Lỗi / khóa không hợp lệ"
                    }
                    val suffix = key.takeLast(4)
                    addView(Ui.tv(a, "Khóa ${index + 1} (…$suffix) · $state", 13.5f, Ui.TEXT))
                }
                row(Ui.button(a, "Đặt lại trạng thái khóa") {
                    KeyRing.reset()
                    Toast.makeText(a, "Đã đặt lại cooldown và trạng thái khóa trong phiên.", Toast.LENGTH_SHORT).show()
                    render()
                }, 8, 0)
            }
            group("SỰ KIỆN GẦN ĐÂY", 16, 4) {
                val lines = AiErrorLog.entries()
                addView(Ui.tv(a, if (lines.isEmpty()) "Chưa có nhật ký." else lines.take(100).joinToString("\n"), 12.5f, Ui.SUB))
                row(Ui.button(a, "Sao chép nhật ký") {
                    val clip = android.content.ClipData.newPlainText("DichGame AI log", AiErrorLog.exportText())
                    (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(a, "Đã sao chép nhật ký.", Toast.LENGTH_SHORT).show()
                }, 8, 4)
                row(Ui.button(a, "Xóa nhật ký") {
                    AiErrorLog.clear()
                    Toast.makeText(a, "Đã xóa nhật ký.", Toast.LENGTH_SHORT).show()
                    render()
                }, 4, 0)
            }
        }
    }

    // ---------------- Giới thiệu và hỗ trợ ----------------

    private fun aboutPage() {
        val a = this
        screen("Giới thiệu") {
            val ver = try { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() } catch (_: Exception) { "?" }
            group("PHIÊN BẢN", 16, 4) {
                addView(Ui.tv(a, "Dịch game $ver" + (if (BuildConfig.BUILD_NO > 0) " (bản dựng ${BuildConfig.BUILD_NO})" else ""), 16f, Ui.TEXT, true))
                addView(Ui.tv(a, "Có bản mới thì app tự báo khi mở, và cập nhật ngay trong app, không cần ra trình duyệt.", 13.5f, Ui.SUB), Ui.lpm(a, 6, 0))
                row(Ui.button(a, "Kiểm tra bản mới") { UpdateUi.show(a, check = true) }, 12, 6)
            }
            group("HỖ TRỢ", 0) {
                addView(Ui.navRow(a, null, "Báo lỗi / yêu cầu game", "Mở trang tạo báo cáo trên GitHub, đã điền sẵn thông tin máy") { report() })
                divide(16)
                addView(Ui.navRow(a, null, "Trang dự án trên GitHub", com.dichgame.update.Update.REPO) { openUrl("https://github.com/" + com.dichgame.update.Update.REPO) })
                divide(16)
                addView(Ui.navRow(a, null, "Giấy phép và ghi công", "Chiaki, sherpa-onnx, Piper và các thư viện khác") {
                    startActivity(Intent(a, LicensesActivity::class.java))
                })
            }
        }
    }

    private fun openUrl(u: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
        catch (_: Exception) { Toast.makeText(this, "Không mở được trình duyệt.", Toast.LENGTH_SHORT).show() }
    }

    /** Mở trang tạo báo cáo trên GitHub, điền sẵn thông tin máy. Bạn xem lại rồi mới gửi. */
    private fun report() {
        val body = "Mô tả (lỗi gặp phải, hoặc tên game cần hỗ trợ):\n\n\n---\n" +
            "Phiên bản: " + (try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }) + "\n" +
            "Android: " + android.os.Build.VERSION.RELEASE + "\n" +
            "Máy: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n" +
            "Ngôn ngữ game: " + Prefs.langName(p.lang) + "\n" +
            "Game: " + p.game
        openUrl("https://github.com/" + com.dichgame.update.Update.REPO + "/issues/new?title=" + android.net.Uri.encode("[Lỗi/Yêu cầu game] ") + "&body=" + android.net.Uri.encode(body))
    }
}
