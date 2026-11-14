package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class GameAudioPcmQueueTest {
    @Test fun copiesSamplesBeforeReturning() {
        val queue = GameAudioPcmQueue(2)
        val input = shortArrayOf(1, 2, 3, 4)
        assertTrue(queue.offer(input, 48_000, 2))
        input[0] = 99

        val chunk = queue.poll()!!
        assertArrayEquals(shortArrayOf(1, 2, 3, 4), chunk.samples)
        assertEquals(2, chunk.frameCount)
        assertEquals(48_000, chunk.sampleRate)
        assertEquals(2, chunk.channels)
    }

    @Test fun dropsOldestChunkWhenConsumerFallsBehind() {
        val queue = GameAudioPcmQueue(2)
        assertTrue(queue.offer(shortArrayOf(1, 2), 48_000, 1))
        assertTrue(queue.offer(shortArrayOf(3, 4), 48_000, 1))
        assertTrue(queue.offer(shortArrayOf(5, 6), 48_000, 1))

        assertEquals(1L, queue.droppedChunks())
        assertArrayEquals(shortArrayOf(3, 4), queue.poll()!!.samples)
        assertArrayEquals(shortArrayOf(5, 6), queue.poll()!!.samples)
        assertNull(queue.poll())
    }

    @Test fun sequenceTrackerDetectsAudioGapAfterConsumerHasStarted() {
        val queue = GameAudioPcmQueue(2)
        val tracker = GameAudioSequenceTracker()

        assertTrue(queue.offer(shortArrayOf(0, 0), 48_000, 1))
        val first = queue.poll()!!
        assertFalse(tracker.hasGap(first.sequence))

        assertTrue(queue.offer(shortArrayOf(1, 1), 48_000, 1))
        assertTrue(queue.offer(shortArrayOf(2, 2), 48_000, 1))
        assertTrue(queue.offer(shortArrayOf(3, 3), 48_000, 1))

        // The queue discarded sequence 1 to preserve the newest live audio.
        val afterGap = queue.poll()!!
        assertEquals(2L, afterGap.sequence)
        assertTrue(tracker.hasGap(afterGap.sequence))

        val next = queue.poll()!!
        assertEquals(3L, next.sequence)
        assertFalse(tracker.hasGap(next.sequence))
    }

    @Test fun rejectsInvalidFramesAndClosedQueue() {
        val queue = GameAudioPcmQueue(2)
        assertFalse(queue.offer(shortArrayOf(1), 48_000, 2))
        assertFalse(queue.offer(shortArrayOf(1, 2), 0, 1))
        assertFalse(queue.offer(shortArrayOf(1, 2), 48_000, 3))
        queue.close()
        assertFalse(queue.offer(shortArrayOf(1, 2), 48_000, 1))
        assertNull(queue.poll())
    }

    @Test fun clearRemovesStaleAudio() {
        val queue = GameAudioPcmQueue(2)
        assertTrue(queue.offer(shortArrayOf(1, 2), 48_000, 1))
        queue.clear()
        assertNull(queue.poll())
        assertEquals(0L, queue.droppedChunks())
    }
}
