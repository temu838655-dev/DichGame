package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRequestTest {
    @Test fun generateContentBodyHasRequiredTextAndBoundedOutput() {
        val body = Ai.geminiBody("Translate: hello", withSafety = false)
        val contents = body.getJSONArray("contents")
        val parts = contents.getJSONObject(0).getJSONArray("parts")
        assertEquals("Translate: hello", parts.getJSONObject(0).getString("text"))
        assertEquals(512, body.getJSONObject("generationConfig").getInt("maxOutputTokens"))
        assertTrue(!body.has("safetySettings"))
    }

    @Test fun safetySettingsAreOnlyIncludedWhenRequested() {
        val body = Ai.geminiBody("hello", withSafety = true)
        assertEquals(4, body.getJSONArray("safetySettings").length())
    }
}
