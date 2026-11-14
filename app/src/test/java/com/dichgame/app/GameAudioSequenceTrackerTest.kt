package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioSequenceTrackerTest {
    @Test fun acceptsContiguousPcmFrames() {
        val tracker = GameAudioSequenceTracker()
        assertFalse(tracker.hasGap(10))
        assertFalse(tracker.hasGap(11))
        assertFalse(tracker.hasGap(12))
    }

    @Test fun detectsDroppedPcmFramesAndResynchronizes() {
        val tracker = GameAudioSequenceTracker()
        assertFalse(tracker.hasGap(4))
        assertTrue(tracker.hasGap(7))
        assertFalse(tracker.hasGap(8))
    }

    @Test fun resetStartsANewSequenceWithoutReportingGap() {
        val tracker = GameAudioSequenceTracker()
        assertFalse(tracker.hasGap(100))
        tracker.reset()
        assertFalse(tracker.hasGap(2))
    }
}
