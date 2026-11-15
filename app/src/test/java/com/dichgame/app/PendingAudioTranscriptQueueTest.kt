package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAudioTranscriptQueueTest {
    @Test
    fun preservesFifoOrder() {
        val queue = PendingAudioTranscriptQueue(capacity = 3, maxAgeMs = 8_000L)
        assertTrue(queue.offer("First line", 100L))
        assertTrue(queue.offer("Second line", 110L))
        assertTrue(queue.offer("Third line", 120L))

        assertEquals("First line", queue.pollFresh(130L)?.text)
        assertEquals("Second line", queue.pollFresh(130L)?.text)
        assertEquals("Third line", queue.pollFresh(130L)?.text)
        assertNull(queue.pollFresh(130L))
    }

    @Test
    fun rejectsNewestWhenFullInsteadOfEvictingEarlierDialogue() {
        val queue = PendingAudioTranscriptQueue(capacity = 2)
        assertTrue(queue.offer("First", 100L))
        assertTrue(queue.offer("Second", 110L))

        assertFalse(queue.offer("Third", 120L))
        assertEquals("First", queue.pollFresh(130L)?.text)
        assertEquals("Second", queue.pollFresh(130L)?.text)
    }

    @Test
    fun rejectsDuplicateLinesAlreadyWaiting() {
        val queue = PendingAudioTranscriptQueue()
        assertTrue(queue.offer("Open the door!", 100L))
        assertFalse(queue.offer("open the door", 110L))
        assertEquals("Open the door!", queue.pollFresh(120L)?.text)
    }

    @Test
    fun discardsExpiredEntriesAndReturnsNextFreshLine() {
        val queue = PendingAudioTranscriptQueue(capacity = 3, maxAgeMs = 1_000L)
        assertTrue(queue.offer("Expired", 100L))
        assertTrue(queue.offer("Still fresh", 500L))

        assertEquals("Still fresh", queue.pollFresh(1_200L)?.text)
        assertNull(queue.pollFresh(1_200L))
    }

    @Test
    fun discardsEntriesWhenMonotonicTimestampIsInTheFuture() {
        val queue = PendingAudioTranscriptQueue()
        assertTrue(queue.offer("Invalid future entry", 2_000L))
        assertNull(queue.pollFresh(1_999L))
    }

    @Test
    fun clearRemovesAllPendingLines() {
        val queue = PendingAudioTranscriptQueue()
        assertTrue(queue.offer("One", 100L))
        queue.clear()
        assertFalse(queue.isNotEmpty())
        assertNull(queue.pollFresh(100L))
    }
}
