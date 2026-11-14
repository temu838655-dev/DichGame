package com.dichgame.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineGameAsrTest {
    @Test
    fun monoPcmConvertsToNormalizedFloat() {
        val actual = OfflineGameAsr.toMonoFloat(shortArrayOf(0, 16384, -16384, 32767), 1)
        assertEquals(4, actual.size)
        assertEquals(0f, actual[0], 0.0001f)
        assertEquals(0.5f, actual[1], 0.0001f)
        assertEquals(-0.5f, actual[2], 0.0001f)
        assertEquals(32767 / 32768f, actual[3], 0.0001f)
    }

    @Test
    fun stereoPcmIsDownmixedWithoutClipping() {
        val actual = OfflineGameAsr.toMonoFloat(shortArrayOf(16384, 16384, -16384, 16384), 2)
        assertArrayEquals(floatArrayOf(0.5f, 0f), actual, 0.0001f)
    }

    @Test
    fun resamplingPreservesSamplesAtSameRate() {
        val input = floatArrayOf(-0.5f, 0f, 0.5f)
        assertArrayEquals(input, OfflineGameAsr.resample(input, 16000, 16000), 0f)
    }

    @Test
    fun resamplingConvertsTwentyFourKhzToSixteenKhz() {
        val input = FloatArray(240) { 0.25f }
        val actual = OfflineGameAsr.resample(input, 24000, 16000)
        assertEquals(160, actual.size)
        assertArrayEquals(FloatArray(160) { 0.25f }, actual, 0.0001f)
    }

    @Test
    fun resamplingKeepsVeryShortNonEmptyUtterance() {
        val actual = OfflineGameAsr.resample(floatArrayOf(0.1f, 0.2f), 48000, 16000)
        assertEquals(1, actual.size)
        assertEquals(0.1f, actual[0], 0.0001f)
    }
}
