package com.dichgame.app

import com.metallic.chiaki.lib.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerDpadTest {
    @Test fun digitalDpadSurvivesNeutralHatAxisEvents() {
        val left = ControllerState.BUTTON_DPAD_LEFT
        assertEquals(left, ControllerDpad.combine(0u, left, 0u))
    }

    @Test fun hatAndDigitalDpadInputsAreCombined() {
        val left = ControllerState.BUTTON_DPAD_LEFT
        val up = ControllerState.BUTTON_DPAD_UP
        assertEquals(left or up, ControllerDpad.combine(0u, left, up))
    }

    @Test fun unrelatedButtonsArePreserved() {
        val cross = ControllerState.BUTTON_CROSS
        val right = ControllerState.BUTTON_DPAD_RIGHT
        assertEquals(cross or right, ControllerDpad.combine(cross, 0u, right))
    }

    @Test fun releasedDigitalDirectionIsRemovedWithoutClearingHatDirection() {
        val down = ControllerState.BUTTON_DPAD_DOWN
        assertEquals(down, ControllerDpad.combine(down, 0u, down))
    }
}
