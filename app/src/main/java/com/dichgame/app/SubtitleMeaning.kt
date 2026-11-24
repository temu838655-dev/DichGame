package com.dichgame.app

import java.util.Locale

/** Words that cannot safely be inferred from character similarity alone. */
internal object SubtitleMeaning {
    private val contraction = "(?:ai|are|ca|could|did|does|do|had|has|have|is|must|need|sha|should|was|were|wo|would)n['’]?t"
    private val cue = Regex(
        """(?<![\p{L}\p{N}])(?:$contraction|cannot|not|no|never|without|nothing|nobody|nowhere|neither|nor|left|right|open|close|lock|unlock|enable|disable|đừng|không|chưa|chớ)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    fun signature(text: String): String = cue.findAll(text).joinToString("|") {
        val word = it.value.lowercase(Locale.ROOT)
        if (word == "cannot" || word.endsWith("n't") || word.endsWith("n’t") || word.endsWith("nt")) "not" else word
    }

    // Compare only direct commands with the same action in both languages.
    // Idiomatic pairs such as "Don't move" / "Đứng yên" use different actions.
    private val actions = mapOf(
        "mở" to "open", "đóng" to "close", "khép" to "close",
        "khóa" to "lock", "khoá" to "lock", "mở khóa" to "unlock", "mở khoá" to "unlock",
        "chạy" to "run", "đợi" to "wait", "chờ" to "wait",
        "dừng" to "stop", "ngừng" to "stop", "di chuyển" to "move",
        "nhúc nhích" to "move", "cử động" to "move"
    )
    private val sourceCommand = Regex(
        """^(?:please,?\s+)?(?:(do\s+not|don['’]?t|never)\s+)?(open|close|shut|lock|unlock|run|wait|stop|move)\b""",
        RegexOption.IGNORE_CASE
    )
    private val vietnameseVerbs = actions.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val targetCommand = Regex(
        """^(?:(?:xin vui lòng|vui lòng|xin|làm ơn|hãy|cứ|mau)\s+)*(?:(đừng(?: có)?|chớ|cấm|không(?: được(?: phép)?)?)\s+)?($vietnameseVerbs)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )
    private val qualifier = Regex("""\b(?:unless|until|only|except|without)\b""", RegexOption.IGNORE_CASE)

    private val directionEn = Regex("""^(?:please,?\s+)?(?:turn|go|move|look|head|drive)\s+(?:to\s+the\s+)?(left|right)\b""", RegexOption.IGNORE_CASE)
    private val directionVi = Regex("""^(?:(?:hãy|xin|vui lòng|làm ơn)\s+)*(?:rẽ|quẹo|đi|di chuyển|nhìn|quay|lái)\s+(?:(?:sang|về|qua|bên|phía|hướng)\s+)*(trái|phải)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    fun reversesDirection(source: String, vietnamese: String): Boolean {
        if ('?' in source || '?' in vietnamese || qualifier.containsMatchIn(source)) return false
        val en = directionEn.find(source.trimStart()) ?: return false
        val vi = directionVi.find(vietnamese.trimStart()) ?: return false
        // Skip commands with multiple directional references; they need context.
        if (Regex("""\b(?:left|right)\b""", RegexOption.IGNORE_CASE).findAll(source).count() != 1) return false
        return en.groupValues[1].equals("left", true) != vi.groupValues[1].equals("trái", true)
    }

    fun reversesCommand(source: String, vietnamese: String): Boolean {
        val en = sourceCommand.find(source.trimStart()) ?: return false
        val vi = targetCommand.find(vietnamese.trimStart()) ?: return false
        if ('?' in source || '?' in vietnamese || qualifier.containsMatchIn(source)) return false
        val verb = en.groupValues[2].lowercase(Locale.ROOT)
        val sameAction = (if (verb == "shut") "close" else verb) == actions[vi.groupValues[2].lowercase(Locale.ROOT)]
        return sameAction && (en.groupValues[1].isEmpty() != vi.groupValues[1].isEmpty())
    }
}
