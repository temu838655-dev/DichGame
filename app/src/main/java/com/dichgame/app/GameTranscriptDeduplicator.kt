package com.dichgame.app

import java.util.Locale

/**
 * Conservative OCR/ASR overlap detection. Only suppresses a partial match when
 * the shorter transcript contains multiple meaningful tokens (or characters
 * for scripts that do not normally separate words with spaces).
 */
internal object GameTranscriptDeduplicator {
    fun isPartialOverlap(candidate: String, reference: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        val normalizedReference = normalize(reference)
        if (normalizedCandidate.isEmpty() || normalizedReference.isEmpty() ||
            normalizedCandidate == normalizedReference
        ) return false

        // Chinese and Japanese dialogue commonly has no spaces between words.
        // Token-only matching would treat each whole sentence as one token and
        // fail to detect OCR/ASR duplicates in those languages.
        if (containsCjk(normalizedCandidate) || containsCjk(normalizedReference)) {
            val compactCandidate = normalizedCandidate.replace(" ", "")
            val compactReference = normalizedReference.replace(" ", "")
            val shorter: String
            val longer: String
            if (compactCandidate.length < compactReference.length) {
                shorter = compactCandidate
                longer = compactReference
            } else {
                shorter = compactReference
                longer = compactCandidate
            }
            if (shorter.length < MIN_CJK_OVERLAP_CHARS) return false
            if (shorter.length.toDouble() / longer.length < MIN_OVERLAP_RATIO) return false
            return longer.contains(shorter)
        }

        val candidateTokens = normalizedCandidate.split(' ').filter(String::isNotEmpty)
        val referenceTokens = normalizedReference.split(' ').filter(String::isNotEmpty)
        if (candidateTokens.size < 2 || referenceTokens.size < 2) return false

        val shorter: List<String>
        val longer: List<String>
        if (candidateTokens.size < referenceTokens.size) {
            shorter = candidateTokens
            longer = referenceTokens
        } else {
            shorter = referenceTokens
            longer = candidateTokens
        }
        if (shorter.sumOf { it.length } < MIN_LATIN_OVERLAP_CHARS) return false
        if (shorter.size.toDouble() / longer.size < MIN_OVERLAP_RATIO) return false
        return longer.windowed(shorter.size).any { it == shorter }
    }

    private fun containsCjk(value: String): Boolean = value.any {
        it in '\u3040'..'\u30ff' || // Hiragana and Katakana
            it in '\u3400'..'\u4dbf' || // CJK Extension A
            it in '\u4e00'..'\u9fff' || // CJK Unified Ideographs
            it in '\uf900'..'\ufaff' // CJK Compatibility Ideographs
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private const val MIN_LATIN_OVERLAP_CHARS = 6
    private const val MIN_CJK_OVERLAP_CHARS = 3
    private const val MIN_OVERLAP_RATIO = 0.45
}
