package com.dichgame.app

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class KeyRingTest {
    private val keys = listOf("A", "B", "C")
    private val m = "gemini-flash"

    @Before fun clean() = KeyRing.reset()
    @After fun clean2() = KeyRing.reset()

    private fun quota(retry: Long = 0, daily: Boolean = false) = AiException(429, "q", retry, perDay = daily)

    @Test fun usesFirstKeyAndSticksToTheOneThatWorks() {
        assertEquals("A", KeyRing.pick(keys, m, 0))
        KeyRing.success("A", m)
        assertEquals("A", KeyRing.pick(keys, m, 10))
    }

    @Test fun exhaustedKeyIsSkippedUntilItRecovers() {
        assertTrue(KeyRing.report("A", m, quota(retry = 21_000), now = 1_000))
        assertEquals("B", KeyRing.pick(keys, m, 2_000))
        // chưa tới giờ hồi: vẫn bỏ qua; tới giờ hồi thì dùng lại được
        assertEquals("B", KeyRing.pick(keys, m, 21_000))
        assertEquals("A", KeyRing.pick(keys, m, 22_001))
    }

    @Test fun switchingReportedOnce() {
        assertFalse(KeyRing.success("A", m))      // lần đầu: không phải đổi
        KeyRing.report("A", m, quota(retry = 30_000), now = 0)
        assertTrue(KeyRing.success("B", m))       // đổi từ A sang B
        assertFalse(KeyRing.success("B", m))
    }

    @Test fun allExhaustedReturnsNullAndEarliestWait() {
        KeyRing.report("A", m, quota(retry = 60_000), now = 0)
        KeyRing.report("B", m, quota(retry = 20_000), now = 0)
        KeyRing.report("C", m, quota(retry = 90_000), now = 0)
        assertNull(KeyRing.pick(keys, m, 1_000))
        assertEquals(19_000L, KeyRing.waitMs(keys, m, 1_000))
        assertEquals(0L, KeyRing.waitMs(keys, m, 20_001))
        assertEquals("B", KeyRing.pick(keys, m, 20_001))
    }

    @Test fun rateLimitedFirstKeyImmediatelyMovesToSecondKey() {
        val first = KeyRing.acquire(keys, m, 0)
        assertEquals("A", first)
        assertTrue(KeyRing.report("A", m, quota(60_000), now = 0))
        KeyRing.release("A", m)
        assertEquals("B", KeyRing.acquire(keys, m, 1))
        assertEquals(KeyRing.State.QUOTA, KeyRing.status("A", m, 1).state)
        KeyRing.success("B", m)
    }

    @Test fun invalid400Or401KeyIsSkippedImmediately() {
        assertEquals("A", KeyRing.acquire(keys, m, 0))
        assertTrue(KeyRing.report("A", m, AiException(400, "API key not valid", badKey = true), now = 0))
        KeyRing.release("A", m)
        assertEquals("B", KeyRing.acquire(keys, m, 1))
        KeyRing.release("B", m)
        assertTrue(KeyRing.report("B", m, AiException(401, "unauthorized", badKey = true), now = 2))
        assertEquals("C", KeyRing.pick(keys, m, 3))
    }

    @Test fun quotaIsPerModel() {
        KeyRing.report("A", "tts-model", quota(retry = 60_000), now = 0)
        assertNull(KeyRing.pick(listOf("A"), "tts-model", 1_000))
        assertEquals("A", KeyRing.pick(listOf("A"), m, 1_000))
    }

    @Test fun badKeyIsBadForEveryModel() {
        assertTrue(KeyRing.report("A", m, AiException(400, "API key not valid", badKey = true), now = 0))
        assertEquals("B", KeyRing.pick(keys, "other-model", 1_000))
        assertEquals(KeyRing.State.BAD, KeyRing.status("A", "other-model", 1_000).state)
        assertTrue(KeyRing.allBad(listOf("A"), 1_000))
        assertFalse(KeyRing.allBad(keys, 1_000))
    }

    @Test fun forgetLetsFixedKeyWorkAtOnce() {
        KeyRing.report("A", m, AiException(403, "leaked", badKey = true), now = 0)
        KeyRing.report("B", m, quota(retry = 60_000), now = 0)
        KeyRing.forget("A"); KeyRing.forget("B")
        assertEquals("A", KeyRing.pick(keys, m, 1))
        assertEquals(KeyRing.State.OK, KeyRing.status("B", m, 1).state)
    }

    @Test fun otherErrorsAreNotAboutTheKey() {
        for (c in listOf(500, 503, 404, 400, 403)) assertFalse("mã $c", KeyRing.report("A", m, AiException(c, "x"), now = 0))
        assertEquals("A", KeyRing.pick(keys, m, 1))
    }

    @Test fun dailyQuotaWaitsUntilPacificMidnight() {
        val noon = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles")).apply {
            clear(); set(2026, Calendar.NOVEMBER, 23, 12, 0, 0)
        }.timeInMillis
        val ms = KeyRing.quotaBlockMs(0, true, noon)
        assertEquals(12 * 3_600_000L + 60_000L, ms)
        KeyRing.report("A", m, quota(daily = true), now = noon)
        assertEquals("B", KeyRing.pick(keys, m, noon + 3_600_000L))
        assertEquals("A", KeyRing.pick(keys, m, noon + ms + 1))
    }

    @Test fun minuteQuotaDefaultsAndClamps() {
        assertEquals(60_000L, KeyRing.DEFAULT_QUOTA_MS)
        assertEquals(60_000L, KeyRing.quotaBlockMs(0, false, 0))
        assertEquals(1_000L, KeyRing.quotaBlockMs(100, false, 0))
        assertEquals(7_200_000L, KeyRing.quotaBlockMs(7_200_000, false, 0))
        assertEquals(21_000L, KeyRing.quotaBlockMs(21_000, false, 0))
    }

    @Test fun readsServerErrorBodies() {
        val daily = """{"error":{"code":429,"details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        val minute = """{"error":{"code":429,"details":[{"violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]}]}}"""
        assertTrue(KeyRing.isDailyQuota(daily))
        assertFalse(KeyRing.isDailyQuota(minute))
        assertTrue(KeyRing.isBadKey(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        assertTrue(KeyRing.isBadKey(403, """{"error":{"message":"Your API key was reported as leaked."}}"""))
        assertTrue(KeyRing.isBadKey(401, ""))
        assertFalse(KeyRing.isBadKey(403, """{"error":{"message":"Model not enabled for this project"}}"""))
        assertFalse(KeyRing.isBadKey(400, """{"error":{"message":"Invalid JSON payload"}}"""))
        assertFalse(KeyRing.isBadKey(500, "API_KEY_INVALID"))
    }

    @Test fun usableCountsKeysLeft() {
        assertEquals(3, KeyRing.usable(keys, m, 0))
        KeyRing.report("A", m, quota(retry = 60_000), now = 0)
        KeyRing.report("B", m, AiException(401, "x", badKey = true), now = 0)
        assertEquals(1, KeyRing.usable(keys, m, 1))
    }

    @Test fun normalizesMultiKeyInputAndRemovesDuplicates() {
        assertEquals(listOf("AIzaOne", "AIzaTwo"), KeyRing.normalizeKeys("  AIzaOne  \\n\\nAIzaTwo\\r\\nAIzaOne\\t"))
    }

    @Test fun emptyKeyListHasNothing() {
        assertNull(KeyRing.pick(emptyList(), m, 0))
        assertEquals(0L, KeyRing.waitMs(emptyList(), m, 0))
        assertFalse(KeyRing.allBad(emptyList(), 0))
    }


    @Test fun acquireReservesKeysAcrossConcurrentRequestsAndReleasesThem() {
        assertEquals("A", KeyRing.acquire(keys, m, 0))
        assertEquals("B", KeyRing.acquire(keys, m, 0))
        assertEquals("C", KeyRing.acquire(keys, m, 0))
        assertNull(KeyRing.acquire(keys, m, 0))
        assertTrue(KeyRing.hasInFlight(keys, m, 0))
        // A failed request must still release its lease through finally in Ai.withGeminiKey.
        KeyRing.release("B", m)
        assertEquals("B", KeyRing.acquire(keys, m, 0))
        KeyRing.release("A", m)
        KeyRing.release("B", m)
        KeyRing.release("C", m)
        assertFalse(KeyRing.hasInFlight(keys, m, 0))
        assertEquals("A", KeyRing.acquire(keys, m, 0))
    }

    @Test fun quotaReportWhileReservedReleasesKeyForCooldownNotReuse() {
        assertEquals("A", KeyRing.acquire(keys, m, 0))
        assertTrue(KeyRing.report("A", m, quota(60_000), now = 0))
        KeyRing.release("A", m)
        assertEquals("B", KeyRing.acquire(keys, m, 1))
        assertEquals(KeyRing.State.QUOTA, KeyRing.status("A", m, 1).state)
        KeyRing.release("B", m)
    }

    @Test fun humanWait() {
        assertEquals("21 giây", AiRetry.humanWait(21_000))
        assertEquals("5 phút", AiRetry.humanWait(5 * 60_000L))
        assertEquals("12 giờ", AiRetry.humanWait(12 * 3_600_000L))
    }

    @Test fun accountBillingLimitIsNotMistakenForOrdinaryRateLimit() {
        val billing = """{"error":{"code":"insufficient_quota","message":"billing hard limit reached"}}"""
        assertTrue(KeyRing.isBillingLimit(billing))
        assertFalse(KeyRing.isBillingLimit("""{"error":{"code":"rate_limit_exceeded"}}"""))
        val error = AiException(429, billing, billingLimit = true)
        assertTrue(KeyRing.report("A", m, error, now = 0))
        val status = KeyRing.status("A", m, 0)
        assertEquals(KeyRing.State.QUOTA, status.state)
        assertTrue(status.leftMs >= 60 * 60_000L)
        assertEquals(KeyRing.State.QUOTA, KeyRing.status("B", m, 0).state)
        assertTrue(KeyRing.waitMs(keys, m, 0) >= 60 * 60_000L)
    }

}
