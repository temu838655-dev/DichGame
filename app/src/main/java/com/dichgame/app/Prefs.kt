package com.dichgame.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Toàn bộ cài đặt của app, lưu trong SharedPreferences "dg" (giữ nguyên các khóa cũ để CaptureService vẫn đọc được). */
class Prefs(ctx: Context) {
    private val app: Context = ctx.applicationContext
    val sp: SharedPreferences = app.getSharedPreferences("dg", Context.MODE_PRIVATE)

    companion object {
        const val ENGINE_LOCAL = "local"
        const val ENGINE_CLOUD = "cloud"
        const val ENGINE_GEMINI = "gemini"
        const val DEFAULT_PROFILE = "Mặc định"
        /** Ngôn ngữ chữ trong game đọc được bằng OCR trên máy (chữ Latin, Hindi, Nhật, Hàn, Trung). "vi" chỉ dùng cho chế độ Thuyết minh. */
        val LANGS = listOf(
            "en" to "Tiếng Anh", "ja" to "Tiếng Nhật", "ko" to "Tiếng Hàn", "zh" to "Tiếng Trung",
            "fr" to "Tiếng Pháp", "de" to "Tiếng Đức", "es" to "Tiếng Tây Ban Nha", "pt" to "Tiếng Bồ Đào Nha",
            "it" to "Tiếng Ý", "id" to "Tiếng Indonesia", "nl" to "Tiếng Hà Lan", "pl" to "Tiếng Ba Lan",
            "tr" to "Tiếng Thổ Nhĩ Kỳ", "hi" to "Tiếng Hindi",
            "sv" to "Tiếng Thụy Điển", "cs" to "Tiếng Séc", "ro" to "Tiếng Romania", "hu" to "Tiếng Hungary",
            "da" to "Tiếng Đan Mạch", "fi" to "Tiếng Phần Lan", "no" to "Tiếng Na Uy", "ms" to "Tiếng Mã Lai",
            "ru" to "Tiếng Nga", "uk" to "Tiếng Ukraina", "th" to "Tiếng Thái", "ar" to "Tiếng Ả Rập",
            "el" to "Tiếng Hy Lạp", "he" to "Tiếng Do Thái", "vi" to "Tiếng Việt"
        )
        /** Chữ mà bộ đọc chữ trên máy không có: app nhờ Google Gemini đọc ảnh (cần khóa Gemini, ảnh chụp được gửi cho Google). */
        val CLOUD_OCR = setOf("ru", "uk", "th", "ar", "el", "he")
        fun isCloudOcr(code: String) = code in CLOUD_OCR
        /** Tên hiển thị trong danh sách chọn ngôn ngữ. */
        fun langLabel(code: String) = langName(code) + if (isCloudOcr(code)) " (đọc bằng Gemini)" else ""
        /** Chữ Nhật, Hàn, Trung không có dấu cách giữa các từ. */
        fun isCjk(code: String) = code == "ja" || code == "ko" || code == "zh"
        /** Tên ngôn ngữ viết thường để đưa vào câu lệnh cho AI, vd "tiếng Pháp". */
        fun srcNameOf(code: String) = langName(code).replaceFirst("Tiếng", "tiếng")
        val STYLES = listOf("Chữ trắng, nền đen", "Chữ vàng, nền đen", "Chữ đen, nền trắng", "Chữ trắng, không nền")
        /** Phong cách dịch: tên hiển thị và chỉ dẫn đưa cho AI. */
        val TR_STYLES = listOf(
            "Tự nhiên" to "Dịch tự nhiên, trôi chảy như người Việt nói.",
            "Thuyết minh" to "Dịch theo giọng thuyết minh phim: câu gọn, rõ nghĩa, dễ nghe khi đọc to.",
            "Ngắn gọn" to "Dịch ngắn gọn nhất có thể, đủ ý, phù hợp phụ đề hiện nhanh.",
            "Sát nghĩa" to "Dịch sát nghĩa nguyên văn, ít diễn đạt lại.",
            "Anh – em" to "Dịch theo giọng gần gũi: nhân vật xưng hô anh – em, thân mật như người trong nhà.",
            "Bụi bặm, đường phố" to "Dịch theo giọng đường phố bụi bặm: lóng, thô ráp, cộc; chỉ chửi thề khi bản gốc có chửi, không văn vẻ.",
            "Cao bồi miền Tây" to "Dịch theo giọng cao bồi miền Tây: chậm rãi, khô, từng trải; hay dùng tôi, ông, chú mày, nhóc.",
            "Podcast" to "Dịch theo giọng podcast: tự nhiên, thân mật, như đang kể chuyện cho người nghe.",
            "Tin tức" to "Dịch theo giọng bản tin: trang trọng, mạch lạc, trung lập, câu đầy đủ.",
            "Học tập" to "Dịch rõ nghĩa để học: giữ cấu trúc câu gốc, từ vựng chuẩn, dễ đối chiếu.",
            "Prompt riêng" to ""
        )
        /** Vị trí của "Prompt riêng" trong TR_STYLES: dùng chỉ dẫn do người dùng tự viết. */
        val CUSTOM_STYLE = TR_STYLES.size - 1
        /** Giọng AI của Gemini: (mã giọng, mô tả). */
        val AI_VOICES = listOf(
            "Kore" to "Chắc chắn", "Puck" to "Vui tươi", "Charon" to "Rõ ràng, thông tin", "Zephyr" to "Sáng",
            "Sulafat" to "Ấm", "Aoede" to "Nhẹ nhàng", "Achird" to "Thân thiện", "Gacrux" to "Trưởng thành"
        )
        fun langName(code: String) = LANGS.firstOrNull { it.first == code }?.second ?: "Tiếng Anh"
    }

