package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GameAudioSpeechPipelineTest {
    @Test
    fun flushesSpeechAtDroppedPcmBoundaryInsteadOfJoiningAcrossGap() {
        val pipeline = GameAudioSpeechPipeline()
        val frame = ShortArray(320) { 6_000 } // 20 ms at 16 kHz, mono.

        repeat(6) { index ->
            assertEquals(emptyList<GameAudioSpeechSegmenter.Segment>(),
                pipeline.accept(GameAudioPcmQueue.Chunk(index.toLong(), 16_000, 1, frame)))
        }

        // Sequences 6 and 7 were dropped by the bounded queue.
        val beforeGap = pipeline.accept(GameAudioPcmQueue.Chunk(8L, 16_000, 1, frame))
        assertEquals(1, beforeGap.size)
        assertEquals(120L, beforeGap.single().durationMs)
        assertEquals(6 * frame.size, beforeGap.single().samples.size)

        repeat(5) { offset ->
            assertEquals(emptyList<GameAudioSpeechSegmenter.Segment>(),
                pipeline.accept(GameAudioPcmQueue.Chunk(9L + offset, 16_000, 1, frame)))
        }

        val afterGap = pipeline.flush()
        assertNotNull(afterGap)
        assertEquals(120L, afterGap!!.durationMs)
        assertEquals(6 * frame.size, afterGap.samples.size)
    }
}
