package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationContextHistoryTest {
    @Test
    fun snapshotContainsOnlyEarlierDialogue() {
        val history = TranslationContextHistory()

        assertEquals(emptyList<String>(), history.appendAndSnapshot("A", 3))
        assertEquals(listOf("A"), history.appendAndSnapshot("B", 3))
        assertEquals(listOf("A", "B"), history.appendAndSnapshot("C", 3))
        assertEquals(listOf("B", "C"), history.appendAndSnapshot("D", 2))
    }

    @Test
    fun zeroContextDoesNotSendEarlierLines() {
        val history = TranslationContextHistory()
        history.appendAndSnapshot("Earlier line", 3)

        assertEquals(emptyList<String>(), history.appendAndSnapshot("Current line", 0))
    }

    @Test
    fun historyRemainsBoundedToTenEntries() {
        val history = TranslationContextHistory()
        repeat(12) { history.appendAndSnapshot("line-$it", 10) }

        assertEquals(
            (2..11).map { "line-$it" },
            history.appendAndSnapshot("line-12", 10)
        )
    }
}
