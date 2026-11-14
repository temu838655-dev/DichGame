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

    @Test fun keepsBriefOneWordDialogue() {
        val result = mutableListOf<FloatArray>()
        val segmenter = GameAudioSegmenter(onUtterance = result::add)
        segmenter.accept(silence(200))
        segmenter.accept(tone(120))
        segmenter.accept(silence(600))
        assertEquals(1, result.size)
        assertTrue("Short utterance should retain audio context", result.single().size >= 120 * 16)
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
