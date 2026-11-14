package com.dichgame.app

/** Kết hợp D-pad từ phím số và HAT axis mà không làm mất các nút khác. */
internal object ControllerDpad {
    private val mask = ControllerState.BUTTON_DPAD_LEFT or ControllerState.BUTTON_DPAD_RIGHT or
        ControllerState.BUTTON_DPAD_UP or ControllerState.BUTTON_DPAD_DOWN

    fun combine(buttons: UInt, keyButtons: UInt, hatButtons: UInt): UInt =
        (buttons and mask.inv()) or (keyButtons and mask) or (hatButtons and mask)
}
