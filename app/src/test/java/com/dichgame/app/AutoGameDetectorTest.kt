package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class AutoGameDetectorTest {
    private val detector = AutoGameDetector(listOf(
        "Grand Theft Auto V", "Grand Theft Auto VI", "Alan Wake 2", "Control"
    ))

    @Test fun needsTwoConfirmedTitleFrames() {
        assertNull(detector.observe(listOf("Grand Theft Auto VI"), "Alan Wake 2"))
        assertEquals("Grand Theft Auto VI", detector.observe(listOf("Grand Theft Auto VI"), "Alan Wake 2"))
    }

    @Test fun doesNotMistakeSeriesOrSubtitleForGameTitle() {
        assertNull(detector.matchFrame(listOf("Grand Theft Auto VII")))
        assertNull(detector.matchFrame(listOf("I enjoyed playing Grand Theft Auto VI with you.")))
        assertNull(detector.matchFrame(listOf("Control")))
        assertEquals("Grand Theft Auto V", detector.matchFrame(listOf("GTA V")))
        assertEquals("Grand Theft Auto VI", detector.matchFrame(listOf("GTA VI")))
    }

    @Test fun changedCandidateRestartsConfirmation() {
        assertNull(detector.observe(listOf("Alan Wake 2"), ""))
        assertNull(detector.observe(listOf("Grand Theft Auto VI"), ""))
        assertEquals("Grand Theft Auto VI", detector.observe(listOf("GTA VI"), ""))
    }

    @Test fun unknownOrCurrentTitleKeepsProfile() {
        assertNull(detector.observe(listOf("Grand Theft Auto VI"), "Grand Theft Auto VI"))
        assertNull(detector.observe(listOf("some random subtitle"), "Alan Wake 2"))
    }
}
