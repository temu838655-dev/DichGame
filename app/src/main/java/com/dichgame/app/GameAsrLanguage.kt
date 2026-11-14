package com.dichgame.app

/**
 * Accept only language codes supported by the app's translation pipeline.
 * If Whisper returns no usable language label, retain the user's configured
 * source language instead of guessing from the transcript.
 */
internal object GameAsrLanguage {
    private val supported = setOf(
        "en", "ja", "ko", "zh", "fr", "de", "es", "pt", "it", "id",
        "nl", "pl", "tr", "hi", "sv", "cs", "ro", "hu", "da", "fi",
        "no", "ms", "ru", "uk", "th", "ar", "el", "he"
    )

    fun resolve(detected: String?, fallback: String): String {
        val code = detected.orEmpty().trim().lowercase().substringBefore('-')
        return if (code in supported) code else fallback
    }
}
