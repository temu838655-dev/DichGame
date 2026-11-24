package com.dichgame.app

/** Conservative selection between the two installed source languages. */
object SourceLanguage {
    fun choose(text: String, fallback: String): String {
        val counts = LangDetect.scriptCounts(text)
        val han = counts['H'] ?: 0
        val latin = counts['L'] ?: 0
        if ((counts['K'] ?: 0) > 0 || (counts['G'] ?: 0) > 0) return fallback
        if (han >= 2 && han >= latin / 3) return "zh"
        if (han == 0 && latin >= 3 && LangDetect.guess(text)?.lang == "en") return "en"
        return fallback
    }
    fun ocrScore(text: String, confidence: Float, expected: String): Float {
        if (text.isBlank()) return 0f
        val counts = LangDetect.scriptCounts(text)
        val han = counts['H'] ?: 0
        val latin = counts['L'] ?: 0
        val letters = counts.values.sum()
        if (letters == 0) return 0f
        val useful = if (expected == "zh") han else latin
        if (useful == 0) return 0f
        val guess = choose(text, expected)
        val penalty = if (guess != expected) .35f else 1f
        return useful.coerceAtMost(80) * confidence.coerceIn(0f, 1f) * penalty
    }
}
