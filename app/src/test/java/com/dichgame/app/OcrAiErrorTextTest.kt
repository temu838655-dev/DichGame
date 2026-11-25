package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAiErrorTextTest {
    @Test fun billingLimitIsNotReportedAsGeminiKeyQuota() {
        val message = ocrAiErrorText(AiException(429, "billing hard limit", billingLimit = true))
        assertTrue(message.contains("giới hạn thanh toán"))
        assertFalse(message.contains("Mọi khóa Gemini"))
    }

    @Test fun rateLimitUsesProviderNeutralWording() {
        val message = ocrAiErrorText(AiException(429, "rate limit", retryAfterMs = 30_000))
        assertTrue(message.contains("giới hạn tốc độ hoặc hết quota"))
        assertTrue(message.contains("30 giây"))
        assertFalse(message.contains("Mọi khóa Gemini"))
    }

    @Test fun authAndModelErrorsAreNotCalledQuota() {
        assertTrue(ocrAiErrorText(AiException(401, "unauthorized")).contains("khóa API sai"))
        assertTrue(ocrAiErrorText(AiException(404, "model missing")).contains("model hoặc endpoint"))
    }

    @Test fun networkAndServerErrorsHaveDistinctMessages() {
        assertTrue(ocrAiErrorText(AiException(408, "timeout")).contains("thời gian chờ"))
        assertTrue(ocrAiErrorText(AiException(503, "unavailable")).contains("HTTP 503"))
        assertTrue(ocrAiErrorText(java.io.IOException("offline")).contains("offline"))
    }
}
