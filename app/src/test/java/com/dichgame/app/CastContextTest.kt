package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class CastContextTest {
    private val cast = Cast.parse("Anna = speaks gently\nBen = speaks firmly\nAnn = speaks slowly\nCarl = speaks quickly")
    @Test fun activeSpeakerFirstAndNoSubstringMatch() {
        val prompt = Cast.promptLine(cast, "Anna is here", speaker = "Ben")
        assertTrue(prompt.indexOf("Ben:") < prompt.indexOf("Anna:"))
        assertFalse(prompt.contains("Ann:"))
        assertFalse(prompt.contains("Carl:"))
    }
    @Test fun unknownSpeakerUsesSmallFallback() {
        assertFalse(Cast.promptLine(cast, "Nobody here").contains("Carl:"))
    }
    @Test fun aliasMatchesActiveSpeaker() {
        val cards = Cast.parse("Geralt / White Wolf = speaks calmly\nCiri = speaks quickly")
        assertTrue(Cast.promptLine(cards, "Go", speaker = "White Wolf").contains("Geralt"))
        assertFalse(Cast.promptLine(cards, "Go", speaker = "White Wolf").contains("Ciri"))
    }
}
