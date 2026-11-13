package com.dichgame.app

import java.util.Locale

/** Cảm xúc của một câu thoại. Mỗi cảm xúc có hệ số đổi tốc độ và cao độ giọng đọc, và màu nhãn trên phụ đề. */
enum class Mood(val key: String, val label: String, val rate: Float, val pitch: Float, val style: String, val color: Int) {
    NORMAL("bình thường", "bình thường", 1f, 1f, "", 0xFFB0BEC5.toInt()),
    HAPPY("vui", "vui", 1.05f, 1.15f, "cheerful", 0xFFFFD54F.toInt()),
    ANGRY("giận", "giận dữ", 1.12f, 1.1f, "angry and intense", 0xFFFF7043.toInt()),
    SCARED("sợ", "sợ hãi", 1.18f, 1.2f, "scared and trembling", 0xFFB39DDB.toInt()),
    SAD("buồn", "buồn", 0.86f, 0.88f, "sad and soft", 0xFF64B5F6.toInt()),
    WHISPER("thì thầm", "thì thầm", 0.92f, 0.95f, "whispering", 0xFF90A4AE.toInt()),
    SHOUT("hét", "hét lên", 1.1f, 1.12f, "shouting", 0xFFEF5350.toInt()),
    SARCASM("mỉa mai", "mỉa mai", 0.95f, 1f, "sarcastic and dry", 0xFF81C784.toInt()),
    TENSE("căng thẳng", "căng thẳng", 1.1f, 1.05f, "tense and urgent", 0xFFFFB74D.toInt());

    companion object {
        /** Tên cảm xúc AI hay dùng (có dấu hoặc gần nghĩa) -> cảm xúc của app. */
        private val ALIAS: Map<String, Mood> = mapOf(
            "bình thường" to NORMAL, "trung tính" to NORMAL, "bình tĩnh" to NORMAL, "điềm tĩnh" to NORMAL,
            "vui" to HAPPY, "vui vẻ" to HAPPY, "hào hứng" to HAPPY, "phấn khích" to HAPPY,
            "giận" to ANGRY, "giận dữ" to ANGRY, "tức giận" to ANGRY, "tức" to ANGRY, "cáu" to ANGRY,
            "sợ" to SCARED, "sợ hãi" to SCARED, "hoảng" to SCARED, "hoảng sợ" to SCARED,
            "buồn" to SAD, "buồn bã" to SAD, "đau buồn" to SAD, "tiếc nuối" to SAD,
            "thì thầm" to WHISPER, "thầm thì" to WHISPER, "nói nhỏ" to WHISPER,
            "hét" to SHOUT, "hét lên" to SHOUT, "la hét" to SHOUT, "quát" to SHOUT,
            "mỉa mai" to SARCASM, "châm biếm" to SARCASM, "giễu cợt" to SARCASM,
            "căng thẳng" to TENSE, "gấp gáp" to TENSE, "hồi hộp" to TENSE, "khẩn cấp" to TENSE,
        )
        fun of(s: String): Mood? = ALIAS[s.trim().lowercase(Locale.ROOT)]
        /** Danh sách tên đưa cho AI chọn. */
        val NAMES: String get() = values().joinToString(", ") { it.key }
    }
}

/** Nhịp nói: chậm rãi, vừa, hoặc nhanh dồn dập. */
enum class Pace(val key: String, val rate: Float) {
    SLOW("chậm", 0.9f), MID("vừa", 1f), FAST("nhanh", 1.12f);

    companion object {
        private val ALIAS: Map<String, Pace> = mapOf(
            "chậm" to SLOW, "chậm rãi" to SLOW, "từ tốn" to SLOW, "trầm" to SLOW,
            "vừa" to MID, "bình thường" to MID, "đều" to MID,
            "nhanh" to FAST, "dồn dập" to FAST, "gấp" to FAST, "liến thoắng" to FAST,
        )
        fun of(s: String): Pace? = ALIAS[s.trim().lowercase(Locale.ROOT)]
    }
}

/** Cách đọc một câu: cảm xúc cộng nhịp. Dùng để đổi tốc độ, cao độ và màu nhãn. */
class Delivery(val mood: Mood, val pace: Pace) {
    val rate: Float get() = (mood.rate * pace.rate).coerceIn(0.7f, 1.4f)
    val pitch: Float get() = mood.pitch
    val style: String get() = mood.style
    /** Nhãn ngắn hiện trên phụ đề, rỗng nếu chẳng có gì đặc biệt. */
    val label: String get() = when {
        mood == Mood.NORMAL && pace == Pace.MID -> ""
        mood == Mood.NORMAL -> pace.key
        pace == Pace.MID -> mood.label
        else -> mood.label + " · " + pace.key
    }
    override fun toString() = "${mood.key}|${pace.key}"
}

