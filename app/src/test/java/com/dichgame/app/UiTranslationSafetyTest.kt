package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class UiTranslationSafetyTest {
    @Test fun refusesWrongNumbersAndDirections() {
        assertFalse(UiText.safeTranslation("Turn left.", "Rẽ phải.", false, "en"))
        assertFalse(UiText.safeTranslation("You have 250 gold.", "Bạn có 520 vàng.", false, "en"))
        assertTrue(UiText.safeTranslation("Turn left.", "Rẽ trái.", false, "en"))
    }
    @Test fun keepsMarkersAndRejectsUncertainText() {
        assertTrue(UiText.samePlaceholders("Hello {name}", "Chào {name}"))
        assertFalse(UiText.samePlaceholders("Hello {name}", "Chào {player}"))
        assertFalse(UiText.safeTranslation("Continue", "Continue", false, "en"))
        assertFalse(UiText.safeTranslation("Continue", "Tiếp tục", true, "en"))
        assertTrue(UiText.safeTranslation("Continue", "Tiếp tục", false, "en"))
    }
}
