package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AiContextBudgetTest {
    @Test fun keepsNewestWholeLinesInOrder() {
        assertEquals(listOf("Anna: Go", "Ben: Wait"), Ai.boundedHistory(listOf("old long dialogue", "Anna: Go", "Ben: Wait"), 20))
    }

    @Test fun neverCutsSpeakerOrDialogue() {
        assertEquals(emptyList<String>(), Ai.boundedHistory(listOf("Anna: a long sentence"), 5))
    }

    @Test fun skipsBlankLines() {
        assertEquals(listOf("Go"), Ai.boundedHistory(listOf(" ", "Go", ""), 10))
    }
}
