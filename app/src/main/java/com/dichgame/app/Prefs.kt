package com.dichgame.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Toàn bộ cài đặt của app, lưu trong SharedPreferences "dg" (giữ nguyên các khóa cũ để CaptureService vẫn đọc được). */
class Prefs(ctx: Context) {
    private val app: Context = ctx.applicationContext
    val sp: SharedPreferences = app.getSharedPreferences("dg", Context.MODE_PRIVATE)

    init {
        if (!sp.contains("ai_auto_enabled")) {
            // Existing installations retain the previous opt-in default.
            sp.edit().putBoolean("ai_auto_enabled", sp.all.isEmpty()).apply()
        }
        // Migrate legacy plaintext credentials as soon as app preferences are opened,
        // not only when the player first enters the AI settings screen.
        ApiSecrets.read(app, "key")
        ApiSecrets.read(app, "keys2")
        ApiSecrets.read(app, "ckey")
    }

    companion object {
        const val ENGINE_PACK = "pack"
        const val ENGINE_LOCAL = "local"
        const val ENGINE_CLOUD = "cloud"
        const val ENGINE_GEMINI = "gemini"
        const val MODE_TRANSLATE = "translate"
        const val MODE_VIETSUB = "vietsub"
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
            "Kore" to "Mai · nữ · rõ ràng", "Puck" to "Minh · nam · vui tươi",
            "Charon" to "Hùng · nam · điềm tĩnh", "Zephyr" to "Linh · nữ · tươi sáng",
            "Sulafat" to "Thảo · nữ · ấm áp", "Aoede" to "Ngọc · nữ · nhẹ nhàng",
            "Achird" to "Đức · nam · thân thiện", "Gacrux" to "Hà · nữ · trưởng thành"
        )
        /** Tên Việt là nhãn trong app, mã giọng tiếng Anh giữ nguyên khi gọi API Gemini. */
        fun aiVoiceLabel(id: String): String = AI_VOICES.firstOrNull { it.first == id }?.second ?: id
        /** Giới tính của các preset Gemini theo danh sách chính thức của Google Cloud. */
        val MALE_AI_VOICES = AI_VOICES.filter { it.first in setOf("Puck", "Charon", "Achird") }
        val FEMALE_AI_VOICES = AI_VOICES.filter { it.first in setOf("Kore", "Zephyr", "Sulafat", "Aoede", "Gacrux") }
        fun langName(code: String) = LANGS.firstOrNull { it.first == code }?.second ?: "Tiếng Anh"
    }

    /** Chỉ có hai chế độ: Dịch và Thuyết minh. Giữ tương thích key narrate cũ. */
    var mode: String
        get() = if (sp.getBoolean("narrate", false) ||
            sp.getString("smart_mode", null) == MODE_VIETSUB) MODE_VIETSUB else MODE_TRANSLATE
        set(value) {
            val next = if (value == MODE_VIETSUB) MODE_VIETSUB else MODE_TRANSLATE
            sp.edit().putString("smart_mode", next).putBoolean("narrate", next == MODE_VIETSUB).apply()
        }

    /** Thuyết minh: chỉ đọc phụ đề tiếng Việt đã có, không dịch. */
    var narrate: Boolean
        get() = mode == MODE_VIETSUB
        set(value) { mode = if (value) MODE_VIETSUB else MODE_TRANSLATE }

    var aiAuto: Boolean
        get() = !narrate && sp.getBoolean("ai_auto_enabled", false)
        set(value) { sp.edit().putBoolean("ai_auto_enabled", value).apply() }

    var processingMode: ProcessingMode
        get() = ProcessingMode.fromSettings(narrate, aiAuto, speak)
        set(value) {
            val narrating = value == ProcessingMode.NARRATE_ONLY
            val editor = sp.edit().putString("smart_mode", if (narrating) MODE_VIETSUB else MODE_TRANSLATE)
                .putBoolean("narrate", narrating)
                .putBoolean("ai_auto_enabled", value == ProcessingMode.AUTOMATIC)
            when (value) {
                ProcessingMode.TRANSLATE_ONLY -> editor.putBoolean("speak", false).putBoolean("listen_only", false)
                ProcessingMode.TRANSLATE_AND_SPEAK -> editor.putBoolean("speak", true)
                else -> Unit
            }
            editor.apply()
        }

    /** Ngôn ngữ trong game: en, ja, ko, zh. Khi thuyết minh thì nguồn luôn là tiếng Việt. */
    var lang: String
        get() = sp.getString("langc", null) ?: arrayOf("en", "ja", "ko", "zh")[sp.getInt("lang", 0).coerceIn(0, 3)]
        set(v) = sp.edit().putString("langc", v).apply()

    var engine: String
        get() = sp.getString("engine", null) ?: if (geminiKeys().isEmpty()) ENGINE_LOCAL else ENGINE_GEMINI
        set(v) = sp.edit().putString("engine", v).apply()

    val packOnly: Boolean get() = !aiAuto && engine == ENGINE_PACK

    var geminiKey: String
        get() = ApiSecrets.read(app, "key")
        set(v) { ApiSecrets.write(app, mapOf("key" to v.trim())) }
    /** Khóa dự phòng (mỗi dòng một khóa). Khóa chính ở [geminiKey]. */
    var geminiSpare: String
        get() = ApiSecrets.read(app, "keys2")
        set(v) { ApiSecrets.write(app, mapOf("keys2" to v)) }

    /** All Gemini keys in display order, including keys manually disabled by the player. */
    fun geminiStoredKeys(): List<String> =
        (listOf(geminiKey) + geminiSpare.lines()).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun geminiKeyId(key: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun isGeminiKeyEnabled(key: String): Boolean =
        geminiKeyId(key) !in (sp.getStringSet("gemini_disabled_keys", emptySet()) ?: emptySet())

    /** Persist only one-way key fingerprints; never store another plaintext copy of credentials. */
    fun setGeminiKeyEnabled(key: String, enabled: Boolean): Boolean {
        val disabled = (sp.getStringSet("gemini_disabled_keys", emptySet()) ?: emptySet()).toMutableSet()
        val id = geminiKeyId(key)
        if (enabled) disabled.remove(id) else disabled.add(id)
        return sp.edit().putStringSet("gemini_disabled_keys", disabled).commit()
    }

    /** Only enabled keys are eligible for translation and AI voice requests. */
    fun geminiKeys(): List<String> = geminiStoredKeys().filter { isGeminiKeyEnabled(it) }

    /** Save keys atomically; newly added keys become usable and removed keys lose their disabled marker. */
    fun setGeminiKeys(list: List<String>): Boolean {
        val before = geminiStoredKeys().toSet()
        val k = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (!ApiSecrets.write(app, mapOf("key" to k.firstOrNull().orEmpty(), "keys2" to k.drop(1).joinToString("\n")))) return false
        val keepIds = k.map { geminiKeyId(it) }.toSet()
        val disabled = (sp.getStringSet("gemini_disabled_keys", emptySet()) ?: emptySet()).filter { it in keepIds }.toSet()
        if (!sp.edit().putStringSet("gemini_disabled_keys", disabled).commit()) {
            android.util.Log.w("DG_PREFS", "Could not update disabled-key metadata.")
        }
        for (n in k) if (n !in before) KeyRing.forget(n)
        return true
    }

    var geminiModel: String
        get() = sp.getString("gmodel", "gemini-3.5-flash-lite").orEmpty().ifBlank { "gemini-3.5-flash-lite" }
        set(v) = sp.edit().putString("gmodel", v.trim()).apply()

    /** CloudAPI: dịch vụ AI đám mây theo chuẩn OpenAI (/chat/completions), cần địa chỉ, khóa và tên model. */
    var packCatalogUrl: String
        get() = sp.getString("pack_catalog_url", "").orEmpty()
        set(v) = sp.edit().putString("pack_catalog_url", v.trim()).apply()

    /** Console đang chơi; chỉ hỗ trợ PS5, PS4 và Xbox. Không dùng trường này để lọc nguồn dữ liệu Việt hóa. */
    var selectedPlatform: String
        get() = sp.getString("selected_platform_$profile", "PS5").orEmpty()
            .takeIf { it in setOf("PS5", "PS4", "XBOX") } ?: "PS5"
        set(v) {
            val safe = v.uppercase(java.util.Locale.ROOT)
            if (safe in setOf("PS5", "PS4", "XBOX"))
                sp.edit().putString("selected_platform_$profile", safe).apply()
        }

    var autoPackRefresh: Boolean
        get() = sp.getBoolean("auto_pack_refresh", false)
        set(v) = sp.edit().putBoolean("auto_pack_refresh", v).apply()

    var autoCloudFallback: Boolean
        get() = sp.getBoolean("auto_cloud_fallback", false)
        set(v) = sp.edit().putBoolean("auto_cloud_fallback", v).apply()

    var cloudUrl: String
        get() = sp.getString("curl", "").orEmpty()
        set(v) = sp.edit().putString("curl", v.trim()).apply()
    var cloudKey: String
        get() = ApiSecrets.read(app, "ckey")
        set(v) {
            if (ApiSecrets.write(app, mapOf("ckey" to v.trim()))) KeyRing.clearBilling()
        }
    var cloudModel: String
        get() = sp.getString("cmodel", "gemini-3.1-flash-lite").orEmpty()
        set(v) = sp.edit().putString("cmodel", v.trim()).apply()

    /** Số câu trước đưa cho AI để hiểu mạch truyện. */
    /** Từ điển riêng của hồ sơ hiện tại, mỗi dòng "gốc = tiếng Việt". Dùng cho cả dịch AI lẫn dịch trên máy. */
    private fun gameSettingsPrefix(profile: String): String =
        "game_v2_" + GamePackMatch.storageHash(profile) + "_"

    private fun gameSettingKey(key: String): String {
        val currentProfile = profile
        val game = GamePackMatch.title(gameOf(currentProfile), currentProfile,
            selectedGameTitleOf(currentProfile), DEFAULT_PROFILE)
        val scoped = GamePackMatch.storageGame(currentProfile, game)
        return if (scoped.isEmpty()) "${key}_$currentProfile"
        else gameSettingsPrefix(currentProfile) + GamePackMatch.storageHash(scoped) + "_" + key
    }

    var glossary: String
        get() = sp.getString(gameSettingKey("gloss"), "").orEmpty()
        set(v) = sp.edit().putString(gameSettingKey("gloss"), v).apply()

    var lookaheadCount: Int
        get() = sp.getInt("lookahead_count", 4).coerceIn(0, 10)
        set(value) = sp.edit().putInt("lookahead_count", value.coerceIn(0, 10)).apply()

    var ctxCount: Int
        get() = sp.getInt("ctxn", 3).coerceIn(0, 10)
        set(v) = sp.edit().putInt("ctxn", v.coerceIn(0, 10)).apply()

    /** Phong cách dịch AI (chỉ số trong TR_STYLES). */
    var trStyle: Int
        get() = sp.getInt("trstyle", 0).coerceIn(0, TR_STYLES.size - 1)
        set(v) = sp.edit().putInt("trstyle", v).apply()

    /** Chỉ dẫn dịch đang dùng: phong cách đã chọn, hoặc prompt riêng. */
    fun styleText(): String =
        if (trStyle == CUSTOM_STYLE) customPrompt.ifBlank { TR_STYLES[0].second } else TR_STYLES[trStyle].second

    /** Prompt riêng do người dùng viết, dùng khi chọn phong cách "Prompt riêng". */
    internal var promptTemplates: PromptTemplates
        get() = PromptTemplates.decode(sp.getString(gameSettingKey("prompt_templates"), null),
            sp.getString("customprompt", "").orEmpty())
        set(value) = sp.edit().putString(gameSettingKey("prompt_templates"), value.encode()).apply()

    val customPrompt: String
        get() = promptTemplates.let { it.entries[it.selected].orEmpty() }

    /** Dùng gói Việt hoá có sẵn của game (thuật ngữ, thẻ nhân vật, câu thoại chuẩn). */
    /** Gói thuật ngữ bật/tắt theo từng hồ sơ game; cài đặt cũ làm mặc định tương thích. */
    var packOn: Boolean
        get() = sp.getBoolean("packon_$profile", sp.getBoolean("packon", true))
        set(v) = sp.edit().putBoolean("packon_$profile", v).apply()

    /** Tự lưu và tái sử dụng bản dịch AI đã qua kiểm tra, riêng trên thiết bị. */
    var learnTranslations: Boolean
        get() = sp.getBoolean("learn_translations", true)
        set(v) = sp.edit().putBoolean("learn_translations", v).apply()

    /** Thẻ nhân vật của hồ sơ hiện tại, mỗi dòng "Tên = mô tả giọng, xưng hô; nhịp: chậm". */
    var cast: String
        get() = sp.getString(gameSettingKey("cast"), "").orEmpty()
        set(v) = sp.edit().putString(gameSettingKey("cast"), v).apply()

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
        get() = sp.getInt("scanms", 333).coerceIn(100, 3000)
        set(v) = sp.edit().putInt("scanms", v.coerceIn(100, 3000)).apply()

    /** Hai lựa chọn giọng: "system" (cơ bản) và "ai" (Gemini); tự đọc cơ bản nếu AI lỗi. */
    var voiceEngine: String
        get() = sp.getString("voiceengine", "system").orEmpty().takeIf { it in setOf("ai", "offline") } ?: "system"
        set(v) = sp.edit().putString("voiceengine", if (v in setOf("ai", "offline")) v else "system").apply()
    var aiVoice: String
        get() = sp.getString("aivoice", "Kore").orEmpty().ifBlank { "Kore" }
        set(v) = sp.edit().putString("aivoice", v).apply()
    /** Thuyết minh theo vai nhân vật khi thẻ nhân vật có ghi giọng nam hoặc giọng nữ. */
    var characterVoices: Boolean
        get() = sp.getBoolean("charvoices", true)
        set(v) = sp.edit().putBoolean("charvoices", v).apply()
    var maleAiVoice: String
        get() = sp.getString("male_aivoice", "Charon").orEmpty()
            .takeIf { v -> MALE_AI_VOICES.any { it.first == v } } ?: "Charon"
        set(v) = sp.edit().putString("male_aivoice", v).apply()
    var femaleAiVoice: String
        get() = sp.getString("female_aivoice", "Kore").orEmpty()
            .takeIf { v -> FEMALE_AI_VOICES.any { it.first == v } } ?: "Kore"
        set(v) = sp.edit().putString("female_aivoice", v).apply()

    /** Nhân vật phát hiện từ nhãn lời thoại, lưu theo hồ sơ game; không tự đoán giới tính qua tên. */
    fun detectedSpeakers(): List<String> {
        val result = ArrayList<String>()
        val raw = sp.getString(gameSettingKey("detected_speakers"), "[]").orEmpty()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length().coerceAtMost(250)) {
                val name = a.optString(i).trim()
                if (name.isNotEmpty() && result.none { it.equals(name, ignoreCase = true) }) result.add(name)
            }
        } catch (_: Exception) {}
        return result
    }

    private fun ignoredSpeakers(): List<String> {
        val raw = sp.getString(gameSettingKey("ignored_speakers"), "[]").orEmpty()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length().coerceAtMost(250)).mapNotNull { i ->
                arr.optString(i).trim().takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun isIgnoredSpeaker(name: String?): Boolean =
        !name.isNullOrBlank() && ignoredSpeakers().any { it.equals(name, ignoreCase = true) }

    /** Chỉ lưu người nói được OCR xác nhận; tên đã xóa không tự xuất hiện lại nếu người dùng chưa thêm. */
    @Synchronized fun rememberSpeaker(raw: String, fromUser: Boolean = false) {
        val name = raw.replace(Regex("\\s+"), " ").trim()
        if (name.length !in 2..40 || name.count { it.isLetter() } < 2 || '=' in name) return
        val ignored = ignoredSpeakers()
        if (!fromUser && ignored.any { it.equals(name, ignoreCase = true) }) return
        val editor = sp.edit()
        if (fromUser) editor.putString(gameSettingKey("ignored_speakers"),
            JSONArray(ignored.filterNot { it.equals(name, ignoreCase = true) }).toString())
        val names = detectedSpeakers()
        if (names.none { it.equals(name, ignoreCase = true) })
            editor.putString(gameSettingKey("detected_speakers"), JSONArray((names + name).takeLast(250)).toString())
        editor.apply()
    }

    /** Xóa một nhãn nhân vật OCR nhận nhầm khỏi hồ sơ, kèm giọng đã gán cho tên đó. */
    fun forgetDetectedSpeaker(raw: String) = synchronized(sp) {
        val name = raw.trim()
        if (name.isEmpty()) return@synchronized
        val key = name.lowercase(java.util.Locale.ROOT)
        val rest = detectedSpeakers().filterNot { it.equals(name, ignoreCase = true) }
        val ignored = (ignoredSpeakers().filterNot { it.equals(name, ignoreCase = true) } + name).takeLast(250)
        fun withoutEntry(prefKey: String): String {
            val data = try { JSONObject(sp.getString(prefKey, "{}").orEmpty()) }
                catch (_: Exception) { JSONObject() }
            data.remove(key)
            return data.toString()
        }
        val editor = sp.edit()
            .putString(gameSettingKey("detected_speakers"), JSONArray(rest).toString())
            .putString(gameSettingKey("manual_voices"), withoutEntry(gameSettingKey("manual_voices")))
            .putString(gameSettingKey("auto_voices"), withoutEntry(gameSettingKey("auto_voices")))
            .putString(gameSettingKey("ignored_speakers"), JSONArray(ignored).toString())
        if (activeSpeaker.equals(name, ignoreCase = true)) editor.putString(gameSettingKey("active_speaker"), "")
        editor.apply()
    }

    /** Chọn giọng riêng bằng giao diện; null = quay về chế độ tự động. */
    fun manualVoice(name: String): String? {
        val raw = sp.getString(gameSettingKey("manual_voices"), "{}").orEmpty()
        return try {
            JSONObject(raw).optString(name.trim().lowercase(java.util.Locale.ROOT))
                .takeIf { id -> AI_VOICES.any { it.first == id } }
        } catch (_: Exception) { null }
    }

    private fun canonicalColorName(name: String): String =
        Cast.byName(name, PackStore.cast(this))?.names?.firstOrNull() ?: name

    private fun colorSetting(name: String) =
        gameSettingKey("speaker_color_" + GamePackMatch.storageHash(SpeakerBook.key(name)))

    fun speakerColor(name: String): Int {
        val canonical = canonicalColorName(name)
        return SpeakerColor.of(canonical, sp.getInt(colorSetting(canonical), 0))
    }

    fun setSpeakerColor(name: String, color: Int?) {
        val key = colorSetting(canonicalColorName(name))
        if (color == null) sp.edit().remove(key).apply()
        else sp.edit().putInt(key, color).apply()
    }

    /** Giọng thủ công hiệu lực cho tên gọi chính hoặc bất kỳ bí danh nào của cùng nhân vật. */
    fun effectiveManualVoice(name: String): String? {
        manualVoice(name)?.let { return it }
        val card = Cast.byName(name, PackStore.cast(this)) ?: return null
        return card.names.firstNotNullOfOrNull { alias -> manualVoice(alias) }
    }

    @Synchronized fun setManualVoice(name: String, id: String?) {
        if (name.isBlank() || id != null && AI_VOICES.none { it.first == id }) return
        val raw = sp.getString(gameSettingKey("manual_voices"), "{}").orEmpty()
        val data = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        val key = name.trim().lowercase(java.util.Locale.ROOT)
        if (id == null) data.remove(key) else data.put(key, id)
        sp.edit().putString(gameSettingKey("manual_voices"), data.toString()).apply()
        rememberSpeaker(name, fromUser = true)
    }

    /**
     * Đóng băng giọng tự động theo hồ sơ game. Danh sách nhân vật hiển thị chỉ lưu 100 tên gần nhất,
     * vì vậy không được tính lại giọng theo vị trí trong danh sách này sau khi đã gán.
     * Dữ liệu giọng được tách theo game; không sao chép ánh xạ cũ của hồ sơ chung sang game khác.
     */
    private fun stableAutoVoice(name: String, aliases: List<String>, preferred: List<Pair<String, String>> = AI_VOICES): String = synchronized(sp) {
        val key = gameSettingKey("auto_voices")
        val counterKey = gameSettingKey("auto_voice_count")
        val old = sp.getString(key, null)
        val mapping = try { JSONObject(old ?: "{}") } catch (_: Exception) { JSONObject() }
        val known = detectedSpeakers()
        val normalize: (String) -> String = { it.trim().lowercase(java.util.Locale.ROOT) }
        if (old == null) {
            for ((i, candidate) in known.withIndex()) {
                val k = normalize(candidate)
                if (k.isNotEmpty() && !mapping.has(k))
                    mapping.put(k, AI_VOICES[i % AI_VOICES.size].first)
            }
            sp.edit().putString(key, mapping.toString()).putInt(counterKey, known.size).apply()
        }
        val names = (listOf(name) + aliases).map(normalize).filter { it.isNotEmpty() }
        val stored = names.firstNotNullOfOrNull { alias ->
            mapping.optString(alias).takeIf { id -> AI_VOICES.any { it.first == id } }
        }
        if (stored != null && preferred.any { it.first == stored }) return@synchronized stored
        val count = sp.getInt(counterKey, mapping.length()).coerceAtLeast(0)
        val used = mapping.keys().asSequence().map { mapping.optString(it) }.toSet()
        val available = preferred.filterNot { it.first in used }.ifEmpty { preferred }
        val selected = available[count % available.size].first
        mapping.put(names.first(), selected)
        sp.edit().putString(key, mapping.toString()).putInt(counterKey, count + 1).apply()
        selected
    }

    /** Nhân vật được người chơi chọn khi game không hiển thị nhãn tên người nói. */
    var activeSpeaker: String
        get() = sp.getString(gameSettingKey("active_speaker"), "").orEmpty()
        set(v) = sp.edit().putString(gameSettingKey("active_speaker"), v.trim()).apply()

    /** Mỗi người nói đã nhận diện có giọng mặc định ổn định; người chơi có thể ghi đè thủ công. */
    fun aiVoiceFor(speaker: String?, sentence: String): String =
        chooseAiVoice(speaker, sentence, ignoreManual = false)

    /** Giọng mà chế độ tự động sẽ chọn, để giao diện hiển thị đúng khi bỏ gán thủ công. */
    fun automaticAiVoiceFor(speaker: String?, sentence: String): String =
        chooseAiVoice(speaker, sentence, ignoreManual = true)

    private fun chooseAiVoice(speaker: String?, sentence: String, ignoreManual: Boolean): String {
        if (!characterVoices) return aiVoice
        val cards = PackStore.cast(this)
        val card = Cast.byName(speaker, cards) ?: Cast.speakerOf(sentence, cards)
        val name = speaker?.takeIf { it.isNotBlank() && !isIgnoredSpeaker(it) }
            ?: card?.names?.firstOrNull()?.takeIf { !isIgnoredSpeaker(it) }
            ?: activeSpeaker.takeIf { it.isNotBlank() } ?: return aiVoice
        if (!ignoreManual) effectiveManualVoice(name)?.let { return it }
        val desc = card?.desc.orEmpty().lowercase(java.util.Locale.ROOT)
        val requested = Regex("giọng\\s*[:=]\\s*([^;,]+)", RegexOption.IGNORE_CASE)
            .find(desc)?.groupValues?.getOrNull(1)?.trim()
        AI_VOICES.firstOrNull {
            it.first.equals(requested, ignoreCase = true) ||
                it.second.substringBefore(" · ").equals(requested, ignoreCase = true)
        }?.let { return it.first }
        val female = Regex("(?:^|[\\s,;])(?:giọng\\s*)?(?:nữ|female)(?=$|[\\s,;])").containsMatchIn(desc) ||
            listOf("cô gái", "cô bé", "phụ nữ", "nữ thần", "quý bà", "con gái", "người mẹ").any { it in desc }
        val male = Regex("(?:^|[\\s,;])(?:giọng\\s*)?(?:nam|male)(?=$|[\\s,;])").containsMatchIn(desc) ||
            listOf("đàn ông", "cậu bé", "anh chàng", "người cha", "con trai", "quý ông").any { it in desc }
        val pool = when {
            female && !male -> FEMALE_AI_VOICES.sortedBy { if (it.first == femaleAiVoice) 0 else 1 }
            male && !female -> MALE_AI_VOICES.sortedBy { if (it.first == maleAiVoice) 0 else 1 }
            else -> return aiVoice
        }
        val canonical = card?.names?.firstOrNull() ?: name
        return stableAutoVoice(canonical, card?.names.orEmpty() + name, pool)
    }
    var ttsModel: String
        get() = sp.getString("ttsmodel", "gemini-3.8-flash-lite-tts").orEmpty().ifBlank { "gemini-3.8-flash-lite-tts" }
        set(v) = sp.edit().putString("ttsmodel", v.trim()).apply()
    val aiVoiceOn: Boolean get() = voiceEngine == "ai" && geminiKeys().isNotEmpty()
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
    var lastSubtitleSeconds: Int
        get() = sp.getInt("last_subtitle_seconds", 0).takeIf { it in 2..8 } ?: 0
        set(value) = sp.edit().putInt("last_subtitle_seconds", value.takeIf { it in 2..8 } ?: 0).apply()

    var showPrev: Boolean
        get() = sp.getBoolean("showprev", true)
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

    // ---- TV LG (webOS): ghép qua LG Connect Apps, gửi phụ đề bằng thông báo trên TV (thử nghiệm) ----
    val lgIp: String get() = sp.getString("lgip", "").orEmpty()
    val lgKey: String get() = sp.getString("lgkey", "").orEmpty()
    val lgPaired: Boolean get() = lgIp.isNotEmpty() && lgKey.isNotEmpty()
    fun saveLg(ip: String, key: String) = sp.edit().putString("lgip", ip).putString("lgkey", key).apply()
    fun clearLg() = sp.edit().remove("lgkey").apply()
    var lgSubs: Boolean
        get() = sp.getBoolean("lgsubs", true)
        set(v) = sp.edit().putBoolean("lgsubs", v).apply()

    /** Bật trang web phụ đề phát từ điện thoại (xem bằng trình duyệt TV/iPhone/máy tính bảng). */
    var webSub: Boolean
        get() = sp.getBoolean("websub", false)
        set(v) = sp.edit().putBoolean("websub", v).apply()

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
            if (old != v) RecentLines.clear()
            val cur = sp.getString("game", "").orEmpty()
            val next = if (v == old) cur else (sp.getString("game_$v", null) ?: "")
            sp.edit().putString("game_$old", cur).putString("profile", v).putString("game", next).apply()
        }

    fun gameOf(p: String): String = sp.getString("game_$p", null) ?: if (p == profile) sp.getString("game", "").orEmpty() else ""

    /** Tên nguyên bản từ Kho game (có thể chứa dấu phẩy); không bị cắt khi thêm thể loại. */
    fun selectedGameTitleOf(profile: String): String = sp.getString("game_title_$profile", "").orEmpty()

    var selectedGameTitle: String
        get() = selectedGameTitleOf(profile)
        set(v) {
            if (selectedGameTitle != v.trim()) RecentLines.clear()
            sp.edit().putString("game_title_$profile", v.trim()).apply()
        }

    var game: String
        get() = sp.getString("game", "").orEmpty()
        set(v) {
            if (game != v.trim() || selectedGameTitle.isNotEmpty()) RecentLines.clear()
            sp.edit().putString("game", v.trim()).putString("game_$profile", v.trim())
                .remove("game_title_$profile").apply()
        }

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
        sp.edit().remove("game_$name").remove("gloss_$name").remove("cast_$name")
            .remove("detected_speakers_$name").remove("manual_voices_$name")
            .remove("active_speaker_$name").remove("auto_voices_$name")
            .remove("auto_voice_count_$name").remove("ignored_speakers_$name")
            .remove("packon_$name").remove("game_title_$name").apply()
        val scopedEditor = sp.edit()
        sp.all.keys.filter { it.startsWith(gameSettingsPrefix(name)) }.forEach { scopedEditor.remove(it) }
        scopedEditor.apply()
        PackStore.deleteProfileLines(app, name)
        LearnedTranslations.clearProfile(app, name)
    }

    var fontSize: Int
        get() = sp.getInt("fs", 18)
        set(v) = sp.edit().putInt("fs", v).apply()
    var autoMenu: Boolean
        get() = sp.getBoolean("automenu", false)
        set(v) = sp.edit().putBoolean("automenu", v).apply()
    var ocrQuality: OcrQuality
        get() = OcrQuality.read(sp.getString("ocr_quality", null), sp.getBoolean("slow", true))
        set(value) = sp.edit().putString("ocr_quality", value.name).putBoolean("slow", value != OcrQuality.FAST).apply()
    val slow: Boolean get() = ocrQuality != OcrQuality.FAST
    var listenOnly: Boolean
        get() = sp.getBoolean("listen_only", false)
        set(v) = sp.edit().putBoolean("listen_only", v).apply()
    var controllerTranslate: Boolean
        get() = sp.getBoolean("controller_translate", true)
        set(v) = sp.edit().putBoolean("controller_translate", v).apply()
    var speak: Boolean
        get() = listenOnly || sp.getBoolean("speak", true)
        set(v) {
            val e = sp.edit().putBoolean("speak", v)
            if (!v) e.putBoolean("listen_only", false)
            e.apply()
        }
    /** Tải ảnh bìa game từ Steam và Wikipedia cho Kho game (chỉ gửi tên game). */
    var covers: Boolean
        get() = sp.getBoolean("covers", true)
        set(v) = sp.edit().putBoolean("covers", v).apply()

    /** Bỏ qua lần đọc chữ khi hình trong khung phụ đề y hệt lần trước: đỡ tốn pin, đỡ nóng máy mà không bỏ sót câu mới. */
    var skipStatic: Boolean
        get() = sp.getBoolean("skipstatic", true)
        set(v) = sp.edit().putBoolean("skipstatic", v).apply()
    /** Tự chuyển ngôn ngữ nguồn khi chữ trên màn hình đổi sang một ngôn ngữ Latin khác (Anh sang Pháp...). */
    var autoLang: Boolean
        get() = sp.getBoolean("autolang", true)
        set(v) = sp.edit().putBoolean("autolang", v).apply()
    /** Chế độ hiệu năng: 0 tiết kiệm pin (quét thưa hơn), 1 cân bằng, 2 chất lượng cao (quét dày hơn). */
    var perf: Int
        get() = sp.getInt("perf", 1).coerceIn(0, 2)
        set(v) = sp.edit().putInt("perf", v.coerceIn(0, 2)).apply()

    /** Nghe âm thanh của game đang chạy trên điện thoại (cần quyền ghi âm và mô hình Whisper đã tải) để bổ sung cho chữ trên màn hình. */
    var phoneAudio: Boolean
        get() = sp.getBoolean("phoneaudio", false)
        set(v) = sp.edit().putBoolean("phoneaudio", v).apply()
    /** Game chặn thu âm thanh trong thì nghe qua micro (chất lượng kém hơn, có thể thu cả giọng người chơi). */
    var phoneAudioMic: Boolean
        get() = sp.getBoolean("phoneaudiomic", true)
        set(v) = sp.edit().putBoolean("phoneaudiomic", v).apply()
}
