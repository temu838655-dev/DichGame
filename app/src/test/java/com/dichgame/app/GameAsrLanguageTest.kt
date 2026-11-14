package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GameAsrLanguageTest {
    @Test fun usesDetectedSupportedLanguage() {
        assertEquals("ja", GameAsrLanguage.resolve("ja", "en"))
    }

    @Test fun normalizesLocaleStyleLanguageLabels() {
        assertEquals("zh", GameAsrLanguage.resolve("ZH-cn", "en"))
    }

    @Test fun fallsBackWhenWhisperHasNoLanguageLabel() {
        assertEquals("ko", GameAsrLanguage.resolve("", "ko"))
        assertEquals("ko", GameAsrLanguage.resolve(null, "ko"))
    }

    @Test fun doesNotGuessUnsupportedLanguages() {
        assertEquals("en", GameAsrLanguage.resolve("xx", "en"))
    }
}
