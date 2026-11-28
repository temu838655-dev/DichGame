package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
    private var skipResume = true
    private var built = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        page = intent?.getStringExtra("page") ?: if (intent?.getStringExtra("goto") == "profile") "profile" else "hub"
        if (BuildConfig.DEBUG) intent?.getStringExtra("demo")?.let { demo(it) }
        speaker = Speaker(this, p)
        speaker?.onAiError = { m -> Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
        render()
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("selftest", false) == true) SelfTest.voice(applicationContext)
    }

    /** Chỉ cho bản debug và bài thử tự động (ci/smoke.sh): dựng sẵn trạng thái để chụp ảnh. */
    private fun demo(kind: String) {
        when (kind) {
            "voice-system" -> { p.narrate = false; p.voiceEngine = "system" }
            "voice-offline" -> { p.narrate = false; p.voiceEngine = "offline" }
            "voice-ai" -> { p.narrate = false; p.voiceEngine = "ai" }
            "errlog" -> try {
                java.io.File(filesDir, "dg-crashes.txt").writeText(
                    "── 28/11 20:41 ──\nBài thử: app bị đóng đột ngột (dữ liệu mẫu của bản debug).\njava.lang.IllegalStateException: mẫu\n  at com.dichgame.app.Demo.run(Demo.kt:1)\n\n" +
                        "── 27/11 09:12 ──\nBài thử: lần đóng đột ngột cũ hơn (dữ liệu mẫu).\n")
            } catch (_: Exception) {}
            "errlog-empty" -> CrashLog.clearHistory(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // quay lại từ trang con: làm mới các giá trị đang chọn hiện ở mỗi hàng
        if (!skipResume && (page == "hub" || page == "voice" || page == "chars")) render()
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
        val keep = if (built) SUi.scrollY(this) else 0
        when (page) {
            "voice" -> voicePage()
            "chars" -> charsPage()
            "subs" -> subsPage()
            "profile" -> profilePage()
            "about" -> aboutPage()
            "errlog" -> errorLogPage()
            else -> hubPage()
        }
        built = true
        SUi.restoreScroll(this, keep)
    }

    private fun open(pg: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", pg))
    }

    private fun dp(n: Number) = Ui.dp(this, n)

    private fun LinearLayout.row(v: View, top: Number = 10, bottom: Number = 10) {
        addView(v, Ui.lpm(context, top, bottom))
    }

    private fun LinearLayout.heading(t: String, top: Number = 22) = SUi.heading(this, t, top)

    private fun LinearLayout.note(t: String, top: Number = 8) = SUi.note(this, t, top)

    private fun LinearLayout.toggle(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        addView(SUi.padded(Ui.switchRow(this@SettingsActivity, title, sub, checked, onChange)))
    }

    // ---------------- Trang chính ----------------

    private fun hubPage() {
        val a = this
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" }
        screen("", back = false) {
            val head = Ui.hbox(a)
            head.addView(Ui.tv(a, "Cài đặt", 32f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
            head.addView(Ui.button(a, "Xong") { finish() }.apply { setPadding(dp(22), dp(10), dp(22), dp(10)) })
            put(head, 8, 14)
            put(SUi.banner(a) { open("about") }, 0, 0)
            put(Hub.card(a, p), 18, 0)
            group(null, 0, 18) {
                addView(SUi.tileSwitchRow(a, Ui.iconTile(a, ToolIcon.Kind.BELL, Color.parseColor("#FF7B7B"), Color.parseColor("#E0403F")),
                    "Nhận tin mới", p.updateNotice) { p.updateNotice = it })
            }
            note("Báo khi có bản mới của app lúc mở app, rồi cập nhật ngay trong app. Tắt thì chỉ kiểm tra khi bấm “Kiểm tra bản mới” trong Giới thiệu và hỗ trợ.")
            group(null, 0, 18) {
                addView(SUi.hubRow(a, Ui.iconTile(a, ToolIcon.Kind.WARN, Color.parseColor("#B58CFF"), Color.parseColor("#7C4DE8")),
                    "Nhật ký lỗi", if (CrashLog.history(a).isEmpty()) "Chưa có" else "Có sự cố") { open("errlog") })
                divide(70)
                addView(SUi.hubRow(a, Ui.iconTile(a, ToolIcon.Kind.DOC, Color.parseColor("#B8B2AA"), Color.parseColor("#7F7A74")),
                    "Giấy phép mã nguồn mở", null) { startActivity(Intent(a, LicensesActivity::class.java)) })
                divide(70)
                addView(SUi.hubRow(a, Ui.iconTile(a, ToolIcon.Kind.HELP, Color.parseColor("#9AA3B2"), Color.parseColor("#5C6678")),
                    "Giới thiệu và hỗ trợ", if (ver.isEmpty()) null else "Bản $ver") { open("about") })
            }
        }
    }

    // ---------------- Nhật ký lỗi ----------------

    private fun errorLogPage() {
        val a = this
        screen("Nhật ký lỗi") {
            val log = CrashLog.history(a)
            if (log.isEmpty()) {
                group(null, 20, 4) {
                    addView(Ui.tv(a, "Chưa có lần nào app bị đóng đột ngột.", 16f, Ui.TEXT, true))
                    addView(Ui.tv(a, "Khi app gặp sự cố, thông tin sự cố được lưu ở đây (mới nhất ở trên cùng) để bạn xem lại hoặc gửi cho người làm app.", 13.5f, Ui.SUB), Ui.lpm(a, 6, 0))
                }
            } else {
                group(null, 16, 4) {
                    addView(Ui.tv(a, log, 12.5f, Ui.TEXT).apply {
                        typeface = Typeface.MONOSPACE
                        setTextIsSelectable(true)
                    })
                }
                group(null, 0, 14) {
                    addView(SUi.textRow(a, "Sao chép nhật ký") {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Nhật ký lỗi", log))
                        SUi.toast(a, "Đã sao chép nhật ký.")
                    })
                    divide()
                    addView(SUi.textRow(a, "Xóa nhật ký", Ui.DANGER) {
                        AlertDialog.Builder(a, R.style.DgDialog)
                            .setTitle("Xóa nhật ký lỗi?")
                            .setMessage("Nhật ký sự cố trên máy này sẽ bị xóa, không hoàn tác được.")
                            .setPositiveButton("Xóa") { _, _ -> CrashLog.clearHistory(a); render() }
                            .setNegativeButton("Giữ lại", null).show()
                    })
                }
            }
            note("Nhật ký chỉ nằm trên máy này, app không tự gửi đi đâu. Muốn báo lỗi thì sao chép rồi dán vào trang báo lỗi trong Giới thiệu và hỗ trợ.", 12)
        }
    }

    // ---------------- Giọng đọc ----------------

    private fun rateText(v: Int) = String.format("%.1f", v / 10f).replace('.', ',') + "x"

    private fun anyVoiceInstalled(): Boolean = Voices.PACKS.any { Voices.installed(this, it.id) }

    private fun voicePage() {
        val a = this
        screen("Giọng đọc") {
            if (p.narrate) note("Thuyết minh luôn đọc to phụ đề tiếng Việt có sẵn, không dịch.", 4)
            put(Ui.segmented(a, listOf("Giọng cơ bản", "Giọng offline", "Giọng AI"),
                when (p.voiceEngine) { "offline" -> 1; "ai" -> 2; else -> 0 }) { i ->
                speaker?.stop()
                p.voiceEngine = when (i) { 1 -> "offline"; 2 -> "ai"; else -> "system" }
                speaker?.prepare()
                render()
            }, 8, 0)
            group(null, 0, 16) { voiceEngineRows(this) }
            voiceEngineNote(this)
            group(null, 0, 16) {
                if (!p.narrate) {
                    toggle("Đọc to bản dịch", null, p.speak) { p.speak = it }
                    divide()
                }
                addView(SUi.padded(SUi.iconSlider(a, "Tốc độ đọc", { rateText(it) }, "🐢", "🐇", 5, 30, p.rate10) { p.rate10 = it }))
                divide()
                toggle("Tự đọc nhanh khi câu dồn lại", null, p.autoFast) { p.autoFast = it }
                divide()
                toggle("Bỏ câu cũ, đọc câu mới nhất", null, p.latestOnly) { p.latestOnly = it }
                divide()
                toggle("Đọc theo cảm xúc", null, p.emotion) { p.emotion = it }
                divide()
                toggle("Đọc qua loa TV",
                    if (p.tvPaired) "Điện thoại im lặng, tiếng phát ra từ TV." + (if (p.voiceEngine != "system") " Giọng AI do điện thoại tạo rồi gửi sang TV, TV không cần tải gì thêm." else "")
                    else "Cần ghép TV trước (mục Phụ đề trên TV).", p.tvSpeak) {
                    p.tvSpeak = it
                    if (it && !p.tvPaired) Toast.makeText(a, "Chưa ghép TV nên vẫn đọc trên điện thoại.", Toast.LENGTH_SHORT).show()
                }
            }
            note("Tự đọc nhanh: câu mới tới lúc câu cũ chưa đọc xong thì đọc nhanh hơn để kịp hình. Bỏ câu cũ, đọc câu mới nhất: bật thì lời thoại dồn lại chỉ giữ 2 câu mới nhất chờ đọc tiếp (giọng máy: dồn tới 3 câu thì xóa hàng, đọc câu mới nhất); tắt thì giữ nhiều câu chờ hơn và đọc lần lượt. Câu chờ quá 10 giây vẫn bị bỏ vì game đã qua đoạn đó. Đọc theo cảm xúc: nhận ra câu giận, buồn, vui hay sợ qua câu chữ để đổi nhịp và giọng đọc.", 10)
            group(null, 0, 16) {
                addView(SUi.textRow(a, "Nghe thử") { listen() })
            }
        }
    }

    private fun voiceEngineRows(c: LinearLayout) {
        val a = this
        when (p.voiceEngine) {
            "offline" -> {
                val pack = Voices.pack(p.offlineVoice) ?: Voices.PACKS[0]
                c.addView(SUi.infoRow(a, "Giọng", pack.name))
                c.divide()
                if (Voices.installed(a, pack.id)) c.addView(SUi.infoRow(a, "Trạng thái", "Đã tải · ${Voices.sizeMb(a, pack.id)} MB", Ui.OK))
                else c.addView(SUi.textRow(a, "Tải giọng (${pack.downloadMb} MB)") { downloadVoice(pack) })
            }
            "ai" -> {
                c.addView(SUi.choiceRow(a, "Giọng",
                    { Prefs.aiVoiceLabel(p.aiVoice) },
                    { Prefs.AI_VOICES.map { it.second } }
                ) { i -> p.aiVoice = Prefs.AI_VOICES[i].first })
                c.divide()
                c.addView(SUi.valueRow(a, null, "Giọng theo nhân vật", if (p.characterVoices) "Bật" else "Tắt") { open("chars") })
                c.divide()
                val n = p.geminiKeys().size
                c.addView(SUi.valueRow(a, null, "Khóa Gemini", if (n == 0) "Chưa có khóa" else "$n khóa", n == 0) {
                    startActivity(Intent(a, TranslateActivity::class.java))
                })
                c.divide()
                c.addView(SUi.valueRow(a, null, "Model giọng AI", p.ttsModel) {
                    SUi.fieldDialog(a, "Model giọng AI", "Tên model", p.ttsModel, reset = { p.ttsModel = ""; render() }) { v ->
                        p.ttsModel = v
                        render()
                    }
                })
                c.divide()
                val st = Ui.tv(a, "", 13f, Ui.SUB).apply {
                    visibility = View.GONE
                    setPadding(dp(16), 0, dp(16), dp(12))
                }
                c.addView(SUi.textRow(a, "Kiểm tra giọng Gemini") { testAiVoice(st) })
                c.addView(st)
            }
            else -> c.addView(SUi.choiceRow(a, "Giọng",
                { p.voice.ifEmpty { "Mặc định" } },
                { listOf("Mặc định") + (speaker?.voices() ?: emptyList()) }
            ) { i ->
                val list = listOf("") + (speaker?.voices() ?: emptyList())
                p.voice = list.getOrElse(i) { "" }
                speaker?.applyVoice()
            })
        }
        if (anyVoiceInstalled()) {
            c.divide()
            c.addView(SUi.textRow(a, "Xóa giọng tải thêm", Ui.DANGER) { confirmDeleteVoices() })
        }
    }

    private fun voiceEngineNote(c: LinearLayout) {
        when (p.voiceEngine) {
            "offline" -> {
                val pack = Voices.pack(p.offlineVoice) ?: Voices.PACKS[0]
                val has = Voices.installed(this, pack.id)
                c.note(pack.desc + (if (has) "" else " Chưa tải nên app đang đọc bằng giọng cơ bản. Cần khoảng ${pack.downloadMb} MB, nên dùng Wi-Fi.") +
                    " Giấy phép giọng: ${pack.license}. ${pack.credit} Số, ký hiệu được đọc thành chữ; tên riêng và chữ nước ngoài được đọc theo kiểu tiếng Việt.")
            }
            "ai" -> c.note("Gemini thử các khóa còn lại khi hết lượt hoặc khóa lỗi; chỉ dùng giọng máy nếu mọi khóa đều thất bại. Câu chưa có âm thanh lưu đệm mới cần gọi Gemini.")
        }
    }

    private fun testAiVoice(st: TextView) {
        st.visibility = View.VISIBLE
        if (p.geminiKeys().isEmpty()) {
            st.setTextColor(Ui.DANGER)
            st.text = "Hãy thêm khóa Gemini ở mục Cách dịch."
            return
        }
        st.setTextColor(Ui.SUB)
        st.text = "Đang kiểm tra..."
        Thread {
            val r = AiSpeech.test(p)
            runOnUiThread {
                st.setTextColor(if (r == null) Ui.OK else Ui.DANGER)
                st.text = r ?: "Giọng Gemini dùng được."
            }
        }.start()
    }

    private fun confirmDeleteVoices() {
        val ids = Voices.PACKS.filter { Voices.installed(this, it.id) }.map { it.id }
        val mb = ids.sumOf { Voices.sizeMb(this, it) }
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Xóa giọng tải thêm?")
            .setMessage("Giọng offline ($mb MB) sẽ bị xóa khỏi máy, muốn dùng lại phải tải lần nữa. Trong lúc đó app đọc bằng giọng cơ bản.")
            .setPositiveButton("Xóa") { _, _ ->
                speaker?.release()
                ids.forEach { Voices.delete(this, it) }
                render()
            }
            .setNegativeButton("Giữ lại", null).show()
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

    // ---------------- Giọng theo nhân vật ----------------

    private fun charsPage() {
        val a = this
        screen("Giọng theo nhân vật") {
            group(null, 0, 4) {
                toggle("Giọng riêng cho nhân vật", "Tự chọn giọng nam/nữ theo thẻ nhân vật, hoặc gán riêng một tên giọng.", p.characterVoices) {
                    p.characterVoices = it
                    render()
                }
                if (p.characterVoices) {
                    divide()
                    addView(SUi.choiceRow(a, "Giọng nam",
                        { Prefs.aiVoiceLabel(p.maleAiVoice) },
                        { Prefs.MALE_AI_VOICES.map { it.second } }
                    ) { i -> p.maleAiVoice = Prefs.MALE_AI_VOICES[i].first })
                    divide()
                    addView(SUi.choiceRow(a, "Giọng nữ",
                        { Prefs.aiVoiceLabel(p.femaleAiVoice) },
                        { Prefs.FEMALE_AI_VOICES.map { it.second } }
                    ) { i -> p.femaleAiVoice = Prefs.FEMALE_AI_VOICES[i].first })
                    divide()
                    addView(SUi.valueRow(a, null, "Danh sách nhân vật", "${p.detectedSpeakers().size} nhân vật") {
                        startActivity(Intent(a, PackActivity::class.java))
                    })
                }
            }
            note("Chỉ dùng khi chọn Giọng AI (Gemini). Trong danh sách nhân vật có thể thêm nhân vật thủ công và gán riêng một tên giọng. App tự chọn giọng ổn định khi phát hiện tên người nói; không đoán giới tính chỉ từ tên.")
            if (p.voiceEngine != "ai") note("Đang không dùng Giọng AI nên cài đặt này chưa có tác dụng. Vào Giọng đọc rồi chọn Giọng AI.", 10)
        }
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

    private val scanRates = listOf(2.0, 2.5, 3.0, 4.0, 6.0, 8.0)

    private fun rateLabel(r: Double) = (if (r % 1.0 == 0.0) r.toInt().toString() else r.toString().replace(".", ",")) + " lần"

    private fun scanRateText(): String {
        val i = scanRates.indexOfFirst { kotlin.math.abs((1000.0 / it).toInt() - p.scanMs) <= 20 }
        if (i >= 0) return rateLabel(scanRates[i])
        return String.format("%.1f", 1000.0 / p.scanMs.coerceAtLeast(1)).replace('.', ',') + " lần"
    }

    private fun subsPage() {
        val a = this
        screen("Phụ đề") {
            group(null, 0, 4) {
                toggle("Hiện câu gốc ${Prefs.srcNameOf(p.lang)}", null, p.showOrig) { p.showOrig = it }
                divide()
                toggle("Hiện câu thoại trước", null, p.showPrev) { p.showPrev = it }
                divide()
                toggle("Giữ câu đủ lâu để đọc", null, p.holdRead) { p.holdRead = it }
            }
            note("Hiện câu thoại trước: câu vừa dịch hiện bằng chữ nhỏ phía trên phụ đề hiện tại (trên điện thoại). Giữ câu đủ lâu để đọc: bật thì mỗi câu ở lại khoảng 1,4 đến 8 giây tùy độ dài, câu mới tới lúc câu cũ chưa đọc xong thì xếp hàng chờ chứ không đè ngay; tắt thì câu mới thay câu cũ gần như ngay (mỗi câu giữ 0,5 đến 2,5 giây). Thay đổi này áp dụng từ lần bắt đầu dịch tiếp theo.")

            heading("Cỡ chữ phụ đề")
            group(null, 0, 0) {
                addView(SUi.padded(SUi.sizeSlider(a, "Cỡ chữ", { "$it%" }, 50, 200, p.subScale) { p.subScale = it }))
                divide()
                addView(SUi.choiceRow(a, "Kiểu chữ", { Prefs.STYLES[p.style] }, { Prefs.STYLES }) { p.style = it })
            }

            heading("Chế độ")
            group(null, 0, 0) {
                toggle("Chỉ nghe, không hiện phụ đề", "Đọc lời dịch tiếng Việt, ẩn phụ đề trên điện thoại và TV.", p.listenOnly) { p.listenOnly = it }
                divide()
                toggle("Dịch bằng tay cầm", "Giữ Create/Share 0,6 giây rồi thả để dịch toàn màn hình. Bấm nhanh vẫn gửi Share.", p.controllerTranslate) { p.controllerTranslate = it }
            }

            heading("Nâng cao")
            group(null, 0, 0) {
                addView(SUi.choiceRow(a, "Cách đọc chữ trên hình", { if (p.slow) "Kỹ (nên dùng)" else "Nhanh" }, { listOf("Kỹ (nên dùng)", "Nhanh") }) { p.slow = it == 0 })
                divide()
                addView(SUi.choiceRow(a, "Mỗi giây đọc phụ đề", { scanRateText() }, { scanRates.map { rateLabel(it) } }) {
                    p.scanMs = (1000.0 / scanRates[it]).toInt()
                    render()
                })
                divide()
                addView(SUi.choiceRow(a, "Chế độ hiệu năng", { listOf("Tiết kiệm pin", "Cân bằng", "Chất lượng").getOrElse(p.perf) { "Cân bằng" } },
                    { listOf("Tiết kiệm pin", "Cân bằng", "Chất lượng") }) { p.perf = it })
                divide()
                toggle("Bỏ qua khi hình đứng yên",
                    "Hình trong khung y hệt lần đọc trước thì không đọc lại; vẫn cứ vài lần đọc một lần để không sót thay đổi nhỏ.", p.skipStatic) { p.skipStatic = it }
                divide()
                toggle("Tự nhận ra game đổi ngôn ngữ",
                    "Chữ trong game đổi sang ngôn ngữ Latin khác (ví dụ Anh sang Pháp) thì tự chuyển bộ dịch. Chuyển giữa chữ Nhật, Hàn, Trung, Nga... vẫn cần chọn tay.", p.autoLang) { p.autoLang = it }
                divide()
                toggle("Dịch menu tự động", "Đứng yên ở menu là tự dịch, tốn pin hơn.", p.autoMenu) { p.autoMenu = it }
                divide()
                addView(SUi.padded(Ui.seekRow(a, "Tần suất quét (chỉnh tinh)",
                    { v -> "${v * 100} ms" }, 1, 30, (p.scanMs / 100).coerceIn(1, 30)) { p.scanMs = it * 100 }))
            }
            note("Máy nóng hoặc sót câu thoại thì chỉnh ở đây. Kỹ: chờ chữ hiện đủ rồi mới dịch, ít dịch nhầm câu đang hiện dở; Nhanh: dịch ngay khi thấy chữ. Đọc nhiều lần mỗi giây đỡ sót câu thoại nhưng máy nóng và hao pin hơn (mặc định 2,5 lần mỗi giây). Tiết kiệm pin: quét thưa gấp đôi, đỡ nóng máy nhưng có thể sót câu hiện rất nhanh. Chất lượng: quét dày hơn (nhanh hơn khoảng 40%), tốn pin hơn. Quét nhanh đỡ sót câu thoại, quét chậm đỡ nóng máy.")

            heading("Công cụ")
            group(null, 0, 0) {
                addView(Ui.navRow(a, null, "Sửa bản dịch vừa rồi", "Chỉnh câu dịch sai, app nhớ cho lần sau") {
                    startActivity(Intent(a, CorrectActivity::class.java))
                })
                divide(16)
                addView(Ui.navRow(a, null, "Số đo lần dịch gần nhất", "Số đếm thật trên máy, không lưu nội dung lời thoại") { showReport() })
            }

            heading("Nhận dạng lời thoại bằng âm thanh")
            group(null, 16, 0) { asrBlock(this) }
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
        card.note("Khi đã tải model, app nghe âm thanh game để bổ sung cho chữ trên màn hình (chữ khó đọc, bị sót, hoặc game không có phụ đề). Chơi PS trong app: lấy âm thanh từ Remote Play, không dùng micro. Xử lý hoàn toàn trên máy, không gửi âm thanh đi đâu và không lưu lại.", 0)
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
            group(null, 0, 4) {
                addView(SUi.choiceRow(a, "Hồ sơ", { p.profile }, {
                    p.profiles.map { n -> if (p.gameOf(n).isEmpty()) n else "$n · ${p.gameOf(n)}" }
                }) { i ->
                    p.profile = p.profiles[i]
                    render()
                })
                divide()
                addView(SUi.textRow(a, "Thêm hồ sơ") { addProfile() })
                if (p.profile != Prefs.DEFAULT_PROFILE) {
                    divide()
                    addView(SUi.textRow(a, "Xóa hồ sơ này", Ui.DANGER) {
                        AlertDialog.Builder(a, R.style.DgDialog)
                            .setMessage("Xóa hồ sơ \"${p.profile}\"?")
                            .setPositiveButton("Xóa") { _, _ -> p.deleteProfile(p.profile); render() }
                            .setNegativeButton("Hủy", null).show()
                    })
                }
            }

            heading("Game")
            group(null, 0, 0) {
                addView(SUi.valueRow(a, null, "Game", p.game.ifBlank { "Chưa chọn" }, p.game.isBlank()) {
                    SUi.fieldDialog(a, "Game đang chơi", "vd: Alan Wake 2, kinh dị", p.game) { v -> p.game = v; render() }
                })
                addView(SUi.whiteButton(a, "Chọn trong Kho game") {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(a, KhoGameActivity::class.java), 7)
                }, Ui.lpm(a, 4, 12).apply { leftMargin = dp(16); rightMargin = dp(16) })
                addView(Ui.tv(a, "Việt hóa và dịch theo game (giữ đúng tên, xưng hô nhân vật) khi dùng bộ dịch AI.", 13f, Ui.SUB).apply {
                    setPadding(dp(16), 0, dp(16), dp(14))
                })
            }

            group(null, 0, 16) {
                addView(SUi.choiceRow(a, "Phong cách dịch", { Prefs.TR_STYLES[p.trStyle].first }, { Prefs.TR_STYLES.map { it.first } }) { i ->
                    p.trStyle = i
                    if (i == Prefs.CUSTOM_STYLE) promptDialog() else render()
                })
                if (p.trStyle == Prefs.CUSTOM_STYLE) {
                    divide()
                    addView(SUi.textRow(a, "Sửa prompt riêng") { promptDialog() })
                }
            }
            note("Phong cách và prompt riêng áp dụng cho dịch AI; dịch trên máy không hỗ trợ prompt nhưng vẫn dùng gói Việt hoá. Mỗi hồ sơ lưu vùng quét riêng.")

            val terms = p.glossary.lines().count { it.contains("=") }
            group(null, 0, 16) {
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
