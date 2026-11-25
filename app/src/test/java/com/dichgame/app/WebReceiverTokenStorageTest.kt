package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray

class WebReceiverTokenStorageTest {
    @Test fun parsesOnlyValidHexTokens() {
        val token = "a".repeat(64)
        val json = JSONArray().put(token).put("invalid").put("b".repeat(64)).toString()
        assertEquals(setOf(token, "b".repeat(64)), parseWebReceiverTokens(json))
    }

    @Test fun malformedEncryptedPayloadCanFallBackToLegacyTokens() {
        assertNull(parseWebReceiverTokens("{not-json"))
        assertNull(parseWebReceiverTokens(""))
    }

    @Test fun validEmptyPayloadRemainsAuthoritativeAfterRevocation() {
        assertEquals(emptySet<String>(), parseWebReceiverTokens("[]"))
    }
}
