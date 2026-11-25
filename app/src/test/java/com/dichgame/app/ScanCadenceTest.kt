package com.dichgame.app
import org.junit.Assert.*
import org.junit.Test
class ScanCadenceTest {
    @Test fun preservesConfirmationAndFastMode() {
        assertEquals(150L, ScanCadence.delay(150, 8, true, false))
        assertEquals(150L, ScanCadence.delay(150, 8, false, true))
        assertEquals(150L, ScanCadence.delay(150, 2, false, false))
    }
    @Test fun boundsIdleDelay() {
        assertEquals(310L, ScanCadence.delay(150, 12, false, false))
        assertEquals(500L, ScanCadence.delay(450, 12, false, false))
        assertEquals(900L, ScanCadence.delay(900, 12, false, false))
    }
}
