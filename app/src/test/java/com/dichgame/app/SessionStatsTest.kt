package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsTest {
    @Test fun reportTracksLatestProviderAndDistinguishesUnreportedModel() {
        val stats = SessionStats()
        stats.reset(1, 90)
        stats.recordTranslationModel("Google Gemini", "requested", "returned")
        assertTrue(stats.report(2, 90).contains("Google Gemini · returned"))
        stats.recordTranslationModel("OpenAI", "fallback", null)
        val report = stats.report(3, 90)
        assertTrue(report.contains("OpenAI · fallback (model gửi trong yêu cầu)"))
        assertTrue(!report.contains("Google Gemini · returned"))
        stats.reset(4, 90)
        assertTrue(!stats.report(5, 90).contains("OpenAI · fallback"))
    }

    @Test fun apiAttemptsAndReportedTokensAreSeparate() {
        val s = SessionStats()
        s.reset(1, 50)
        s.inc("api_dich_gemini", 3)
        s.inc("api_dich_cloud")
        s.inc("api_giong_gemini", 2)
        s.recordTokens("api_dich_gemini", 100, 20)
        s.recordTokens("api_dich_gemini", 50, 10)
        s.recordTokens("api_dich_gemini", -1, 10)
        val r = s.report(1000, 50)
        assertTrue(r, r.contains("dịch Gemini 3, dịch CloudAPI 1, giọng Gemini 2"))
        assertTrue(r, r.contains("Token Gemini dịch: vào 150, ra 30 (2 phản hồi có số liệu)"))
        assertEquals(0L, s.get("api_dich_cloud_usage"))
        s.reset(2000, 50)
        assertEquals(0L, s.get("api_dich_gemini_input"))
    }

    @Test fun countsAndPercentiles() {
        val s = SessionStats()
        s.reset(1_000, 80)
        s.inc("quet", 100); s.inc("quet_dung_yen", 40); s.inc("ocr_cau", 7); s.inc("ocr_rac", 2)
        for (ms in listOf(800L, 900L, 1_000L, 1_100L, 2_500L)) s.latency(ms)
        assertEquals(1_000L, s.percentile(50))
        assertEquals(2_500L, s.percentile(100))
        val r = s.report(1_000 + 20 * 60_000L, 72)
        assertTrue(r, r.contains("100 lần"))
        assertTrue(r, r.contains("40%"))
        assertTrue(r, r.contains("giảm 8%"))
        assertTrue(r, r.contains("24% mỗi giờ"))
        assertTrue(r, r.contains("20 phút"))
    }

    @Test fun reportsP95ForSubtitleAndSpeechStartSeparately() {
        val s = SessionStats()
        s.reset(1_000, 80)
        for (ms in listOf(100L, 200L, 300L, 400L, 500L)) s.latency(ms)
        for (ms in listOf(150L, 250L, 350L, 450L)) s.speechLatency(ms)
        assertEquals(500L, s.percentile(95))
        assertEquals(450L, s.speechPercentile(95))
        val report = s.report(2_000, 80)
        assertTrue(report.contains("hiện phụ đề (5 câu): p50 0.3 giây, p95 0.5 giây"))
        assertTrue(report.contains("bắt đầu đọc (4 câu): p50 0.3 giây, p95 0.5 giây"))
    }

    @Test fun ignoresAbsurdLatencies() {
        val s = SessionStats()
        s.reset(1, 50)
        s.latency(-5); s.latency(999_999)
        assertEquals(-1L, s.percentile(50))
    }

    @Test fun emptyReportIsExplained() {
        assertTrue(SessionStats().report(0, 50).contains("Chưa có"))
    }

    @Test fun chargingIsReportedAsGain() {
        val s = SessionStats()
        s.reset(1, 50)
        assertTrue(s.report(5 * 60_000L, 55).contains("tăng 5%"))
    }

    @Test fun reportSeparatesLocalHitsFromProviderErrorsAndUntranslatedText() {
        val s = SessionStats()
        s.reset(1, 100)
        for (key in listOf("dich_cache", "dich_nho", "dich_goi", "goi_gan_tin_cay", "nguyen_van_vi")) s.inc(key)
        s.inc("tra_ban_dich", 5)
        s.recordHttpStatus("api_dich_gemini", 429)
        s.recordHttpStatus("api_dich_gemini", 200)
        s.recordHttpStatus("api_dich_cloud", 400)
        val report = s.report(2, 100)
        assertTrue(report.contains("4/5 lượt tra (80%)"))
        assertTrue(report.contains("HTTP Gemini dịch: 400 = 0, 429 = 1"))
        assertTrue(report.contains("HTTP CloudAPI dịch: 400 = 1, 429 = 0"))
    }

    @Test fun speakerColorIsStable() {
        assertEquals(SpeakerColor.of("Geralt"), SpeakerColor.of("geralt"))
        assertNotEquals(SpeakerColor.of("Geralt"), SpeakerColor.of("Yennefer"))
        for ((_, color) in SpeakerColor.choices) assertEquals(color, SpeakerColor.of("Geralt", color))
        assertEquals(SpeakerColor.of("Geralt"), SpeakerColor.of("Geralt", 0))
    }

    @Test fun audioSourceIsReportedHonestly() {
        val s = SessionStats()
        s.reset(1, 50)
        s.inc("nguon_he_thong"); s.inc("nguon_mic")
        assertTrue(s.report(5 * 60_000L, 50).contains("âm thanh trong của máy, sau đó micro"))
        val n = SessionStats()
        n.reset(1, 50); n.inc("nguon_khong")
        assertTrue(n.report(5 * 60_000L, 50).contains("không nghe được"))
        val o = SessionStats()
        o.reset(1, 50)
        assertTrue(!o.report(5 * 60_000L, 50).contains("Nguồn âm thanh"))
    }
}
