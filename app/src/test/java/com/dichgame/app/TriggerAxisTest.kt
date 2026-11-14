package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerAxisTest {
    @Test fun valuesAreClampedToTriggerRange() {
        assertEquals(0f, TriggerAxis.normalize(-0.5f, 0f), 0.0001f)
        assertEquals(1f, TriggerAxis.normalize(1.5f, 0f), 0.0001f)
    }

    @Test fun driverDeadZoneIsRemovedAndRemainingRangeRescaled() {
        assertEquals(0f, TriggerAxis.normalize(0.1f, 0.1f), 0.0001f)
        assertEquals(0.5f, TriggerAxis.normalize(0.55f, 0.1f), 0.0001f)
        assertEquals(1f, TriggerAxis.normalize(1f, 0.1f), 0.0001f)
    }

    @Test fun invalidValuesCannotCreatePhantomTriggerPresses() {
        assertEquals(0f, TriggerAxis.normalize(Float.NaN, 0f), 0.0001f)
        assertEquals(0f, TriggerAxis.normalize(Float.POSITIVE_INFINITY, 0f), 0.0001f)
        assertEquals(0f, TriggerAxis.normalize(Float.NEGATIVE_INFINITY, 0f), 0.0001f)
        assertEquals(0f, TriggerAxis.normalize(0.5f, Float.NaN), 0.0001f)
        assertEquals(0f, TriggerAxis.normalize(0.5f, Float.POSITIVE_INFINITY), 0.0001f)
    }
}
