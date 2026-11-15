package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsTest {
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

    @Test fun speakerColorIsStable() {
        assertEquals(SpeakerColor.of("Geralt"), SpeakerColor.of("geralt"))
        assertNotEquals(SpeakerColor.of("Geralt"), SpeakerColor.of("Yennefer"))
    }
}
