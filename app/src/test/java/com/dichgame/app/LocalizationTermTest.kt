package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalizationTermTest {
    @Test fun exactTermDoesNotStandInForAnUntranslatedSentence() {
        val terms = TermMap(listOf("Fire Arrow" to "Tên lửa"))
        assertEquals("Tên lửa", terms.exact(" FIRE   ARROW "))
        assertNull(terms.exact("Equip Fire Arrow"))
        assertNull(terms.exact("Fire Arrows"))
    }
}
