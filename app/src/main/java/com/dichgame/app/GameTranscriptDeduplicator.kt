package com.dichgame.app

import java.util.Locale

/**
 * Conservative OCR/ASR overlap detection. Only suppresses a partial match when
 * the shorter transcript contains multiple meaningful tokens (or characters
 * for scripts that do not normally separate words with spaces).
 */
internal object GameTranscriptDeduplicator {
    /** Suppress partial OCR/ASR duplicates only while the OCR result is still recent. */
    fun overlapsRecentOcr(
        candidate: String,
        ocrText: String,
        ocrAtMs: Long,
        nowMs: Long,
        windowMs: Long = 3_000L
    ): Boolean {
        if (ocrAtMs <= 0L || windowMs < 0L) return false
        val ageMs = nowMs - ocrAtMs
        if (ageMs !in 0L..windowMs) return false
        return isPartialOverlap(candidate, ocrText)
    }

    fun isPartialOverlap(candidate: String, reference: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        val normalizedReference = normalize(reference)
        if (normalizedCandidate.isEmpty() || normalizedReference.isEmpty() ||
            normalizedCandidate == normalizedReference
        ) return false

        // Chinese, Japanese, Korean and Thai dialogue may not use spaces between words.
        // Token-only matching would treat each whole sentence as one token and
        // fail to detect OCR/ASR duplicates in those languages.
        if (containsUnspacedScript(normalizedCandidate) || containsUnspacedScript(normalizedReference)) {
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
            if (shorter.length < MIN_UNSPACED_OVERLAP_CHARS) return false
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

    private fun containsUnspacedScript(value: String): Boolean = value.any {
        it in '\u3040'..'\u30ff' || // Hiragana and Katakana
            it in '\u3400'..'\u4dbf' || // CJK Extension A
            it in '\u4e00'..'\u9fff' || // CJK Unified Ideographs
            it in '\uf900'..'\ufaff' || // CJK Compatibility Ideographs
            it in '\uac00'..'\ud7af' || // Hangul syllables
            it in '\u1100'..'\u11ff' || // Hangul Jamo
            it in '\u0e00'..'\u0e7f' // Thai
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private const val MIN_LATIN_OVERLAP_CHARS = 6
    private const val MIN_UNSPACED_OVERLAP_CHARS = 3
    private const val MIN_OVERLAP_RATIO = 0.45
}