/** Nhận ra cảm xúc và nhịp của câu thoại, và phần chỉ dẫn đưa cho AI. */
object Emo {
    class Parsed(val text: String, val delivery: Delivery?)

    private val TAG = Regex("^\\s*\\[\\s*([^\\]\\[|\\n]{1,16}?)\\s*(?:\\|\\s*([^\\]\\[|\\n]{1,12}?)\\s*)?]\\s*")

    /** Bóc thẻ [cảm xúc|nhịp] ở đầu câu AI trả về. Thẻ không hiểu được thì giữ nguyên, để khỏi nuốt chữ thật. */
    fun parse(raw: String): Parsed {
        val m = TAG.find(raw) ?: return Parsed(raw.trim(), null)
        val mood = Mood.of(m.groupValues[1])
        val pace = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { Pace.of(it) }
        val onlyPace = mood == null && pace == null && Pace.of(m.groupValues[1]) != null
        if (mood == null && pace == null && !onlyPace) return Parsed(raw.trim(), null)
        val p2 = pace ?: Pace.of(m.groupValues[1])
        val rest = raw.substring(m.range.last + 1).trim()
        if (rest.isEmpty()) return Parsed(raw.trim(), null)
        return Parsed(rest, Delivery(mood ?: Mood.NORMAL, p2 ?: Pace.MID))
    }

    private fun has(s: String, vararg w: String) = w.any { s.contains(it) }

    private val CAP = Regex("\\p{Lu}{2,}")

