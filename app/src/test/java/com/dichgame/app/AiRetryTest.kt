package com.dichgame.app

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiRetryTest {
    /** Đồng hồ giả: sleep cộng thời gian thay vì chờ thật. */
    private class Clock {
        var t = 0L
        val sleeps = ArrayList<Long>()
        fun sleep(ms: Long) { sleeps.add(ms); t += ms }
        fun now() = t
    }

    private fun ex(code: Int, after: Long = 0L) = AiException(code, "x", after)

    @Test fun validatesGeminiModelIdentifierBeforeSending() {
        assertTrue(Ai.validGeminiModelName("gemini-3.5-flash-lite"))
        assertTrue(Ai.validGeminiModelName("gemini-2.5-flash-preview_05"))
        assertFalse(Ai.validGeminiModelName(""))
        assertFalse(Ai.validGeminiModelName(" gemini-2.5-flash"))
        assertFalse(Ai.validGeminiModelName("gemini/../../other"))
        assertFalse(Ai.validGeminiModelName("gemini model"))
    }

    @Test fun safetySettingsFallbackOnlyForExplicitCompatibilityErrors() {
        assertTrue(AiRetry.shouldRetryWithoutSafetySettings(400,
            """{"error":{"message":"Unknown name 'safetySettings' in request"}}"""))
        assertTrue(AiRetry.shouldRetryWithoutSafetySettings(400,
            """{"error":{"message":"safetySettings are not supported for this model"}}"""))
        assertFalse(AiRetry.shouldRetryWithoutSafetySettings(400,
            """{"error":{"message":"API key not valid. Please pass a valid API key."}}"""))
        assertFalse(AiRetry.shouldRetryWithoutSafetySettings(400,
            """{"error":{"message":"Invalid model name"}}"""))
        assertFalse(AiRetry.shouldRetryWithoutSafetySettings(404,
            """{"error":{"message":"safetySettings unsupported"}}"""))
    }

    @Test fun extractsUsefulProviderErrorDetails() {
        val message = AiRetry.providerErrorMessage(
            """{"error":{"code":400,"status":"INVALID_ARGUMENT","message":"API key not valid","details":[{"reason":"API_KEY_INVALID","domain":"googleapis.com"}]}}"""
        )
        assertTrue(message.contains("API key not valid"))
        assertTrue(message.contains("INVALID_ARGUMENT"))
        assertTrue(message.contains("API_KEY_INVALID"))
    }

    @Test fun invalidRequestExplanationIncludesProviderDetail() {
        val message = AiRetry.explain(AiRetry.Kind.BAD, 400, 0, "API key not valid · INVALID_ARGUMENT")
        assertTrue(message.contains("API key not valid"))
        assertTrue(message.contains("INVALID_ARGUMENT"))
    }

    @Test fun ttsEndpointFallbackDoesNotHideInvalidApiKey() {
        assertTrue(AiRetry.shouldFallbackTtsEndpoint(404, """{"error":{"message":"Interactions endpoint not found"}}"""))
        assertFalse(AiRetry.shouldFallbackTtsEndpoint(404, """{"error":{"message":"Model gemini-unknown not found"}}"""))
        assertTrue(AiRetry.shouldFallbackTtsEndpoint(400, """{"error":{"message":"Unknown field response_format in Interactions request"}}"""))
        assertFalse(AiRetry.shouldFallbackTtsEndpoint(400, """{"error":{"message":"API key not valid"}}"""))
        assertFalse(AiRetry.shouldFallbackTtsEndpoint(403, """{"error":{"message":"permission denied"}}"""))
    }

    @Test fun classifiesErrors() {
        assertEquals(AiRetry.Kind.QUOTA, AiRetry.kindOf(ex(429)))
        assertEquals(AiRetry.Kind.BILLING, AiRetry.kindOf(AiException(429, "insufficient_quota", billingLimit = true)))
        assertEquals(AiRetry.Kind.NETWORK, AiRetry.kindOf(ex(503)))
        assertEquals(AiRetry.Kind.NETWORK, AiRetry.kindOf(ex(500)))
        assertEquals(AiRetry.Kind.NETWORK, AiRetry.kindOf(ex(408)))
        assertEquals(AiRetry.Kind.NETWORK, AiRetry.kindOf(SocketTimeoutException("t")))
        assertEquals(AiRetry.Kind.NETWORK, AiRetry.kindOf(IOException("mất mạng")))
        assertEquals(AiRetry.Kind.BAD, AiRetry.kindOf(ex(401)))
        assertEquals(AiRetry.Kind.BAD, AiRetry.kindOf(ex(400)))
        assertEquals(AiRetry.Kind.BAD, AiRetry.kindOf(ex(404)))
        assertEquals(AiRetry.Kind.BLOCKED, AiRetry.kindOf(ex(AiRetry.CODE_BLOCKED)))
        assertEquals(AiRetry.Kind.OTHER, AiRetry.kindOf(ex(0)))
        assertEquals(AiRetry.Kind.OTHER, AiRetry.kindOf(IllegalStateException("lạ")))
    }

    @Test fun fallbackOnlyForProviderFailures() {
        assertTrue(AiRetry.canFallback(ex(429)))
        assertTrue(AiRetry.canFallback(AiException(429, "insufficient_quota", billingLimit = true)))
        assertTrue(AiRetry.canFallback(ex(503)))
        assertTrue(AiRetry.canFallback(ex(401)))
        assertTrue(AiRetry.canFallback(SocketTimeoutException("timeout")))
        assertFalse(AiRetry.canFallback(ex(AiRetry.CODE_BLOCKED)))
        assertFalse(AiRetry.canFallback(ex(0)))
        assertFalse(AiRetry.canFallback(InterruptedException()))
    }

    @Test fun everyKindHasItsOwnStatKey() {
        val keys = AiRetry.Kind.values().map { AiRetry.statKey(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun succeedsFirstTimeWithoutSleeping() {
        val c = Clock()
        val r = AiRetry.run(AiRetry.Plan(), c::sleep, c::now) { "xin chào" }
        assertEquals("xin chào", r)
        assertTrue(c.sleeps.isEmpty())
    }

    @Test fun retriesTransientServerErrorThenSucceeds() {
        val c = Clock()
        var n = 0
        val r = AiRetry.run(AiRetry.Plan(), c::sleep, c::now) {
            n++
            if (n < 3) throw ex(503)
            "ok"
        }
        assertEquals("ok", r)
        assertEquals(3, n)
        assertEquals(2, c.sleeps.size)
        assertTrue("first retry jitter range", c.sleeps[0] in 400L..600L)
        assertTrue("second exponential retry jitter range", c.sleeps[1] in 800L..1200L)
    }


    @Test fun exponentialBackoffIsCappedAndJittered() {
        val c = Clock()
        var n = 0
        try {
            AiRetry.run(AiRetry.Plan(maxAttempts = 5, backoffMs = 500, maxBackoffMs = 1200), c::sleep, c::now) {
                n++
                throw ex(503)
            }
            fail("phải ném lỗi")
        } catch (e: AiException) {
            assertEquals(503, e.code)
        }
        assertEquals(4, c.sleeps.size)
        assertTrue(c.sleeps[0] in 400L..600L)
        assertTrue(c.sleeps[1] in 800L..1200L)
        assertTrue(c.sleeps[2] in 900L..1200L)
        assertTrue(c.sleeps[3] in 900L..1200L)
    }

    @Test fun serverRetryAfterIsNeverShortenedByJitter() {
        val c = Clock()
        var n = 0
        val r = AiRetry.run(AiRetry.Plan(), c::sleep, c::now) {
            n++
            if (n == 1) throw ex(429, after = 2000)
            "ok"
        }
        assertEquals("ok", r)
        assertEquals(listOf(2000L), c.sleeps)
    }

    @Test fun retriesTimeouts() {
        val c = Clock()
        var n = 0
        val r = AiRetry.run(AiRetry.Plan(), c::sleep, c::now) {
            n++
            if (n == 1) throw SocketTimeoutException("read timed out")
            "ok"
        }
        assertEquals("ok", r)
        assertEquals(2, n)
    }

    @Test fun givesUpAfterMaxAttemptsAndRethrowsLastError() {
        val c = Clock()
        var n = 0
        try {
            AiRetry.run(AiRetry.Plan(), c::sleep, c::now) { n++; throw ex(500 + n) }
            fail("phải ném lỗi")
        } catch (e: AiException) {
            assertEquals(503, e.code)   // lỗi của lần thử cuối
        }
        assertEquals(3, n)
    }

    @Test fun neverRetriesBlockedOrBadKey() {
        for (code in listOf(AiRetry.CODE_BLOCKED, 400, 401, 403, 404)) {
            val c = Clock()
            var n = 0
            try {
                AiRetry.run(AiRetry.Plan(), c::sleep, c::now) { n++; throw ex(code) }
                fail("phải ném lỗi")
            } catch (_: AiException) { }
            assertEquals("mã $code không được thử lại", 1, n)
            assertTrue(c.sleeps.isEmpty())
        }
    }

    @Test fun shortQuotaWaitIsHonouredThenRetried() {
        val c = Clock()
        var n = 0
        val r = AiRetry.run(AiRetry.Plan(), c::sleep, c::now) {
            n++
            if (n == 1) throw ex(429, after = 2000)
            "ok"
        }
        assertEquals("ok", r)
        assertEquals(listOf(2000L), c.sleeps)
    }

    @Test fun longQuotaWaitGivesUpAtOnce() {
        // máy chủ bảo chờ 21 giây: không treo câu phụ đề, trả lỗi ngay cho dịch máy lo
        val c = Clock()
        var n = 0
        try {
            AiRetry.run(AiRetry.Plan(), c::sleep, c::now) { n++; throw ex(429, after = 21_000) }
            fail("phải ném lỗi")
        } catch (e: AiException) {
            assertEquals(429, e.code)
            assertEquals(21_000L, e.retryAfterMs)
        }
        assertEquals(1, n)
        assertTrue(c.sleeps.isEmpty())
    }

    @Test fun quotaWithoutHintRetriesBrieflyThenGivesUp() {
        val c = Clock()
        var n = 0
        try {
            AiRetry.run(AiRetry.Plan(), c::sleep, c::now) { n++; throw ex(429) }
            fail("phải ném lỗi")
        } catch (e: AiException) { assertEquals(429, e.code) }
        assertEquals(3, n)
    }

    @Test fun budgetStopsRetriesEvenWhenAttemptsRemain() {
        // mỗi lần gọi tốn 15 giây (quá thời gian chờ): lần thứ hai xong ở 30 giây là quá ngân sách 22 giây
        val c = Clock()
        var n = 0
        try {
            AiRetry.run(AiRetry.Plan(), c::sleep, c::now) { n++; c.t += 15_000; throw SocketTimeoutException("t") }
            fail("phải ném lỗi")
        } catch (_: IOException) { }
        assertEquals(2, n)
    }

    @Test fun parsesRetryDelay() {
        assertEquals(2000L, AiRetry.retryDelayMs("2", ""))
        assertEquals(1500L, AiRetry.retryDelayMs(" 1.5 ", ""))
        assertEquals(21000L, AiRetry.retryDelayMs(null, """{"error":{"code":429,"details":[{"@type":"x.RetryInfo","retryDelay": "21s"}]}}"""))
        assertEquals(0L, AiRetry.retryDelayMs(null, """{"error":{"code":429}}"""))
        assertEquals(2000L, AiRetry.retryDelayMs("Thu, 01 Jan 1970 00:00:03 GMT", "", 1000L))
        assertEquals(0L, AiRetry.retryDelayMs("Thu, 01 Jan 1970 00:00:03 GMT", "", 4000L))
        assertEquals(3000L, AiRetry.retryDelayMs("1", """{"retryDelay":"3s"}"""))
        assertEquals(0L, AiRetry.retryDelayMs("0", ""))
    }

    @Test fun cooldownIsClamped() {
        assertEquals(20_000L, AiRetry.cooldownMs(0))
        assertEquals(5_000L, AiRetry.cooldownMs(1_000))
        assertEquals(21_000L, AiRetry.cooldownMs(21_000))
        assertEquals(3_600_000L, AiRetry.cooldownMs(3_600_000))
    }

    private fun gem(text: String, finish: String = "STOP") =
        """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(text)}}],"role":"model"},"finishReason":"$finish"}]}"""

    @Test fun parsesNormalGeminiAnswer() {
        assertEquals("Xin chào", AiRetry.geminiText(gem("  Xin chào \n")))
    }

    @Test fun joinsPartsAndSkipsThoughts() {
        val j = """{"candidates":[{"content":{"parts":[{"text":"đang nghĩ...","thought":true},{"text":"Xin "},{"text":"chào"}]}}]}"""
        assertEquals("Xin chào", AiRetry.geminiText(j))
    }

    @Test fun malformedGeminiPartInvalidatesTheWholeTranslation() {
        for (part in listOf("null", "7", "{}", """{"text":false}""", """{"text":{"value":"sai"}}""")) {
            val json = """{"candidates":[{"content":{"parts":[{"text":"Một phần"},$part]}}]}"""
            try {
                AiRetry.geminiText(json)
                fail("Không được nhận phần còn lại làm bản dịch hoàn chỉnh")
            } catch (error: AiException) {
                assertEquals(0, error.code)
            }
        }
    }

    @Test fun promptBlockedByFilter() {
        val j = """{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"}}"""
        try { AiRetry.geminiText(j); fail("phải ném lỗi") }
        catch (e: AiException) { assertEquals(AiRetry.CODE_BLOCKED, e.code) }
    }

    @Test fun answerCutBySafetyFinish() {
        val j = gem("Một phần", "SAFETY")
        try { AiRetry.geminiText(j); fail("phải ném lỗi") }
        catch (e: AiException) { assertEquals(AiRetry.CODE_BLOCKED, e.code) }
    }

    @Test fun emptyOrMissingCandidatesIsNotBlocked() {
        for (j in listOf("""{"candidates":[]}""", """{}""", gem("Một phần", "MAX_TOKENS"))) {
            try { AiRetry.geminiText(j); fail("phải ném lỗi: $j") }
            catch (e: AiException) { assertEquals(AiRetry.kindOf(e).toString(), AiRetry.Kind.OTHER, AiRetry.kindOf(e)) }
        }
    }

    @Test fun cloudRequiresCompletedText() {
        fun answer(reason: String, value: String) =
            """{"choices":[{"finish_reason":"$reason","message":{"content":$value}}]}"""
        assertEquals("Xin chào", AiRetry.cloudText(answer("stop", "\"Xin chào\"")))
        for (json in listOf(answer("length", "\"Một phần\""), answer("stop", "null"), answer("stop", "[]"))) {
            try { AiRetry.cloudText(json); fail("Phải từ chối bản dịch chưa hợp lệ") }
            catch (e: AiException) { assertEquals(0, e.code) }
        }
    }

    @Test fun hybridErrorsKeepOriginalAnd400DoesNotAssumeGemini() {
        for (kind in AiRetry.Kind.values()) {
            val message = AiRetry.explain(kind, 400, 0, keepOriginal = true)
            assertTrue(message.contains("Giữ nguyên câu gốc"))
            assertFalse(message.contains("dịch máy"))
        }
        val cloud = AiRetry.explain(AiRetry.Kind.BAD, 400, 0, "invalid model")
        assertTrue(cloud.contains("invalid model"))
        assertFalse(cloud.contains("Gemini"))
        assertTrue(cloud.contains("dịch máy"))
    }

    @Test fun explainMentionsWhatToDo() {
        assertTrue(AiRetry.explain(AiRetry.Kind.QUOTA, 429, 21_000).contains("21 giây"))
        assertTrue(AiRetry.explain(AiRetry.Kind.QUOTA, 429, 0).contains("hạn mức"))
        assertTrue(AiRetry.explain(AiRetry.Kind.BAD, 401, 0).contains("Khóa AI"))
        assertTrue(AiRetry.explain(AiRetry.Kind.BAD, 404, 0).contains("model"))
        assertTrue(AiRetry.explain(AiRetry.Kind.BLOCKED, -1, 0).contains("bộ lọc"))
        for (k in AiRetry.Kind.values()) assertFalse(AiRetry.explain(k, 500, 0).isBlank())
    }

    @Test fun statsExplainWhyMachineTranslationWasUsed() {
        val s = SessionStats()
        s.reset(1_000, 80)
        assertEquals("", s.machineReasons())
        s.inc(AiRetry.statKey(AiRetry.Kind.QUOTA), 5)
        s.inc(AiRetry.statKey(AiRetry.Kind.NETWORK), 2)
        s.inc(AiRetry.statKey(AiRetry.Kind.BILLING))
        s.inc("may_loai")
        val why = s.machineReasons()
        assertTrue(why, why.contains("hết lượt 5"))
        assertTrue(why, why.contains("mạng hoặc máy chủ AI quá tải 2"))
        assertTrue(why, why.contains("giới hạn thanh toán/chi tiêu 1"))
        assertTrue(why, why.contains("bị loại hai lần 1"))
        assertTrue(s.report(2_000, 80), s.report(2_000, 80).contains("Dịch trên máy vì AI không dùng được"))
    }

    @Test fun billingLimitIsNotRetriedAndHasSeparateExplanation() {
        val c = Clock()
        var calls = 0
        try {
            AiRetry.run(AiRetry.Plan(), c::sleep, c::now) {
                calls++
                throw AiException(429, "insufficient_quota", billingLimit = true)
            }
            fail("billing limit should not be retried")
        } catch (e: AiException) {
            assertTrue(e.billingLimit)
        }
        assertEquals(1, calls)
        assertTrue(c.sleeps.isEmpty())
        assertTrue(AiRetry.explain(AiRetry.Kind.BILLING, 429, 0).contains("thanh toán"))
    }


}
