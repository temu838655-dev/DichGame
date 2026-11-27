package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRequestTest {
    @org.junit.Test fun previewUsesOnlyAvailableFollowingLinesWithinTheLimit() {
        val lines = sequenceOf(" ", "A", "B", "C", "D", "E")
        org.junit.Assert.assertEquals(listOf("A", "B", "C", "D"), Ai.lookahead(lines, 4))
        org.junit.Assert.assertEquals(listOf("A"), Ai.lookahead(sequenceOf("A"), 4))
        org.junit.Assert.assertTrue(Ai.lookahead(emptySequence(), 4).isEmpty())
        org.junit.Assert.assertTrue(Ai.lookahead(lines, 0).isEmpty())
    }

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
