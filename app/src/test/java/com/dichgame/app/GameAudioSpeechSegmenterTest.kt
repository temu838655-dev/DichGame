package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioSpeechSegmenterTest {
    private fun chunk(sequence: Long, value: Short, durationMs: Int = 20, rate: Int = 16_000): GameAudioPcmQueue.Chunk {
        return GameAudioPcmQueue.Chunk(
            sequence = sequence,
            sampleRate = rate,
            channels = 1,
            samples = ShortArray(rate * durationMs / 1000) { value },
        )
    }

    @Test
    fun retainsBriefDialogueAndIncludesLeadingPreRoll() {
        val vad = GameAudioSpeechSegmenter()
        var sequence = 0L
        // 100 ms of room tone is kept as pre-roll; 200 ms speech must survive.
        repeat(5) { vad.accept(chunk(sequence++, 0)) }
        repeat(10) { assertTrue(vad.accept(chunk(sequence++, 5_000)).isEmpty()) }
        var emitted: GameAudioSpeechSegmenter.Segment? = null
        repeat(18) {
            val out = vad.accept(chunk(sequence++, 0))
            if (out.isNotEmpty()) emitted = out.single()
        }
        assertTrue(emitted != null)
        assertEquals(16_000, emitted!!.sampleRate)
        assertTrue(emitted!!.durationMs >= 360L)
        assertTrue(emitted!!.samples.size >= 5_760)
    }

    @Test
    fun ignoresContinuousQuietBackground() {
        val vad = GameAudioSpeechSegmenter()
        repeat(100) { assertTrue(vad.accept(chunk(it.toLong(), 100)).isEmpty()) }
        assertEquals(null, vad.flush())
    }

    @Test
    fun flushesSpeechAtSessionEnd() {
        val vad = GameAudioSpeechSegmenter()
        repeat(10) { vad.accept(chunk(it.toLong(), 6_000)) }
        val segment = vad.flush()
        assertTrue(segment != null)
        assertEquals(200L, segment!!.durationMs)
        assertEquals(null, vad.flush())
    }

    @Test
    fun splitsContinuousSpeechBeforeWhisperWindowLimit() {
        val vad = GameAudioSpeechSegmenter(maxSegmentMs = 1_000L)
        var sequence = 0L
        var first: GameAudioSpeechSegmenter.Segment? = null
        repeat(10) {
            val emitted = vad.accept(chunk(sequence++, 6_000, durationMs = 100))
            if (emitted.isNotEmpty()) first = emitted.single()
        }

        assertTrue(first != null)
        assertEquals(1_000L, first!!.durationMs)

        repeat(3) {
            assertTrue(vad.accept(chunk(sequence++, 6_000, durationMs = 100)).isEmpty())
        }
        val remainder = vad.flush()
        assertTrue(remainder != null)
        assertEquals(300L, remainder!!.durationMs)
    }

    @Test
    fun changesInAudioFormatFinishPreviousSegment() {
        val vad = GameAudioSpeechSegmenter()
        repeat(10) { vad.accept(chunk(it.toLong(), 6_000)) }
        val next = GameAudioPcmQueue.Chunk(10, 24_000, 1, ShortArray(480) { 0 })
        val emitted = vad.accept(next)
        assertEquals(1, emitted.size)
        assertEquals(16_000, emitted.single().sampleRate)
        assertFalse(vad.flush() != null)
    }
}