    /** Đoán bằng luật đơn giản (không cần AI) từ câu gốc [src] và bản dịch [vi]. [castPace] là nhịp ghi trong thẻ nhân vật nếu có. */
    fun detect(src: String, vi: String, castPace: Pace? = null): Delivery {
        val v = vi.lowercase(Locale.ROOT)
        val s = src.lowercase(Locale.ROOT)
        val bang = (vi.count { it == '!' } + src.count { it == '!' || it == '！' }).coerceAtLeast(0)
        val letters = src.filter { it.isLetter() }
        val words = src.split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
        val shouting = words.size >= 2 && letters.length >= 6 && letters.count { it.isUpperCase() } * 10 >= letters.length * 8 && bang >= 1 && CAP.containsMatchIn(src)
        val mood = when {
            shouting -> Mood.SHOUT
            has(v, "khốn", "chết đi", "giết", "câm miệng", "biến đi", "điên rồi", "đồ ngu", "đồ khốn") ||
                has(s, "damn", "bastard", "shut up", "get out", "i'll kill", "you fool") || bang >= 2 -> Mood.ANGRY
            has(v, "cứu", "chạy đi", "trời ơi", "đừng lại gần", "đừng mà", "làm ơn đừng") || has(s, "help me", "run!", "don't hurt", "please don't", "no, no") -> Mood.SCARED
            has(v, "xin lỗi", "buồn", "khóc", "mất rồi", "nhớ", "tiếc", "chia tay", "cô đơn", "đã ra đi") || has(s, "sorry", "miss you", "she's gone", "he's gone", "alone") -> Mood.SAD
            has(v, "haha", "tuyệt", "cảm ơn", "yêu", "hay quá", "thành công", "chúc mừng") || has(s, "thank", "haha", "love", "congrat", "great") -> Mood.HAPPY
            has(v, "nhanh lên", "không còn thời gian", "coi chừng", "cẩn thận", "sắp rồi", "ở đó!") || has(s, "hurry", "look out", "behind you", "watch out", "quick") -> Mood.TENSE
            src.trimEnd().endsWith("...") || src.trimEnd().endsWith("…") -> if (v.length < 24) Mood.WHISPER else Mood.NORMAL
            else -> Mood.NORMAL
        }
        val sentences = vi.split(Regex("[.!?…]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val avg = if (sentences.isEmpty()) 0 else sentences.sumOf { it.split(Regex("\\s+")).size } / sentences.size
        val pace = castPace ?: when {
            mood == Mood.SAD || mood == Mood.WHISPER -> Pace.SLOW
            mood == Mood.ANGRY || mood == Mood.SHOUT || mood == Mood.SCARED || mood == Mood.TENSE -> Pace.FAST
            sentences.size >= 2 && avg in 1..4 -> Pace.FAST
            (src.count { it == '…' } + Regex("\\.\\.\\.").findAll(src).count()) >= 2 -> Pace.SLOW
            else -> Pace.MID
        }
        return Delivery(mood, pace)
    }

    /** Phần chỉ dẫn thêm vào câu lệnh dịch: cảm xúc, tiết tấu và thẻ ở đầu câu trả lời. */
    fun instruction(): String =
        " Dịch theo cảm xúc và tiết tấu của nhân vật: tự nhận ra ai đang nói và họ đang có cảm xúc gì " +
            "(dựa vào dấu câu, chữ in hoa, nhãn tên, mạch truyện), rồi chọn từ ngữ, từ đệm và xưng hô đúng sắc thái đó. " +
            "Giữ nhịp ngắt của câu gốc: nóng nảy, hoảng sợ thì câu ngắn dồn dập; buồn, trầm, hồi tưởng thì câu dài, chậm, có chỗ ngắt; " +
            "mỉa mai thì giữ giọng khô, không nói quá. Không thêm ý, không bỏ ý. " +
            "Bắt đầu câu trả lời bằng đúng một thẻ dạng [cảm xúc|nhịp], cảm xúc chọn trong: ${Mood.NAMES}; nhịp chọn trong: chậm, vừa, nhanh; " +
            "ví dụ [giận|nhanh] rồi tới bản dịch."
}

/** Một nhân vật trong thẻ nhân vật: tên, mô tả giọng nói và xưng hô, nhịp nói (nếu có). */
class Character(val name: String, val desc: String, val pace: Pace?) {
    /** Tên gọi khác, tách bằng dấu "/". */
    val names: List<String> = name.split('/').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Thẻ nhân vật của một game, mỗi dòng "Tên = mô tả; nhịp: chậm". Dùng để AI giữ đúng giọng và xưng hô từng người,
 * và để chọn nhịp đọc khi đoán được ai đang nói.
 */
object Cast {
    private val PACE = Regex("nhịp\\s*[:=]?\\s*(chậm rãi|chậm|vừa|nhanh|dồn dập)", RegexOption.IGNORE_CASE)

    fun paceOf(desc: String): Pace? = PACE.find(desc)?.let { Pace.of(it.groupValues[1]) }

    fun parse(text: String): List<Character> = text.lines().mapNotNull { l ->
        val i = l.indexOf('=')
        if (i <= 0) null else {
            val n = l.substring(0, i).trim()
            val d = l.substring(i + 1).trim()
            if (n.isEmpty() || d.isEmpty()) null else Character(n, d, paceOf(d))
        }
    }

    /** Đoán người nói từ nhãn tên ở đầu câu ("Geralt: ..." hoặc "GERALT ..."). Không thấy nhãn thì null. */
    fun speakerOf(src: String, cast: List<Character>): Character? {
        val t = src.trimStart()
        if (t.isEmpty()) return null
        for (c in cast) for (n in c.names) {
            if (n.length < 2 || t.length <= n.length) continue
            if (!t.startsWith(n, ignoreCase = true)) continue
            val next = t[n.length]
            if (next == ':' || next == '：' || next == '\n' || next == '-' || next == '—') return c
            if (next == ' ' && n.length >= 3 && t.startsWith(n.uppercase(Locale.ROOT))) return c
        }
        return null
    }

    /** Dòng đưa vào câu lệnh: các nhân vật có mặt trong câu hoặc câu trước đứng đầu, tối đa [max] người. */
    fun promptLine(cast: List<Character>, context: String, max: Int = 14): String {
        if (cast.isEmpty()) return ""
        val ctx = context.lowercase(Locale.ROOT)
        val (hit, rest) = cast.partition { c -> c.names.any { it.length >= 2 && ctx.contains(it.lowercase(Locale.ROOT)) } }
        val pick = (hit + rest).take(max)
        return " Nhân vật trong game (giữ đúng giọng nói và cách xưng hô của từng người): " +
            pick.joinToString("; ") { it.name + ": " + it.desc.trimEnd('.', ';') } + "."
    }

    /** Thêm các dòng chưa có tên nhân vật đó vào thẻ. */
    fun merge(existing: String, add: List<String>): String {
        val have = existing.lines().mapNotNull { it.substringBefore('=', "").trim().lowercase(Locale.ROOT).takeIf { s -> s.isNotEmpty() } }.toHashSet()
        val sb = StringBuilder(existing.trimEnd())
        for (l in add) {
            val k = l.substringBefore('=', "").trim().lowercase(Locale.ROOT)
            if (k.isEmpty() || k in have) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(l.trim())
            have.add(k)
        }
        return sb.toString()
    }
}
