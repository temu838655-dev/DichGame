package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalogAxisTest {
    @Test fun valuesInsideDeadZoneBecomeZero() {
        assertEquals(0f, AnalogAxis.normalize(0f, 0.12f), 0.0001f)
        assertEquals(0f, AnalogAxis.normalize(0.12f, 0.12f), 0.0001f)
        assertEquals(0f, AnalogAxis.normalize(-0.08f, 0.12f), 0.0001f)
    }

    @Test fun valuesOutsideDeadZoneAreRescaledAndKeepDirection() {
        assertEquals(0.5f, AnalogAxis.normalize(0.55f, 0.1f), 0.0001f)
        assertEquals(-0.5f, AnalogAxis.normalize(-0.55f, 0.1f), 0.0001f)
        assertEquals(1f, AnalogAxis.normalize(1f, 0.1f), 0.0001f)
        assertEquals(-1f, AnalogAxis.normalize(-1f, 0.1f), 0.0001f)
    }

    @Test fun valuesAndDeadZoneAreClampedSafely() {
        assertEquals(1f, AnalogAxis.normalize(2f, 0f), 0.0001f)
        assertEquals(-1f, AnalogAxis.normalize(-2f, 0f), 0.0001f)
        assertEquals(0f, AnalogAxis.normalize(0.9f, 1f), 0.0001f)
        assertEquals(0.5f, AnalogAxis.normalize(0.55f, Float.NaN), 0.0001f)
    }
}
