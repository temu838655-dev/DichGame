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
    @Test fun radialDeadZoneKeepsDirectionAndNormalizesDiagonalMagnitude() {
        val straight = AnalogAxis.normalizePair(0.55f, 0f, 0.1f, 0.1f)
        assertEquals(0.5f, straight.first, 0.0001f)
        assertEquals(0f, straight.second, 0.0001f)

        val diagonal = AnalogAxis.normalizePair(0.7071067f, 0.7071067f, 0.1f, 0.1f)
        assertEquals(0.7071067f, diagonal.first, 0.001f)
        assertEquals(0.7071067f, diagonal.second, 0.001f)
        assertEquals(1f, kotlin.math.sqrt(diagonal.first * diagonal.first + diagonal.second * diagonal.second), 0.001f)
    }

    @Test fun radialDeadZoneRejectsCenterAndClampsInputs() {
        assertEquals(0f to 0f, AnalogAxis.normalizePair(0.05f, -0.05f, 0.1f, 0.1f))
        val clamped = AnalogAxis.normalizePair(2f, 2f, 0f, 0f)
        assertEquals(0.7071f, clamped.first, 0.001f)
        assertEquals(0.7071f, clamped.second, 0.001f)
    }

}
