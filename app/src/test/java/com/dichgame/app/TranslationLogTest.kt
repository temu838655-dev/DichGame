package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLogTest {
    @Test fun logFormatShowsOnlyLastFourKeyCharacters() {
        val line = TranslationLog.formatLine(0L, "WARN", "Gemini", 2, "AIza-super-secret-1234", 429,
            "Key #2 gặp 429 -> đã chuyển sang Key #3")
        assertTrue(line.contains("[WARN]"))
        assertTrue(line.contains("Key #2 (****1234)"))
        assertTrue(line.contains("[HTTP 429]"))
        assertFalse(line.contains("AIza-super-secret-1234"))
        assertTrue(line.contains("Key #3"))
    }
}
