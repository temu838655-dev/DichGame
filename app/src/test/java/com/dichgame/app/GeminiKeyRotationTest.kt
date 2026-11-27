package com.dichgame.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GeminiKeyRotationTest {
    private val keys = listOf("key-A", "key-B", "key-C")
    private val scope = "gemini-test-model"

    @Before fun resetBefore() = KeyRing.reset()
    @After fun resetAfter() = KeyRing.reset()

    @Test fun ordinary429RotatesToTheNextKeyUntilOneSucceeds() {
        val attempts = mutableListOf<String>()
        val result = Ai.withGeminiKeys(keys, scope) { key ->
            attempts += key
            if (key != "key-C") throw AiException(429, "per-key quota", 60_000L)
            "Bản dịch thành công"
        }

        assertEquals("Bản dịch thành công", result)
        assertEquals(keys, attempts)
        assertEquals(KeyRing.State.QUOTA, KeyRing.status("key-A", scope).state)
        assertEquals(KeyRing.State.QUOTA, KeyRing.status("key-B", scope).state)
        assertEquals(KeyRing.State.OK, KeyRing.status("key-C", scope).state)
        assertEquals(1, KeyRing.usable(keys, scope))
    }

    @Test fun whenEveryKeyReturns429EachKeyIsTriedOnceBeforeCooldown() {
        val attempts = mutableListOf<String>()
        try {
            Ai.withGeminiKeys(keys, scope) { key ->
                attempts += key
                throw AiException(429, "quota exhausted", 45_000L)
            }
            fail("Phải báo hết quota sau khi đã thử tất cả khóa")
        } catch (e: AiException) {
            assertEquals(429, e.code)
            assertTrue(e.retryAfterMs > 0L)
        }

        assertEquals(keys, attempts)
        assertEquals(0, KeyRing.usable(keys, scope))
    }

    @Test fun accountBillingLimitStopsWithoutWastingOtherKeys() {
        val attempts = mutableListOf<String>()
        try {
            Ai.withGeminiKeys(keys, scope) { key ->
                attempts += key
                throw AiException(429, "credit_balance_exhausted", billingLimit = true)
            }
            fail("Billing limit phải được trả về ngay")
        } catch (e: AiException) {
            assertEquals(429, e.code)
            assertTrue(e.billingLimit)
        }

        assertEquals(listOf("key-A"), attempts)
        assertTrue(KeyRing.billingWaitMs(scope) > 0L)
    }
}
