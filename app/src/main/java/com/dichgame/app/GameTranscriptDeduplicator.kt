package com.dichgame.app

import java.util.Locale

/**
 * Conservative OCR/ASR overlap detection. Only suppresses a partial match when
 * the shorter transcript contains at least two meaningful tokens; one-word
 * replies such as "No" and "Go" must remain eligible for translation.
 */
internal object GameTranscriptDeduplicator {
    fun isPartialOverlap(candidate: String, reference: String): Boolean {
        val candidateTokens = normalize(candidate).split(' ').filter(String::isNotEmpty)
        val referenceTokens = normalize(reference).split(' ').filter(String::isNotEmpty)
        if (candidateTokens.size < 2 || referenceTokens.size < 2) return false
        if (candidateTokens == referenceTokens) return false

        val shorter: List<String>
        val longer: List<String>
        if (candidateTokens.size < referenceTokens.size) {
            shorter = candidateTokens
            longer = referenceTokens
        } else {
            shorter = referenceTokens
            longer = candidateTokens
        }
        if (shorter.sumOf { it.length } < 6) return false
        if (shorter.size.toDouble() / longer.size < 0.45) return false
        return longer.windowed(shorter.size).any { it == shorter }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
