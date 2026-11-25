package com.dichgame.app
import org.junit.Assert.*
import org.junit.Test

class OcrLatencyTest {
    @Test fun separatesEnginesAndResets() {
        val s = SessionStats()
        s.reset(1, 80)
        for (ms in listOf(100L, 200L, 300L)) s.ocrLatency(false, ms)
        s.ocrLatency(true, 900)
        s.ocrLatency(true, -1)
        assertEquals(200L, s.ocrPercentile(false, 50))
        assertEquals(900L, s.ocrPercentile(true, 90))
        assertTrue(s.report(1000, 80).contains("OCR offline"))
        s.reset(1000, 80)
        assertEquals(-1L, s.ocrPercentile(false, 50))
        assertEquals(-1L, s.ocrPercentile(true, 50))
    }
}
