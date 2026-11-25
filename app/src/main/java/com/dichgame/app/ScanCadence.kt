package com.dichgame.app

object ScanCadence {
    fun delay(base: Long, skipped: Int, confirming: Boolean, fast: Boolean): Long {
        if (confirming || fast || skipped < 3) return base
        // Do not slow a configured fast scan beyond 500 ms.
        if (base >= 500L) return base
        return (base + minOf(skipped, 8) * 20L).coerceAtMost(500L)
    }
}
