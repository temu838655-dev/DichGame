package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class GameAudioSegmenterTest {
    private fun tone(durationMs: Int, sampleRate: Int = 16_000, amplitude: Float = 0.2f): FloatArray {
        val count = durationMs * sampleRate / 1000
        return FloatArray(count) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * amplitude).toFloat()
        }
    }

    private fun silence(durationMs: Int, sampleRate: Int = 16_000) =
        FloatArray(durationMs * sampleRate / 1000)

    @Test fun ignoresPureSilence() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(silence(2_000))
        segmenter.flush()
        assertTrue(result.isEmpty())
    }

    @Test fun ignoresLowLevelBackgroundNoiseBelowSpeechThreshold() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(tone(1_000, amplitude = 0.004f))
        segmenter.flush()
        assertTrue("Low-level background should not become a transcript", result.isEmpty())
    }

    @Test fun acceptsAnEightyMillisecondQuietUtteranceAfterLowLevelBackground() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(tone(500, amplitude = 0.004f))
        segmenter.accept(tone(80, amplitude = 0.02f))
        segmenter.accept(silence(600))
        assertEquals("The minimum-duration short utterance must be retained", 1, result.size)
        assertTrue(result.single().size >= 80 * 16)
    }

    @Test fun keepsBriefOneWordDialogue() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(silence(200))
        segmenter.accept(tone(120))
        segmenter.accept(silence(600))
        assertEquals(1, result.size)
        assertTrue("Short utterance should retain audio context", result.single().size >= 120 * 16)
    }

    @Test fun doesNotJoinSpeechAcrossAnAudioGapReset() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(tone(40))
        segmenter.reset()
        segmenter.accept(tone(40))
        segmenter.accept(silence(600))
        assertEquals("Two partial phrases on opposite sides of a dropped-audio gap must not be joined", 0, result.size)
    }

    @Test fun resetClearsPreRollSoAudioBeforeGapCannotLeakIntoNextUtterance() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(tone(140, amplitude = 0.004f))
        segmenter.reset()
        segmenter.accept(tone(100, amplitude = 0.2f))
        segmenter.accept(silence(600))
        assertEquals(1, result.size)
        assertTrue("Only post-gap speech should be emitted", result.single().size <= 100 * 16 + 120 * 16)
    }

    @Test fun producesSameUtteranceWhenPcmArrivesInIrregularChunkSizes() {
        val audio = tone(180) + silence(600)
        val wholeResult = mutableListOf<FloatArray>()
        GameAudioSegmenter(onUtterance = wholeResult::add).accept(audio)
        // Re-run as irregular decoder chunks that do not align to 20 ms frames.
        val chunkedResult = mutableListOf<FloatArray>()
        val chunkedSegmenter = GameAudioSegmenter(onUtterance = chunkedResult::add)
        var offset = 0
        val sizes = intArrayOf(137, 911, 53, 2048, 317, 29)
        var index = 0
        while (offset < audio.size) {
            val end = minOf(audio.size, offset + sizes[index++ % sizes.size])
            chunkedSegmenter.accept(audio.copyOfRange(offset, end))
            offset = end
        }
        assertEquals(1, chunkedResult.size)
        assertEquals(1, wholeResult.size)
        assertArrayEquals(wholeResult.single(), chunkedResult.single(), 0.000001f)
    }

    @Test fun separatesUtterancesAfterSilence() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(tone(300))
        segmenter.accept(silence(600))
        segmenter.accept(tone(250))
        segmenter.accept(silence(600))
        assertEquals(2, result.size)
    }

    @Test fun flushEmitsSpeechWhenStreamStopsBeforeSilenceTimeout() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(tone(180))
        segmenter.flush()
        assertEquals(1, result.size)
        assertTrue(result.single().isNotEmpty())
    }

    @Test fun capsVeryLongContinuousAudioIntoBoundedSegments() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(maxUtteranceMs = 1_000, onUtterance = result::add)
        segmenter.accept(tone(2_200))
        assertTrue(result.size >= 2)
        assertTrue(result.all { it.size <= 1_000 * 16 })
    }
}
