package com.dichgame.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineGameAsrTest {
    @Test
    fun sileroGateAcceptsBriefConfidentDialogue() {
        assertEquals(true, OfflineGameAsr.hasSpeechConfidence(listOf(0.08f, 0.72f, 0.81f, 0.66f, 0.04f)))
    }

    @Test
    fun sileroGateRejectsLowConfidenceBackground() {
        assertEquals(false, OfflineGameAsr.hasSpeechConfidence(listOf(0.05f, 0.21f, 0.12f, 0.08f, 0.03f)))
    }

    @Test
    fun sileroGateRequiresMultipleSpeechWindows() {
        assertEquals(false, OfflineGameAsr.hasSpeechConfidence(listOf(0.91f, 0.02f, 0.01f, 0.03f, 0.02f)))
    }

    @Test
    fun sileroGateRejectsEmptyAudioWindows() {
        assertEquals(false, OfflineGameAsr.hasSpeechConfidence(emptyList()))
    }

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
    fun stereoAntiPhaseAudioDoesNotCancelOutDialogue() {
        val actual = OfflineGameAsr.toMonoFloat(
            shortArrayOf(12_000, -12_000, -8_000, 8_000, 4_000, -4_000), 2
        )
        assertArrayEquals(
            floatArrayOf(12_000 / 32768f, -8_000 / 32768f, 4_000 / 32768f),
            actual,
            0.0001f
        )
    }

    @Test
    fun stereoDownmixStillAveragesNormalInPhaseAudio() {
        val actual = OfflineGameAsr.toMonoFloat(shortArrayOf(8_000, 16_000, -4_000, 4_000), 2)
        assertArrayEquals(floatArrayOf(12_000 / 32768f, 0f), actual, 0.0001f)
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
    fun whisperMinimumInputMatchesTheShortDialogueVadFloor() {
        assertEquals(120 * 16, OfflineGameAsr.MIN_SAMPLES)
    }

    @Test
    fun resamplesEightKhzShortDialogueBeforeApplyingWhisperMinimum() {
        val source = FloatArray(960) { 0.1f } // 120 ms at 8 kHz
        val actual = OfflineGameAsr.resample(source, 8_000, OfflineGameAsr.TARGET_SAMPLE_RATE)
        assertEquals(OfflineGameAsr.MIN_SAMPLES, actual.size)
    }

    @Test
    fun resamplingKeepsVeryShortNonEmptyUtterance() {
        val actual = OfflineGameAsr.resample(floatArrayOf(0.1f, 0.2f), 48000, 16000)
        assertEquals(1, actual.size)
        assertEquals(0.1f, actual[0], 0.0001f)
    }
}
