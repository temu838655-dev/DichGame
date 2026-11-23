package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedTranslationRulesTest {
    @Test fun repeatedSentenceHasStableKeyAcrossWhitespaceAndCase() {
        val a = LearnedTranslationRules.key("GTA VI", "en", "Lucia", false, "We need to get out of here!")
        val b = LearnedTranslationRules.key("GTA VI", "EN", " lucia ", false, "  WE   need to get out of here!  ")
        assertEquals(a, b)
    }

    @Test fun speakerAndGameLanguageAndMenuStaySeparate() {
        val src = "We need to get out of here!"
        val lucia = LearnedTranslationRules.key("GTA VI", "en", "Lucia", false, src)
        assertNotEquals(lucia, LearnedTranslationRules.key("GTA VI", "en", "Jason", false, src))
        assertNotEquals(lucia, LearnedTranslationRules.key("GTA VI", "ja", "Lucia", false, src))
        assertNotEquals(lucia, LearnedTranslationRules.key("GTA V", "en", "Lucia", false, src))
        assertNotEquals(lucia, LearnedTranslationRules.key("GTA VI", "en", "Lucia", true, src))
        assertNotEquals(lucia, LearnedTranslationRules.key("GTA VI", "en", "Lucia", false, "We need to get out of here?"))
    }

    @Test fun meaningfulVietnameseLineMayBeRemembered() {
        assertTrue(LearnedTranslationRules.canRemember(
            "We need to get out of here!", "Chúng ta phải rời khỏi đây ngay!"))
        assertTrue(LearnedTranslationRules.canRemember(
            "彼女が戻るまで待ってください。", "Hãy đợi cho đến khi cô ấy quay lại."))
    }

    @Test fun shortAmbiguousOrUntranslatedLinesAreNotRemembered() {
        assertFalse(LearnedTranslationRules.canRemember("Yes.", "Vâng."))
        assertFalse(LearnedTranslationRules.canRemember("No!", "Không!"))
        assertFalse(LearnedTranslationRules.canRemember("We need to get out of here!", "We need to get out of here!"))
        assertFalse(LearnedTranslationRules.canRemember(
            "We need to get out of here!", ""))
    }

    @Test fun normalizeDoesNotRemovePunctuationThatCanChangeMeaning() {
        val a = LearnedTranslationRules.key("GTA VI", "en", null, false, "Don't shoot!")
        val b = LearnedTranslationRules.key("GTA VI", "en", null, false, "Don't shoot?")
        assertNotEquals(a, b)
    }
}
