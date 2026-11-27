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

    @Test fun requestTextSanitizerDropsControlCharactersAndBoundsLength() {
        assertEquals("Xin chào\nđừng đi", Ai.cleanRequestText("  Xin chào\u0000\nđừng đi\u0007  "))
        assertEquals(5, Ai.cleanRequestText("123456789", 5).length)
        assertEquals("", Ai.cleanRequestText("\u0000\u0007"))
    }

    @Test fun safetySettingsAreOnlyIncludedWhenRequested() {
        val body = Ai.geminiBody("hello", withSafety = true)
        assertEquals(4, body.getJSONArray("safetySettings").length())
    }
}
