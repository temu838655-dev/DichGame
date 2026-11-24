package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePacksTest {
    @Test fun qualityDefaultsPreferCarefulReadingAndContext() {
        assertTrue(Prefs.DEFAULT_READ_CAREFULLY)
        assertEquals(6, Prefs.DEFAULT_CONTEXT_COUNT)
        assertEquals(333, Prefs.DEFAULT_SCAN_MS)
    }

    @Test fun supportedSourcesAreOnlyEnglishAndChinese() {
        assertEquals(listOf("en", "zh"), Prefs.LANGS.map { it.first }.filter { it != "vi" })
        assertTrue(Prefs.isSupportedSource("en"))
        assertTrue(Prefs.isSupportedSource("zh"))
        for (code in listOf("vi", "ja", "ko", "fr", "de", "ru", "th")) {
            assertFalse(Prefs.isSupportedSource(code))
            assertEquals("en", Prefs.normalizeSource(code))
        }
        assertEquals("zh", Prefs.normalizeSource("zh"))
        assertEquals("en", Prefs.normalizeSource(null))
    }
}
