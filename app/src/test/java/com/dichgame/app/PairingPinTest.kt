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

    @Test fun decodesValidHexAndRejectsCorruptStoredKeys() {
        val decoded = PsStore.parseHex("00aF19")
        assertEquals(listOf<Byte>(0, 175.toByte(), 25), decoded?.toList())
        assertNull(PsStore.parseHex("abc"))
        assertNull(PsStore.parseHex("0g"))
    }

    @Test fun acceptsEmptyHexOnlyForOptionalLegacyMac() {
        assertEquals(emptyList<Byte>(), PsStore.parseHex("")?.toList())
    }

    @Test fun rejectsNonAsciiDigitsAndOtherCharacters() {
        assertNull(parsePairingPin("１２３４５６７８"))
        assertNull(parsePairingPin("1234 678"))
        assertNull(parsePairingPin("1234abcd"))
        assertNull(parsePairingPin("-1234567"))
    }
}
