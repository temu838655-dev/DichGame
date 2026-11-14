package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPinTest {
    @Test fun acceptsExactlyEightAsciiDigits() {
        assertEquals(12345678, parsePairingPin("12345678"))
        assertEquals(00000001, parsePairingPin("00000001"))
    }

    @Test fun rejectsWrongLength() {
        assertNull(parsePairingPin(""))
        assertNull(parsePairingPin("1234567"))
        assertNull(parsePairingPin("123456789"))
    }

    @Test fun rejectsNonAsciiDigitsAndOtherCharacters() {
        assertNull(parsePairingPin("１２３４５６７８"))
        assertNull(parsePairingPin("1234 678"))
        assertNull(parsePairingPin("1234abcd"))
        assertNull(parsePairingPin("-1234567"))
    }
}