    /** Chế độ: "translate" (dịch sang tiếng Việt) hoặc "narrate" (thuyết minh, đọc phụ đề có sẵn). */
    var narrate: Boolean
        get() = sp.getBoolean("narrate", false)
        set(v) = sp.edit().putBoolean("narrate", v).apply()

    /** Ngôn ngữ trong game: en, ja, ko, zh. Khi thuyết minh thì nguồn luôn là tiếng Việt. */
    var lang: String
        get() = sp.getString("langc", null) ?: arrayOf("en", "ja", "ko", "zh")[sp.getInt("lang", 0).coerceIn(0, 3)]
        set(v) = sp.edit().putString("langc", v).apply()

    var engine: String
        get() = sp.getString("engine", null) ?: if (sp.getString("key", "").isNullOrEmpty()) ENGINE_LOCAL else ENGINE_GEMINI
        set(v) = sp.edit().putString("engine", v).apply()

    var geminiKey: String
        get() = sp.getString("key", "").orEmpty()
        set(v) = sp.edit().putString("key", v.trim()).apply()
    var geminiModel: String
        get() = sp.getString("gmodel", "gemini-flash-latest").orEmpty().ifBlank { "gemini-flash-latest" }
        set(v) = sp.edit().putString("gmodel", v.trim()).apply()

    /** CloudAPI: dịch vụ AI đám mây theo chuẩn OpenAI (/chat/completions), cần địa chỉ, khóa và tên model. */
    var cloudUrl: String
        get() = sp.getString("curl", "").orEmpty()
        set(v) = sp.edit().putString("curl", v.trim()).apply()
    var cloudKey: String
        get() = sp.getString("ckey", "").orEmpty()
        set(v) = sp.edit().putString("ckey", v.trim()).apply()
    var cloudModel: String
        get() = sp.getString("cmodel", "gemini-3.1-flash-lite").orEmpty()
        set(v) = sp.edit().putString("cmodel", v.trim()).apply()

    /** Số câu trước đưa cho AI để hiểu mạch truyện. */
    /** Từ điển riêng của hồ sơ hiện tại, mỗi dòng "gốc = tiếng Việt". Dùng cho cả dịch AI lẫn dịch trên máy. */
    var glossary: String
        get() = sp.getString("gloss_$profile", "").orEmpty()
        set(v) = sp.edit().putString("gloss_$profile", v).apply()

    var ctxCount: Int
        get() = sp.getInt("ctxn", 3).coerceIn(0, 10)
        set(v) = sp.edit().putInt("ctxn", v.coerceIn(0, 10)).apply()

    /** Phong cách dịch AI (chỉ số trong TR_STYLES). */
    var trStyle: Int
        get() = sp.getInt("trstyle", 1).coerceIn(0, TR_STYLES.size - 1)
        set(v) = sp.edit().putInt("trstyle", v).apply()

    /** Chỉ dẫn dịch đang dùng: phong cách đã chọn, hoặc prompt riêng. */
    fun styleText(): String =
        if (trStyle == CUSTOM_STYLE) customPrompt.ifBlank { TR_STYLES[0].second } else TR_STYLES[trStyle].second

