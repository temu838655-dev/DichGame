package com.dichgame.app

import com.metallic.chiaki.lib.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualDpadTest {
    @Test fun ignoresTouchInsideDeadZone() {
        assertEquals(0u, VirtualDpad.direction(2f, 2f, 4f))
    }

    @Test fun mapsCardinalDirections() {
        assertEquals(ControllerState.BUTTON_DPAD_LEFT, VirtualDpad.direction(-10f, 0f, 1f))
        assertEquals(ControllerState.BUTTON_DPAD_RIGHT, VirtualDpad.direction(10f, 0f, 1f))
        assertEquals(ControllerState.BUTTON_DPAD_UP, VirtualDpad.direction(0f, -10f, 1f))
        assertEquals(ControllerState.BUTTON_DPAD_DOWN, VirtualDpad.direction(0f, 10f, 1f))
    }

    @Test fun mapsDiagonalDirectionsTogether() {
        assertEquals(
            ControllerState.BUTTON_DPAD_RIGHT or ControllerState.BUTTON_DPAD_DOWN,
            VirtualDpad.direction(10f, 10f, 1f)
        )
    }

    @Test fun ignoresNonFiniteCoordinatesAndInvalidDeadZone() {
        assertEquals(0u, VirtualDpad.direction(Float.NaN, 1f, 0f))
        assertEquals(0u, VirtualDpad.direction(1f, Float.POSITIVE_INFINITY, 0f))
        assertEquals(0u, VirtualDpad.direction(1f, 1f, Float.NaN))
        assertEquals(0u, VirtualDpad.direction(1f, 1f, -1f))
    }

    @Test fun deadZoneBoundaryDoesNotActivateDirection() {
        assertEquals(0u, VirtualDpad.direction(3f, 4f, 5f))
    }
}
