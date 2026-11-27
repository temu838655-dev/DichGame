package com.dichgame.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyManagerTest {
    private val keys = listOf("alpha-secret", "bravo-secret", "charlie-secret")
    @Before fun reset() = KeyRing.reset()
    @After fun cleanup() = KeyRing.reset()

    @Test fun retriesSameRequestWithNextKeyAfter429() {
        val attempts = mutableListOf<String>()
        val value = KeyManager.withKeys("Gemini", keys, "model-x") { key ->
            attempts += key
            if (key != keys[2]) throw AiException(429, "quota", 60_000)
            "translated line"
        }
        assertEquals("translated line", value)
        assertEquals(keys, attempts)
        assertEquals(KeyRing.State.QUOTA, KeyRing.status(keys[0], "model-x").state)
        assertEquals(KeyRing.State.QUOTA, KeyRing.status(keys[1], "model-x").state)
        assertEquals(KeyRing.State.OK, KeyRing.status(keys[2], "model-x").state)
    }

    @Test fun invalidKeyIsSkippedForRestOfSession() {
        val attempts = mutableListOf<String>()
        val value = KeyManager.withKeys("Gemini", keys, "model-x") { key ->
            attempts += key
            if (key == keys[0]) throw AiException(400, "API key not valid", badKey = true)
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(listOf(keys[0], keys[1]), attempts)
        assertEquals(KeyRing.State.BAD, KeyRing.status(keys[0], "another-model").state)
    }

    @Test fun billingLimitDoesNotWasteOtherKeys() {
        val attempts = mutableListOf<String>()
        try {
            KeyManager.withKeys("Gemini", keys, "model-x") { key ->
                attempts += key
                throw AiException(429, "credit_balance_exhausted", billingLimit = true)
            }
        } catch (e: AiException) { assertTrue(e.billingLimit) }
        assertEquals(listOf(keys[0]), attempts)
    }
}
