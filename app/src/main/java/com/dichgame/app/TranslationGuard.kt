package com.dichgame.app

import java.util.Locale

/**
 * Kiểm tra bản dịch (AI hoặc máy) trước khi hiện: có đúng là bản dịch không, có thêm hoặc mất nội dung rõ ràng không,
 * có đổi tên nhân vật, mất số liệu, còn nguyên chữ gốc, hay AI lỡ viết lời giải thích/từ chối thay cho bản dịch.
 * Kiểm tra nhanh, không gọi mạng; không thay thế việc đọc hiểu, chỉ chặn các lỗi đo được. Không phụ thuộc Android.
 */
object TranslationGuard {
    enum class Level { OK, SUSPECT, REJECT }

    class Verdict(val level: Level, val text: String, val reasons: List<String>) {
        val ok: Boolean get() = level == Level.OK
        /** Nghi ngờ nặng (mất/đổi/thêm số, lẫn chữ lạ). Nghi ngờ nhẹ chỉ là độ dài hay cách gọi thuật ngữ khác. */
        val severe: Boolean get() = reasons.any { it == "mất hoặc đổi số" || it == "thêm số không có trong câu gốc" || it == "lẫn chữ lạ" }
        /** Dùng được để hiện cho người chơi: không bị loại và không nghi ngờ nặng. */
        val usable: Boolean get() = level != Level.REJECT && !severe
    }

    private val LABEL = Regex("^\\s*(bản dịch|dịch|translation|translated|vietnamese|tiếng việt|vi)\\s*[:：]\\s*", RegexOption.IGNORE_CASE)
    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")
    private val NOTE_LINE = Regex("^\\s*(\\(?\\s*(lưu ý|ghi chú|chú thích|giải thích|note|notes|translator'?s? note|hoặc|or)(?![\\p{L}]).*|[*•]\\s.*|\\d+[.)]\\s.*)$", RegexOption.IGNORE_CASE)
    private val REFUSAL = listOf(
        "i cannot translate", "i can't translate", "as an ai", "here is the translation", "here's the translation", "the translation is",
        "tôi không thể dịch", "không thể dịch", "dưới đây là bản dịch", "đây là bản dịch", "bản dịch của câu",
        "cần thêm ngữ cảnh", "không có đủ ngữ cảnh", "nội dung không phù hợp",
    )
    private val commonEnglish = setOf("yes", "no", "wait", "stop", "run", "go", "help", "continue", "retry", "cancel", "exit", "settings", "loading", "new game", "game over", "try again", "thank you", "come here", "look out", "watch out", "good morning", "good night", "i love you")
    private val QUOTES = "\"“”«»'‘’「」『』"