    /** Prompt riêng do người dùng viết, dùng khi chọn phong cách "Prompt riêng". */
    var customPrompt: String
        get() = sp.getString("customprompt", "").orEmpty()
        set(v) = sp.edit().putString("customprompt", v.trim()).apply()

    /** Dùng gói Việt hoá có sẵn của game (thuật ngữ, thẻ nhân vật, câu thoại chuẩn). */
    var packOn: Boolean
        get() = sp.getBoolean("packon", true)
        set(v) = sp.edit().putBoolean("packon", v).apply()

    /** Thẻ nhân vật của hồ sơ hiện tại, mỗi dòng "Tên = mô tả giọng, xưng hô; nhịp: chậm". */
    var cast: String
        get() = sp.getString("cast_$profile", "").orEmpty()
        set(v) = sp.edit().putString("cast_$profile", v).apply()

    /** Dịch theo cảm xúc và nhịp nhân vật (AI trả thẻ cảm xúc, app dùng để đổi giọng đọc). */
    var emoTr: Boolean
        get() = sp.getBoolean("emotr", true)
        set(v) = sp.edit().putBoolean("emotr", v).apply()
    /** Hiện nhãn cảm xúc nhỏ phía trên phụ đề (trên điện thoại). */
    var emoLabel: Boolean
        get() = sp.getBoolean("emolabel", true)
        set(v) = sp.edit().putBoolean("emolabel", v).apply()

    /** Khoảng cách giữa hai lần quét khung phụ đề, mili giây. Nhỏ hơn đỡ sót câu, lớn hơn đỡ nóng máy. */
    var scanMs: Int
        get() = sp.getInt("scanms", 1000).coerceIn(100, 3000)
        set(v) = sp.edit().putInt("scanms", v.coerceIn(100, 3000)).apply()

    /** "system" = giọng của máy, "ai" = giọng AI Gemini (cần khóa Gemini), "offline" = giọng đã tải về máy. AI và offline lỗi thì tự lùi về giọng máy. */
    var voiceEngine: String
        get() = sp.getString("voiceengine", "system").orEmpty()
        set(v) = sp.edit().putString("voiceengine", v).apply()
    var aiVoice: String
        get() = sp.getString("aivoice", "Kore").orEmpty().ifBlank { "Kore" }
        set(v) = sp.edit().putString("aivoice", v).apply()
    var ttsModel: String
        get() = sp.getString("ttsmodel", "gemini-3.8-flash-tts").orEmpty().ifBlank { "gemini-3.8-flash-tts" }
        set(v) = sp.edit().putString("ttsmodel", v.trim()).apply()
    val aiVoiceOn: Boolean get() = voiceEngine == "ai" && geminiKey.isNotEmpty()
    /** Mã gói giọng offline đang chọn (xem Voices.PACKS). */
    var offlineVoice: String
        get() = sp.getString("offvoice", "vais1000").orEmpty().ifBlank { "vais1000" }
        set(v) = sp.edit().putString("offvoice", v).apply()

    var voice: String
        get() = sp.getString("voice", "").orEmpty()
        set(v) = sp.edit().putString("voice", v).apply()
    /** Tốc độ đọc theo bước 0,1 (14 = 1,4x). */
    var rate10: Int
        get() = sp.getInt("rate10", 10).coerceIn(5, 30)
        set(v) = sp.edit().putInt("rate10", v.coerceIn(5, 30)).apply()
    var emotion: Boolean
        get() = sp.getBoolean("emotion", false)
        set(v) = sp.edit().putBoolean("emotion", v).apply()

    var showOrig: Boolean
        get() = sp.getBoolean("showorig", false)
        set(v) = sp.edit().putBoolean("showorig", v).apply()
    /** Hiện câu vừa dịch trước đó bằng chữ nhỏ phía trên phụ đề hiện tại. */
    var showPrev: Boolean
        get() = sp.getBoolean("showprev", false)
        set(v) = sp.edit().putBoolean("showprev", v).apply()
    /** Khi câu thoại dồn lại (câu mới tới lúc câu cũ chưa đọc xong) thì tự đọc nhanh hơn. */
    var autoFast: Boolean
        get() = sp.getBoolean("autofast", true)
        set(v) = sp.edit().putBoolean("autofast", v).apply()
    /** Các game đã chọn trong Kho game, mới nhất đứng đầu. */
    val played: List<String>
        get() {
            val out = ArrayList<String>()
            try {
                val a = JSONArray(sp.getString("played", "[]"))
                for (i in 0 until a.length()) out.add(a.getString(i))
            } catch (_: Exception) {}
            return out
        }
    fun addPlayed(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val l = (listOf(n) + played.filter { it != n }).take(30)
        sp.edit().putString("played", JSONArray(l).toString()).apply()
    }
    /** Cỡ chữ phụ đề, phần trăm so với cỡ gốc (50..200). */
    var subScale: Int
        get() = sp.getInt("subscale", 100).coerceIn(50, 200)
        set(v) = sp.edit().putInt("subscale", v.coerceIn(50, 200)).apply()
    var style: Int
        get() = sp.getInt("style", 0).coerceIn(0, 3)
        set(v) = sp.edit().putInt("style", v).apply()

