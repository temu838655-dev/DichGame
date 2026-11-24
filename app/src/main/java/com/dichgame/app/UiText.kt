package com.dichgame.app

/**
 * Lọc và bảo vệ chữ giao diện game (menu, vật phẩm, kỹ năng, nhiệm vụ, mô tả...) trước khi dịch. Thuần Kotlin để có unit test.
 * Mục tiêu: không dịch nhầm ID, mã hệ thống, biến, số liệu, chữ viết tắt chỉ số, chữ đã là tiếng Việt, rác do đọc chữ sai.
 */
object UiText {
    enum class Verdict { TRANSLATE, EMPTY, NO_LETTERS, MOSTLY_SYMBOLS, ID_LIKE, ABBREVIATION, VIETNAMESE, TOO_SHORT, TOO_LONG, LOW_CONFIDENCE }

    /** Chữ viết tắt chỉ số, thuật ngữ máy chơi game quen thuộc: giữ nguyên, người chơi đã quen. */
    private val KEEP = setOf(
        "hp", "mp", "sp", "xp", "exp", "atk", "def", "str", "dex", "int", "luk", "fps", "hud", "npc", "dlc", "ok", "ui",
        "pvp", "pve", "rpg", "fov", "dps", "aoe", "cd", "ap", "lv", "lvl", "ng+", "l1", "l2", "l3", "r1", "r2", "r3",
    )

    private val ID_RE = Regex("^[A-Za-z0-9_.\\-/:\\\\]+$")
    private val FILE_RE = Regex("\\.(png|jpe?g|dds|tga|wav|ogg|mp3|json|xml|ini|cfg|exe|dll|pak|bin|dat|uasset|lua|txt)$", RegexOption.IGNORE_CASE)
    private val URL_RE = Regex("^(https?://|www\\.)", RegexOption.IGNORE_CASE)
    /** Ký tự chỉ có trong tiếng Việt (dấu mũ, móc, nặng, hỏi, ngã, đ...). Chữ Pháp, Bồ Đào Nha... không dùng các ký tự này. */
    private const val VN_ONLY = "ăằắẳẵặâầấẩẫậơờớởỡợưừứửữựđạảãẹẻẽịỉĩọỏõụủũỳýỵỷỹếềểễệồốổỗộ" +
        "ĂẰẮẲẴẶÂẦẤẨẪẬƠỜỚỞỠỢƯỪỨỬỮỰĐẠẢÃẸẺẼỊỈĨỌỎÕỤỦŨỲÝỴỶỸẾỀỂỄỆỒỐỔỖỘ"

    const val MAX_LEN = 400
    const val MIN_CONFIDENCE = 0.5f

    fun verdict(raw: String, conf: Float = 1f): Verdict {
        val t = raw.trim()
        if (t.isEmpty()) return Verdict.EMPTY
        val letters = t.count { it.isLetter() }
        if (letters == 0) return Verdict.NO_LETTERS
        if (conf < MIN_CONFIDENCE) return Verdict.LOW_CONFIDENCE
        if (t.length > MAX_LEN) return Verdict.TOO_LONG
        if (t.lowercase() in KEEP) return Verdict.ABBREVIATION
        if (URL_RE.containsMatchIn(t) || FILE_RE.containsMatchIn(t)) return Verdict.ID_LIKE
        if (!t.any { it.isWhitespace() } && ID_RE.matches(t)) {
            val hasDigit = t.any { it.isDigit() }
            if (t.contains('_') || t.count { it == '/' } >= 2 || t.contains('\\') || t.trimEnd(':').contains(':') || hasDigit || hasInnerCamel(t)) return Verdict.ID_LIKE
        }
        val nonSpace = t.count { !it.isWhitespace() }
        if (letters.toFloat() / nonSpace < 0.5f) return Verdict.MOSTLY_SYMBOLS
        // "ATK 42", "ATK 42 DEF 10": mọi từ đều là chữ viết tắt chỉ số quen thuộc → giữ nguyên
        val words = Regex("\\p{L}+").findAll(t).map { it.value.lowercase() }.toList()
        if (words.isNotEmpty() && words.all { it in KEEP }) return Verdict.ABBREVIATION
        if (letters < 2) return Verdict.TOO_SHORT
        if (t.count { it in VN_ONLY } >= 2) return Verdict.VIETNAMESE
        return Verdict.TRANSLATE
    }

