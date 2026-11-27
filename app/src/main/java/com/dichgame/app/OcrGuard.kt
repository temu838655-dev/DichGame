package com.dichgame.app

import java.util.Locale

/**
 * Cổng kiểm tra chữ đọc từ màn hình trước khi dịch: chỉ cho qua thứ trông như lời thoại thật.
 * Chữ rác do nền phức tạp, hiệu ứng, biểu tượng, đồng hồ, thanh máu, số liệu... bị loại ở đây,
 * thay vì đưa sang bộ dịch rồi hiện ra một câu bịa. Không phụ thuộc Android.
 */
object OcrGuard {
    class Verdict(val ok: Boolean, val text: String, val reason: String = "") {
        /** Câu chỉ 1-2 chữ cái: có thể là "No", "Go", "OK" thật nhưng cũng dễ là rác, nên cần thấy lặp lại qua nhiều khung hình. */
        val short: Boolean get() = ok && letterCount(text) <= 2
    }

    private val ZERO_WIDTH = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
    private val SPACES = Regex("[ \\t\\u00A0\\u3000]+")
    private val EDGE_JUNK = Regex("^[|_~`^*#=<>\\\\/\\-]+|[|_~`^*#=<>\\\\/\\-]+$")
    private val VOWELS = "aeiouyàáâãäåæèéêëìíîïòóôõöøùúûüýÿœ"

    fun letterCount(s: String): Int = s.count { it.isLetter() }

    /** Gộp khoảng trắng, bỏ ký tự vô hình, bỏ ký hiệu thừa (| _ ~ ...) dính ở hai đầu mỗi dòng. */
    fun clean(raw: String): String =
        raw.filter { it == '\\n' || it == '\\t' || !it.isISOControl() }
            .replace(ZERO_WIDTH, "").lines()
            .map { it.replace(SPACES, " ").trim().replace(EDGE_JUNK, "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /**
     * Kiểm tra [text] (đã clean). [conf] là độ tin cậy 0..1 do bộ đọc chữ báo (1 nếu không có).
     * Tham số [minConf] mặc định 0.35: dưới mức đó bộ đọc chữ gần như đang đoán.
     */
    fun check(text: String, conf: Float = 1f, minConf: Float = 0.35f): Verdict {
        val t = text.trim()
        if (t.isEmpty()) return Verdict(false, t, "trống")
        if (conf < minConf) return Verdict(false, t, "độ tin cậy thấp")
        val noSpace = t.filter { !it.isWhitespace() }
        val letters = noSpace.count { it.isLetter() }
        if (letters == 0) return Verdict(false, t, "không có chữ")
        val digits = noSpace.count { it.isDigit() }
        val others = noSpace.length - letters - digits
        // dấu câu thường gặp trong lời thoại không tính là ký hiệu lạ
        val punct = noSpace.count { it in ".,!?;:'\"-–—…()[]¿¡“”‘’«»「」『』、。！？：（）・〜～" }
        val symbols = others - punct
        if (symbols * 100 / noSpace.length > 30) return Verdict(false, t, "nhiều ký hiệu")
        if (letters * 100 / noSpace.length < 50) return Verdict(false, t, "ít chữ, nhiều số hoặc ký hiệu")

        // một chữ lặp gần hết (IIIII, llll, ........)
        val groups = noSpace.filter { it.isLetter() }.lowercase(Locale.ROOT).groupingBy { it }.eachCount()
        val top = groups.values.maxOrNull() ?: 0
        if (letters >= 4 && top * 100 / letters >= 70) return Verdict(false, t, "một chữ lặp lại")

        val latinish = noSpace.count { LangDetect.scriptOf(it) == 'L' } * 2 > letters
        if (latinish) {
            val tokens = t.split(Regex("[\\s\\n]+")).map { it.trim { c -> !c.isLetter() } }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) {
                var junk = 0
                for (w in tokens) if (junkWord(w)) junk++
                if (junk * 2 >= tokens.size && (tokens.size >= 2 || junk == 1 && tokens[0].length >= 6)) {
                    return Verdict(false, t, "giống chữ rác")
                }
            }
        }
        return Verdict(true, t)
    }

    /** Từ Latin "không giống từ": một dãy phụ âm dài, không nguyên âm, hoặc chữ hoa chữ thường xen kẽ lung tung. */
    internal fun junkWord(w: String): Boolean {
        val s = w.lowercase(Locale.ROOT)
        if (s.length < 4 || s.any { LangDetect.scriptOf(it) != 'L' }) return false
        if (s.none { it in VOWELS } && !s.all { it == 'z' || it == 'x' }) return true
        var run = 0
        for (c in s) {
            if (c in VOWELS || !c.isLetter()) run = 0 else if (++run >= 6) return true
        }
        // aBcDeF: từ có từ 2 chữ hoa nằm giữa từ và không phải toàn hoa (McDonald, iPhone chỉ có 1 chữ hoa giữa từ nên qua)
        val mid = w.drop(1)
        if (w.length >= 5 && mid.count { it.isUpperCase() } >= 2 && mid.any { it.isLowerCase() }) return true
        return false
    }

    /** Hai chuỗi cùng nội dung chữ (bỏ khác biệt hoa thường, dấu câu, khoảng trắng). */
    fun sameText(a: String, b: String): Boolean = key(a) == key(b)

    fun key(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Tỉ lệ ký tự giống nhau (Levenshtein) giữa hai khóa; dùng để gộp lần đọc hơi khác nhau của cùng một dòng. */
    fun similarity(a: String, b: String): Float {
        val x = key(a); val y = key(b)
        if (x.isEmpty() && y.isEmpty()) return 1f
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        val n = x.length; val m = y.length
        if (n > 400 || m > 400) return if (x.startsWith(y.take(40)) || y.startsWith(x.take(40))) 0.8f else 0f
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            for (j in 1..m) {
                val c = if (x[i - 1] == y[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + c)
            }
            val t = prev; prev = cur; cur = t
        }
        return 1f - prev[m].toFloat() / maxOf(n, m)
    }
}
