package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrQualityTest {
    @Test fun preservesExistingChoiceAndChecksShortCaptions() {
        assertEquals(OcrQuality.CAREFUL, OcrQuality.read(null, true))
        assertEquals(OcrQuality.FAST, OcrQuality.read(null, false))
        assertEquals(OcrQuality.BALANCED, OcrQuality.read("BALANCED", false))
        assertEquals(2, OcrQuality.BALANCED.frames(11))
        assertEquals(1, OcrQuality.BALANCED.frames(12))
        assertEquals(2, OcrQuality.CAREFUL.frames(20))
    }
}
