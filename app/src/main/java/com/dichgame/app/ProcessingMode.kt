package com.dichgame.app

enum class ProcessingMode(val label: String) {
    AUTOMATIC("AI thông minh — Tự động"),
    TRANSLATE_AND_SPEAK("Dịch và thuyết minh"),
    TRANSLATE_ONLY("Chỉ dịch"),
    NARRATE_ONLY("Chỉ thuyết minh");

    companion object {
        fun fromSettings(narrate: Boolean, automatic: Boolean, speak: Boolean) = when {
            narrate -> NARRATE_ONLY
            automatic -> AUTOMATIC
            speak -> TRANSLATE_AND_SPEAK
            else -> TRANSLATE_ONLY
        }
    }
}
