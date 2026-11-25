package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSecurityTest {
    @Test fun cloudApiRequiresHttpsWithoutEmbeddedCredentials() {
        assertTrue(Ai.isSecureCloudUrl("https://api.example.com/v1"))
        assertTrue(Ai.isSecureCloudUrl("HTTPS://api.example.com:8443/v1"))
        assertFalse(Ai.isSecureCloudUrl("http://api.example.com/v1"))
        assertFalse(Ai.isSecureCloudUrl("https:///v1"))
        assertFalse(Ai.isSecureCloudUrl("https://user:pass@api.example.com/v1"))
        assertFalse(Ai.isSecureCloudUrl("https://api.example.com:0/v1"))
        assertFalse(Ai.isSecureCloudUrl("file:///tmp/api"))
    }

    @Test fun billingLimitClassificationIsProviderNeutral() {
        assertTrue(Ai.isBillingLimitResponse(429, """{"error":{"code":"insufficient_quota"}}"""))
        assertTrue(Ai.isBillingLimitResponse(403, """{"error":"billing_not_active"}"""))
        assertFalse(Ai.isBillingLimitResponse(429, """{"error":{"code":"rate_limit_exceeded"}}"""))
        assertFalse(Ai.isBillingLimitResponse(503, """{"error":{"code":"insufficient_quota"}}"""))
    }

    @Test fun translationAndVoiceTestsExplainBillingSeparatelyFromRateLimit() {
        val billing = AiException(429, "insufficient_quota", billingLimit = true)
        val rateLimit = AiException(429, "rate limit", retryAfterMs = 30_000)
        assertTrue(aiTestErrorText(billing).contains("thanh toán"))
        assertTrue(aiSpeechTestErrorText(billing, "gemini-3.8-flash-tts").contains("thanh toán"))
        assertTrue(aiTestErrorText(rateLimit).contains("hết lượt"))
        assertTrue(aiSpeechTestErrorText(rateLimit, "gemini-3.8-flash-tts").contains("hết lượt"))
    }
}