    /** Bỏ phần AI hay thêm vào quanh bản dịch: rào markdown, nhãn "Bản dịch:", dấu ngoặc kép bao ngoài, dòng ghi chú. */
    fun clean(out: String, src: String): String {
        var t = out.trim().replace(FENCE, "").trim()
        t = t.replace(LABEL, "").trim()
        val srcLines = src.lines().count { it.isNotBlank() }.coerceAtLeast(1)
        var lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > srcLines) {
            val kept = lines.takeWhile { !NOTE_LINE.matches(it) || it === lines.first() }
            if (kept.isNotEmpty()) lines = kept
        }
        t = lines.joinToString("\n")
        if (t.length >= 2 && t.first() in QUOTES && t.last() in QUOTES && (src.trim().firstOrNull() ?: ' ') !in QUOTES) t = t.substring(1, t.length - 1).trim()
        return t
    }

    private fun words(s: String): List<String> = s.split(Regex("[\\s\\n]+")).map { it.trim { c -> !c.isLetterOrDigit() } }.filter { it.isNotEmpty() }

    private fun digitRuns(s: String): List<String> = Regex("\\d+").findAll(s).map { it.value }.toList()

    /**
     * [names]: các tên (nguồn → cách viết tiếng Việt) bắt buộc giữ; mỗi tên có mặt trong câu gốc thì bản dịch phải có tên đó
     * (hoặc cách viết đã quy ước). Trả REJECT nếu chắc chắn hỏng, SUSPECT nếu nghi ngờ, OK nếu qua các phép đo.
     */
    fun check(src: String, out: String, names: List<Pair<String, String>> = emptyList(), sourceLang: String = "en"): Verdict {
        val reasons = ArrayList<String>()
        var reject = false
        val t = out.trim()
        if (t.isEmpty()) return Verdict(Level.REJECT, t, listOf("bản dịch rỗng"))
        val low = t.lowercase(Locale.ROOT)
        if (sourceLang == "en" && SubtitleMeaning.reversesCommand(src, t)) {
            return Verdict(Level.REJECT, t, listOf("bản dịch đảo nghĩa mệnh lệnh khẳng định/phủ định"))
        }
        if (sourceLang == "en" && SubtitleMeaning.reversesDirection(src, t)) {
            return Verdict(Level.REJECT, t, listOf("bản dịch đảo hướng trái/phải"))
        }
        if (sourceLang == "en" && words(src.lowercase(Locale.ROOT)).joinToString(" ") in commonEnglish &&
            words(low).joinToString(" ") == words(src.lowercase(Locale.ROOT)).joinToString(" ")) {
            return Verdict(Level.REJECT, t, listOf("câu tiếng Anh ngắn chưa được dịch"))
        }
        if (REFUSAL.any { low.contains(it) }) { reject = true; reasons.add("AI viết lời giải thích hoặc từ chối") }
        if (t.contains("```")) { reject = true; reasons.add("còn định dạng markdown") }

        val srcLetters = src.filter { it.isLetter() }
        val outLetters = t.filter { it.isLetter() }
        val srcDom = LangDetect.dominantScript(src)?.first ?: 'L'
        val srcCjk = srcDom == 'H' || srcDom == 'K' || srcDom == 'G' || srcDom == 'T'

        // còn chữ viết khác Latin (nguyên chữ gốc hoặc bịa thêm): bản dịch tiếng Việt là chữ Latin
        if (outLetters.isNotEmpty()) {
            val foreign = outLetters.count { LangDetect.scriptOf(it) != 'L' }
            val share = foreign.toFloat() / outLetters.length
            if (share > 0.25f) { reject = true; reasons.add("còn chữ ngoài tiếng Việt") }
            else if (share > 0.08f && !srcCjk) { reasons.add("lẫn chữ lạ") }
        }

        // chưa được dịch: câu Latin dài ra y nguyên
        if (srcDom == 'L' && words(src).size >= 3 && OcrGuard.key(src) == OcrGuard.key(t)) {
            val g = LangDetect.guess(src)
            if (g != null && g.lang != "vi") { reject = true; reasons.add("chưa dịch (giống hệt câu gốc)") }
        }

        // lặp từ
        val ow = words(t.lowercase(Locale.ROOT))
        var run = 1
        for (i in 1 until ow.size) { if (ow[i] == ow[i - 1]) { run++; if (run >= 4) { reject = true; reasons.add("lặp từ"); break } } else run = 1 }

        // dài ngắn bất thường so với câu gốc (không thêm, không bỏ ý)
        val srcWeight = if (srcCjk) srcLetters.length.toFloat() else words(src).size.toFloat()
        if (srcWeight >= 4f && !reject) {
            val ratio = ow.size / srcWeight
            val lo = if (srcCjk) 0.25f else 0.45f
            val hi = if (srcCjk) 2.5f else 3.2f
            if (ratio < lo * 0.5f) { reject = true; reasons.add("bản dịch ngắn hơn nhiều so với câu gốc") }
            else if (ratio < lo) reasons.add("bản dịch ngắn bất thường (có thể mất ý)")
            else if (ratio > hi * 1.5f) { reject = true; reasons.add("bản dịch dài hơn nhiều so với câu gốc") }
            else if (ratio > hi) reasons.add("bản dịch dài bất thường (có thể thêm ý)")
        } else if (srcWeight in 1f..3.9f && !srcCjk && ow.size > srcWeight * 3 + 4 && !reject) {
            reasons.add("bản dịch dài bất thường (có thể thêm ý)")
        }

        // số liệu giữ nguyên
        val sd = digitRuns(src)
        val od = digitRuns(t)
        val outTokens = ow.toSet()
        val remaining = od.toMutableList()
        val missing = sd.filterNot { remaining.remove(it) }
        if (missing.any { !spelledOut(it, outTokens) } ||
            missing.groupingBy { it }.eachCount().any { (n, count) -> count > 1 && spelledOut(n, outTokens) }) {
            reasons.add("mất hoặc đổi số")
        } else if (remaining.isNotEmpty()) reasons.add("thêm số không có trong câu gốc")

        // tên nhân vật, thuật ngữ bắt buộc
        val srcLow = src.lowercase(Locale.ROOT)
        for ((s, d) in names) {
            if (s.length < 3) continue
            if (!containsWord(srcLow, s.lowercase(Locale.ROOT))) continue
            if (!low.contains(d.lowercase(Locale.ROOT)) && !low.contains(s.lowercase(Locale.ROOT))) { reasons.add("đổi tên/thuật ngữ \"$s\""); break }
        }

        val level = when {
            reject -> Level.REJECT
            reasons.isNotEmpty() -> Level.SUSPECT
            else -> Level.OK
        }
        return Verdict(level, t, reasons)
    }

    private val NUM_WORDS = listOf("không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín", "mười")

    /** Số nhỏ (0-10) được viết bằng chữ trong bản dịch ("3" -> "ba") vẫn coi là còn giữ số. */
    private fun spelledOut(digits: String, outTokens: Set<String>): Boolean {
        val n = digits.toIntOrNull() ?: return false
        return n in 0..10 && NUM_WORDS[n] in outTokens
    }

    private fun containsWord(hay: String, needle: String): Boolean {
        var i = hay.indexOf(needle)
        while (i >= 0) {
            val before = if (i == 0) ' ' else hay[i - 1]
            val after = if (i + needle.length >= hay.length) ' ' else hay[i + needle.length]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            i = hay.indexOf(needle, i + 1)
        }
        return false
    }
}
