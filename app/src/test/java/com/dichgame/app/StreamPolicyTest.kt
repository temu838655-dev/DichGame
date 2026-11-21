package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamPolicyTest {
    @Test fun autoPicks1080pOnlyWhenDeviceAndScreenAreBigEnough() {
        assertEquals(StreamPolicy.RES_1080, StreamPolicy.resolveRes(StreamPolicy.RES_AUTO, true, 2400))
        assertEquals(StreamPolicy.RES_720, StreamPolicy.resolveRes(StreamPolicy.RES_AUTO, false, 2400))
        assertEquals(StreamPolicy.RES_720, StreamPolicy.resolveRes(StreamPolicy.RES_AUTO, true, 1280))
    }

    @Test fun manualChoiceIsNeverOverridden() {
        assertEquals(StreamPolicy.RES_720, StreamPolicy.resolveRes(StreamPolicy.RES_720, true, 3000))
        assertEquals(StreamPolicy.RES_1080, StreamPolicy.resolveRes(StreamPolicy.RES_1080, false, 800))
        assertEquals(StreamPolicy.RES_360, StreamPolicy.resolveRes(StreamPolicy.RES_360, true, 3000))
    }

    @Test fun unknownValuesFallBackToSafeDefaults() {
        assertEquals(StreamPolicy.RES_720, StreamPolicy.resolveRes(99, false, 2400))
        assertEquals(60, StreamPolicy.fps(0))
        assertEquals(60, StreamPolicy.fps(120))
        assertEquals(30, StreamPolicy.fps(30))
        assertEquals(StreamPolicy.CODEC_H264, StreamPolicy.codec(7))
        assertEquals(StreamPolicy.CODEC_H265, StreamPolicy.codec(1))
    }

    @Test fun summaryShowsRealRequestedNumbers() {
        assertEquals("1080p · 60 FPS · H.264 · 15 Mbps", StreamPolicy.summary(StreamPolicy.RES_1080, 60, StreamPolicy.CODEC_H264, 0))
        assertEquals("720p · 30 FPS · H.265 · 5 Mbps", StreamPolicy.summary(StreamPolicy.RES_720, 30, StreamPolicy.CODEC_H265, 5))
        assertEquals("540p · 60 FPS · H.264 · 6 Mbps", StreamPolicy.summary(StreamPolicy.RES_540, 60, StreamPolicy.CODEC_H264, 0))
    }
}
