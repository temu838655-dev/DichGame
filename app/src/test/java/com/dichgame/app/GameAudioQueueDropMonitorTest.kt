package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioQueueDropMonitorTest {
    @Test fun warnsWhenQueueFirstDropsAudio() {
        val monitor = GameAudioQueueDropMonitor()
        assertFalse(monitor.shouldWarn(0L, 100L))
        assertTrue(monitor.shouldWarn(1L, 101L))
    }

    @Test fun throttlesWarningsButRetainsUnreportedDrops() {
        val monitor = GameAudioQueueDropMonitor()
        assertTrue(monitor.shouldWarn(1L, 1_000L))
        assertFalse(monitor.shouldWarn(2L, 2_000L))
        assertFalse(monitor.shouldWarn(5L, 5_999L))
        assertTrue(monitor.shouldWarn(5L, 6_000L))
        assertFalse(monitor.shouldWarn(5L, 6_001L))
    }

    @Test fun doesNotWarnForUnchangedOrDecreasingCounter() {
        val monitor = GameAudioQueueDropMonitor()
        assertFalse(monitor.shouldWarn(4L, 1_000L))
        assertTrue(monitor.shouldWarn(5L, 1_001L))
        assertFalse(monitor.shouldWarn(3L, 7_000L))
    }

    @Test fun backwardsClockDoesNotReleaseThrottleEarly() {
        val monitor = GameAudioQueueDropMonitor()
        assertTrue(monitor.shouldWarn(1L, 5_000L))
        assertFalse(monitor.shouldWarn(2L, 4_999L))
        assertTrue(monitor.shouldWarn(2L, 10_000L))
    }

    @Test fun zeroIntervalAllowsEveryNewDropBatchToWarn() {
        val monitor = GameAudioQueueDropMonitor(warningIntervalMs = 0L)
        assertTrue(monitor.shouldWarn(1L, 10L))
        assertTrue(monitor.shouldWarn(2L, 10L))
    }
}
