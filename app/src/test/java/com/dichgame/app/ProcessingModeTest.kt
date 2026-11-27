package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingModeTest {
    @Test fun previousSettingsMapToTheirEffectiveMode() {
        for (speak in listOf(false, true)) {
            assertEquals(ProcessingMode.NARRATE_ONLY, ProcessingMode.fromSettings(true, false, speak))
            assertEquals(ProcessingMode.NARRATE_ONLY, ProcessingMode.fromSettings(true, true, speak))
            assertEquals(ProcessingMode.AUTOMATIC, ProcessingMode.fromSettings(false, true, speak))
        }
        assertEquals(ProcessingMode.TRANSLATE_ONLY, ProcessingMode.fromSettings(false, false, false))
        assertEquals(ProcessingMode.TRANSLATE_AND_SPEAK, ProcessingMode.fromSettings(false, false, true))
    }
}
