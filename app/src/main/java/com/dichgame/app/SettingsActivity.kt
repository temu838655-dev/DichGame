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
import android.widget.ScrollView
import android.widget.Toast

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
            if (target == "profile") post { (parent as? ScrollView)?.smoothScrollTo(0, prof.top) }
        }
    }

    // ---------------- Giọng đọc ----------------

    private fun voiceCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "🎙", "Giọng đọc"))
        c.addView(Ui.switchRow(this, "Đọc to bản dịch", "Tắt nếu chỉ muốn xem chữ.", p.speak) { p.speak = it })
        c.addView(Ui.dropRow(this, "Loại giọng",
            { if (p.voiceEngine == "ai") "Giọng AI (Gemini)" else "Giọng của máy" },
            { listOf("Giọng của máy", "Giọng AI (Gemini)") }
        ) { i ->
            p.voiceEngine = if (i == 1) "ai" else "system"
            render()
        })
        if (p.voiceEngine == "ai") {
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
        c.addView(Ui.switchRow(this, "Đọc qua loa TV",
            if (p.tvPaired) "Điện thoại im lặng, tiếng phát ra từ TV." else "Cần ghép TV trước (mục Phụ đề trên TV).", p.tvSpeak) {
            p.tvSpeak = it
            if (it && !p.tvPaired) Toast.makeText(this, "Chưa ghép TV nên vẫn đọc trên điện thoại.", Toast.LENGTH_SHORT).show()
        }, Ui.lpm(this, 0, 12))
        c.addView(Ui.button(this, "▶  Nghe thử") {
            speaker?.speak("Xin chào, đây là giọng đọc bản dịch trong game.")
        }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
        return c
    }

    // ---------------- Phụ đề ----------------

    private fun subtitleCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "💬", "Phụ đề"))
        c.addView(Ui.switchRow(this, "Hiện câu gốc", null, p.showOrig) { p.showOrig = it })
        c.addView(Ui.seekRow(this, "Cỡ chữ phụ đề", { v -> "$v%" }, 50, 200, p.subScale) { p.subScale = it }, Ui.lpm(this, 14, 6))
        c.addView(Ui.dropRow(this, "Kiểu chữ", { Prefs.STYLES[p.style] }, { Prefs.STYLES }) { p.style = it })
        c.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1))

        val adv = Ui.vbox(this)
        adv.visibility = View.GONE
        adv.addView(Ui.seekRow(this, "Tần suất quét",
            { v -> "${v * 100} ms" }, 4, 30, p.scanMs / 100) { p.scanMs = it * 100 }, Ui.lpm(this, 12, 0))
        adv.addView(Ui.tv(this, "Quét nhanh đỡ sót câu thoại, quét chậm đỡ nóng máy.", 12.5f, Ui.SUB), Ui.lpm(this, 2, 10))
        adv.addView(Ui.switchRow(this, "Chữ hiện dần", "Chờ phụ đề hiện hết rồi mới dịch.", p.slow) { p.slow = it }, Ui.lpm(this, 0, 10))
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
        btns.addView(Ui.button(this, "Danh sách game") { gameList() }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
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
        c.addView(Ui.dropRow(this, "Phong cách dịch", { Prefs.TR_STYLES[p.trStyle].first }, { Prefs.TR_STYLES.map { it.first } }) { p.trStyle = it })
        c.addView(Ui.tv(this, "Phong cách áp dụng đầy đủ cho bộ dịch AI. Mỗi hồ sơ lưu vùng quét riêng.", 12.5f, Ui.SUB), Ui.lpm(this, 2, 10))
        c.addView(Ui.tv(this, "Từ điển riêng: ${glossaryCount()}", 15f, Ui.TEXT, true), Ui.lpm(this, 12, 2))
        c.addView(Ui.tv(this, "Ép bộ dịch AI luôn dịch đúng tên riêng, chiêu thức, địa danh của game này.", 12.5f, Ui.SUB), Ui.lpm(this, 2, 10))
        val row = Ui.hbox(this)
        row.addView(Ui.button(this, "＋  Thêm hồ sơ") { addProfile() }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
        row.addView(Ui.button(this, "Sửa từ điển") { glossaryDialog() }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@SettingsActivity, 10) })
        c.addView(row)
        return c
    }

    private fun glossaryCount(): String {
        val n = p.glossary.lines().count { it.contains("=") }
        return if (n == 0) "Chưa có từ nào" else "$n từ"
    }

    private fun glossaryDialog() {
        val et = Ui.field(this, "Mỗi dòng một từ: gốc = tiếng Việt", p.glossary)
        et.minLines = 6
        et.gravity = android.view.Gravity.TOP
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, "Ví dụ:\nHollow Knight = Hiệp sĩ Rỗng\nBonfire = Lửa trại", 12.5f, Ui.SUB))
        box.addView(et)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Từ điển riêng · ${p.profile}")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ -> p.glossary = et.text.toString().trim(); render() }
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
