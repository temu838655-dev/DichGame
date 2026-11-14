package com.dichgame.app

import com.metallic.chiaki.lib.ControllerState
import kotlin.math.abs
import kotlin.math.hypot

/** Chuyển độ lệch ngón tay thành hướng D-pad, độc lập UI để có thể kiểm thử. */
internal object VirtualDpad {
    fun direction(dx: Float, dy: Float, deadZone: Float): UInt {
        if (!dx.isFinite() || !dy.isFinite() || !deadZone.isFinite() || deadZone < 0f) return 0u
        val distance = hypot(dx, dy)
        if (!distance.isFinite() || distance <= deadZone || distance == 0f) return 0u

        var result = 0u
        if (abs(dx) > distance * 0.38f) {
            result = result or if (dx > 0f) ControllerState.BUTTON_DPAD_RIGHT else ControllerState.BUTTON_DPAD_LEFT
        }
        if (abs(dy) > distance * 0.38f) {
            result = result or if (dy > 0f) ControllerState.BUTTON_DPAD_DOWN else ControllerState.BUTTON_DPAD_UP
        }
        return result
    }
}
