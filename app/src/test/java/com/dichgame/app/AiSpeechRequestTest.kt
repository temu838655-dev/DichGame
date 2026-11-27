package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSpeechRequestTest {
    @Test fun ttsUsesVerbatimTranscriptAndStructuredStyleMetadata() {
        val body = AiSpeech.generateBody("Đừng đi!", "urgent, tense", "Kore")
        val part = body.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0)

        assertEquals("Đừng đi!", part.getString("text"))
        assertFalse(part.getString("text").contains("Đọc chính xác"))
        assertEquals("urgent, tense", part.getJSONObject("speechMetadata").getString("style"))
        assertEquals("AUDIO", body.getJSONObject("generationConfig")
            .getJSONArray("responseModalities").getString(0))
        assertEquals("Kore", body.getJSONObject("generationConfig")
            .getJSONObject("speechConfig").getJSONObject("voiceConfig")
            .getJSONObject("prebuiltVoiceConfig").getString("voiceName"))
    }

    @Test fun ttsSanitizesControlCharactersAndRejectsEmptyTranscript() {
        val body = AiSpeech.generateBody("Xin chào\\u0000\\nđừng đi\\u0007", "", "Kore")
        val text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("Xin chào\\nđừng đi", text)
        try {
            AiSpeech.generateBody("  \\u0000\\u0007 ", "", "Kore")
            org.junit.Assert.fail("empty transcript must be rejected")
        } catch (e: AiException) {
            assertEquals(400, e.code)
        }
    }

    @Test fun ttsOmitsEmptyStyleMetadata() {
        val body = AiSpeech.generateBody("Xin chào.", "", "Kore")
        val part = body.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0)
        assertEquals("Xin chào.", part.getString("text"))
        assertFalse(part.has("speechMetadata"))
        assertTrue(body.getJSONObject("generationConfig").has("speechConfig"))
    }
}
