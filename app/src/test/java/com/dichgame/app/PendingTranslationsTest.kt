package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class PendingTranslationsTest {
    @Test fun duplicateRequestsShareOneFlight() {
        val pending = PendingTranslations<String, String>()
        val received = mutableListOf<String>()
        val flight = pending.begin("same") { received.add("first:$it") }!!
        repeat(10) {
            assertNull(pending.begin("same") { received.add("joined:$it") })
        }
        assertTrue(pending.contains("same"))
        pending.take("same", flight).forEach { it("vi") }
        assertEquals(11, received.size)
        assertEquals("first:vi", received.first())
        assertFalse(pending.contains("same"))
        assertTrue(pending.take("same", flight).isEmpty())
    }

    @Test fun staleCompletionDoesNotRemoveNewFlight() {
        val pending = PendingTranslations<String, String>()
        val old = pending.begin("same") { fail("Old callback must not run") }!!
        pending.clear()
        val next = pending.begin("same") {}!!
        assertTrue(pending.take("same", old).isEmpty())
        assertTrue(pending.contains("same"))
        assertEquals(1, pending.take("same", next).size)
    }

    @Test fun requestIdentityIsolatesGameSpeakerAndMenu() {
        val pending = PendingTranslations<TranslationRequestKey, String>()
        val key = TranslationRequestKey("gameA", "jason", false, "Run!")
        val keys = listOf(key, key.copy(context = "gameB"), key.copy(speaker = "lucia"),
            key.copy(ui = true), key.copy(text = "Don't run!"))
        for (k in keys) assertNotNull(pending.begin(k) {})
        assertNull(pending.begin(key) {})
    }

    @Test fun separatorsInsideFieldsDoNotCollide() {
        val a = TranslationRequestKey("game", "ui|x", false, "y")
        val b = TranslationRequestKey("game", "x", true, "y")
        assertNotEquals(a, b)
    }
}