    /** "phone" hoặc "tv": nơi chơi game. */
    var playOn: String
        get() = sp.getString("playon", "phone").orEmpty()
        set(v) = sp.edit().putString("playon", v).apply()
    var tvSpeak: Boolean
        get() = sp.getBoolean("tvspeak", false)
        set(v) = sp.edit().putBoolean("tvspeak", v).apply()
    val tvIp: String get() = sp.getString("tvip", "").orEmpty()
    var tvToken: String
        get() = sp.getString("tvtoken", "").orEmpty()
        set(v) = sp.edit().putString("tvtoken", v).apply()
    val tvPaired: Boolean get() = tvIp.isNotEmpty() && tvToken.isNotEmpty()
    fun saveTv(ip: String, token: String) = sp.edit().putString("tvip", ip).putString("tvtoken", token).apply()
    fun clearTv() = sp.edit().remove("tvtoken").apply()

    // ---- Hồ sơ game: mỗi hồ sơ có tên game riêng và vùng quét riêng (CaptureService đọc "game" theo hồ sơ hiện tại) ----
    var profiles: List<String>
        get() {
            val out = ArrayList<String>()
            try {
                val a = JSONArray(sp.getString("profiles", "[]"))
                for (i in 0 until a.length()) out.add(a.getString(i))
            } catch (_: Exception) {}
            if (!out.contains(DEFAULT_PROFILE)) out.add(0, DEFAULT_PROFILE)
            return out
        }
        set(v) = sp.edit().putString("profiles", JSONArray(v).toString()).apply()

    var profile: String
        get() = sp.getString("profile", DEFAULT_PROFILE).orEmpty().ifBlank { DEFAULT_PROFILE }
        set(v) {
            // đổi hồ sơ: lưu tên game của hồ sơ cũ, rồi nạp tên game của hồ sơ mới vào khóa "game" mà CaptureService đang đọc
            val old = profile
            val cur = sp.getString("game", "").orEmpty()
            val next = if (v == old) cur else (sp.getString("game_$v", null) ?: "")
            sp.edit().putString("game_$old", cur).putString("profile", v).putString("game", next).apply()
        }

    fun gameOf(p: String): String = sp.getString("game_$p", null) ?: if (p == profile) sp.getString("game", "").orEmpty() else ""
    var game: String
        get() = sp.getString("game", "").orEmpty()
        set(v) = sp.edit().putString("game", v.trim()).putString("game_$profile", v.trim()).apply()

    fun addProfile(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || profiles.contains(n)) return false
        profiles = profiles + n
        profile = n
        return true
    }

    fun deleteProfile(name: String) {
        if (name == DEFAULT_PROFILE) return
        profiles = profiles.filter { it != name }
        if (profile == name) profile = DEFAULT_PROFILE
        sp.edit().remove("game_$name").remove("gloss_$name").remove("cast_$name").apply()
        PackStore.deleteLines(app, name)
    }

    var fontSize: Int
        get() = sp.getInt("fs", 18)
        set(v) = sp.edit().putInt("fs", v).apply()
    var autoMenu: Boolean
        get() = sp.getBoolean("automenu", false)
        set(v) = sp.edit().putBoolean("automenu", v).apply()
    var slow: Boolean
        get() = sp.getBoolean("slow", false)
        set(v) = sp.edit().putBoolean("slow", v).apply()
    var speak: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()
    /** Tải ảnh bìa game từ Steam cho Kho game (chỉ gửi tên game). */
    var covers: Boolean
        get() = sp.getBoolean("covers", true)
        set(v) = sp.edit().putBoolean("covers", v).apply()
}