    /** "itemSword", "UIButton": chữ thường rồi chữ hoa liền nhau giữa một từ không có khoảng trắng là tên biến, không phải câu chữ. */
    private fun hasInnerCamel(t: String): Boolean {
        for (i in 1 until t.length) if (t[i - 1].isLowerCase() && t[i].isUpperCase()) return true
        return false
    }

    /** Khoá so sánh/ghi nhớ: không phân biệt hoa thường, gộp khoảng trắng. */
    fun key(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Chữ đã thay các phần tử giữ chỗ bằng mã [tokens] (theo thứ tự). */
    class Protected(val text: String, val tokens: List<String>)

    private val PLACEHOLDERS = Regex(
        "\\{[^{}\\n]{0,24}\\}" +                       // {0} {player}
            "|%(?:\\d+\\$)?[sdfi]" +                   // %s %d %1\$s
            "|<[^<>\\n]{1,40}>" +                      // <color=#ff0000> </color> <br>
            "|\\[[A-Z][A-Z0-9_ ]{1,24}\\]" +           // [PLAYER_NAME]
            "|\\$[A-Za-z_][A-Za-z0-9_]*\\$" +          // $VAR$
            "|\\\\n"                                   // \n chữ
    )

    private fun tok(i: Int) = "⟦$i⟧"

    fun protect(text: String): Protected {
        val found = ArrayList<String>()
        val out = StringBuilder()
        var last = 0
        for (m in PLACEHOLDERS.findAll(text)) {
            out.append(text, last, m.range.first)
            found.add(m.value)
            out.append(tok(found.size))
            last = m.range.last + 1
        }
        out.append(text, last, text.length)
        return Protected(out.toString(), found)
    }

    /** Trả lại phần tử giữ chỗ vào bản dịch. Mất hoặc thừa mã thì trả null: không được hiện bản dịch làm hỏng biến. */
    fun restore(translated: String, p: Protected): String? {
        var s = translated
        for (i in p.tokens.indices) {
            val t = tok(i + 1)
            if (s.count(t) != 1) return null
            s = s.replace(t, p.tokens[i])
        }
        if (Regex("⟦\\d+⟧").containsMatchIn(s)) return null
        return s
    }

    /** Check original formatting markers even for imported translations. */
    fun samePlaceholders(source: String, translated: String): Boolean =
        protect(source).tokens.groupingBy { it }.eachCount() ==
            protect(translated).tokens.groupingBy { it }.eachCount()

    fun safeTranslation(source: String, translated: String, uncertain: Boolean, lang: String): Boolean =
        !uncertain && translated.isNotBlank() && key(source) != key(translated) &&
            samePlaceholders(source, translated) && TranslationGuard.check(source, translated, sourceLang = lang).ok

    private fun String.count(sub: String): Int {
        var n = 0
        var i = indexOf(sub)
        while (i >= 0) { n++; i = indexOf(sub, i + sub.length) }
        return n
    }

    /**
     * Gộp các dòng liền nhau của một khối chữ thành một câu: nối bằng khoảng trắng (chữ có dấu gạch nối cuối dòng thì nối liền).
     * Chữ Nhật/Trung/Hàn không dùng khoảng trắng giữa các dòng.
     */
    fun joinLines(text: String, cjk: Boolean): String {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return lines.firstOrNull() ?: ""
        val sb = StringBuilder(lines[0])
        for (i in 1 until lines.size) {
            if (cjk) sb.append(lines[i])
            else if (sb.endsWith("-") && sb.length > 1 && sb[sb.length - 2].isLetter()) { sb.setLength(sb.length - 1); sb.append(lines[i]) }
            else sb.append(' ').append(lines[i])
        }
        return sb.toString()
    }
}
