package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrStabilityFilterTest {
    @Test fun requiresBothRepeatedFramesAndElapsedTime() {
        val filter = OcrStabilityFilter()

        assertFalse(filter.observe("hello", requiredFrames = 2, nowMs = 1_000L, minimumStableMs = 250L))
        assertFalse(filter.observe("hello", requiredFrames = 2, nowMs = 1_100L, minimumStableMs = 250L))
        assertTrue(filter.observe("hello", requiredFrames = 2, nowMs = 1_260L, minimumStableMs = 250L))
    }

    @Test fun changedTextStartsANewConfirmationWindow() {
        val filter = OcrStabilityFilter()

        assertFalse(filter.observe("I do not agree", 2, 1_000L, 200L))
        assertFalse(filter.observe("I do agree", 2, 1_500L, 200L))
        assertFalse(filter.observe("I do agree", 2, 1_550L, 200L))
        assertTrue(filter.observe("I do agree", 2, 1_710L, 200L))
    }

    @Test fun shortTextCanRequireThreeFrames() {
        val filter = OcrStabilityFilter()

        assertFalse(filter.observe("No", 3, 1_000L, 0L))
        assertFalse(filter.observe("No", 3, 1_010L, 0L))
        assertTrue(filter.observe("No", 3, 1_020L, 0L))
        assertEquals(3, filter.frameCount)
    }

    @Test fun resetClearsCandidateAndTiming() {
        val filter = OcrStabilityFilter()

        filter.observe("Continue", 2, 5_000L, 100L)
        filter.reset()

        assertEquals("", filter.candidateSignature)
        assertEquals(0, filter.frameCount)
        assertFalse(filter.observe("Continue", 2, 5_050L, 100L))
        assertTrue(filter.observe("Continue", 2, 5_160L, 100L))
    }

    @Test fun blankSignatureNeverPasses() {
        val filter = OcrStabilityFilter()

        assertFalse(filter.observe(" ", 1, 1_000L, 0L))
        assertEquals(0, filter.frameCount)
    }
}
