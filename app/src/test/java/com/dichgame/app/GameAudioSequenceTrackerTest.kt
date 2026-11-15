package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioSequenceTrackerTest {
    @Test fun acceptsContiguousPcmFrames() {
        val tracker = GameAudioSequenceTracker()
        assertFalse(tracker.hasGap(10L))
        assertFalse(tracker.hasGap(11L))
        assertFalse(tracker.hasGap(12L))
    }

    @Test fun detectsDroppedFrames() {
        val tracker = GameAudioSequenceTracker()
        assertFalse(tracker.hasGap(4L))
        assertTrue(tracker.hasGap(7L))
        assertFalse(tracker.hasGap(8L))
    }

    @Test fun detectsQueueClearOrOutOfOrderFrameAsDiscontinuity() {
        val tracker = GameAudioSequenceTracker()
        assertFalse(tracker.hasGap(20L))
        assertTrue(tracker.hasGap(2L))
    }
}
