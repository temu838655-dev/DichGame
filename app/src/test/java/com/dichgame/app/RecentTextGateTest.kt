package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentTextGateTest {
    @Test fun nearDuplicateIsSuppressedWithinShortWindow() {
        val gate = RecentTextGate()
        assertTrue(gate.shouldAccept("I can't believe it.", 1000))
        assertFalse(gate.shouldAccept("I cant believe it!", 2000))
        assertTrue(gate.shouldAccept("Let's go to the castle.", 2200))
    }

    @Test fun sameLineCanBeAcceptedAgainAfterWindowOrReset() {
        val gate = RecentTextGate(windowMs = 3000)
        assertTrue(gate.shouldAccept("Open the door now", 1000))
        assertFalse(gate.shouldAccept("Open the door now!", 2500))
        assertTrue(gate.shouldAccept("Open the door now!", 5000))
        gate.reset()
        assertTrue(gate.shouldAccept("Open the door now!", 5100))
    }

    @Test fun blankOcrNeverPasses() {
        assertFalse(RecentTextGate().shouldAccept("  | _  ", 1))
    }
}
