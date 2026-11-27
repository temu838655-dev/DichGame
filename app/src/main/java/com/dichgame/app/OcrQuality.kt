package com.dichgame.app

enum class OcrQuality(val label: String) {
    FAST("Nhanh"), BALANCED("Cân bằng"), CAREFUL("Kỹ");

    fun frames(letters: Int): Int = when (this) {
        FAST -> 1
        BALANCED -> if (letters < 12) 2 else 1
        CAREFUL -> 2
    }

    companion object {
        fun read(saved: String?, legacySlow: Boolean) = values().find { it.name == saved }
            ?: if (legacySlow) CAREFUL else FAST
    }
}
