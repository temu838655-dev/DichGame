package com.dichgame.app

import com.metallic.chiaki.lib.VideoResolutionPreset
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
    @Test fun convertsMbpsToChiakiKbpsAndRejectsUnsupportedValues() {
        assertEquals(0, PsStore.bitrateKbps(0))
        assertEquals(2_000, PsStore.bitrateKbps(2))
        assertEquals(3_000, PsStore.bitrateKbps(3))
        assertEquals(15_000, PsStore.bitrateKbps(15))
        assertEquals(30_000, PsStore.bitrateKbps(30))
        assertEquals(0, PsStore.bitrateKbps(4))
        assertEquals(0, PsStore.bitrateKbps(-1))
    }

    @Test fun mapsResolutionChoicesWithoutBreakingSavedIndexes() {
        assertEquals(VideoResolutionPreset.RES_720P, PsStore.resolutionPreset(0))
        assertEquals(VideoResolutionPreset.RES_720P, PsStore.resolutionPreset(1))
        assertEquals(VideoResolutionPreset.RES_1080P, PsStore.resolutionPreset(2))
        assertEquals(VideoResolutionPreset.RES_540P, PsStore.resolutionPreset(3))
        assertEquals(VideoResolutionPreset.RES_360P, PsStore.resolutionPreset(4))
        assertEquals(VideoResolutionPreset.RES_720P, PsStore.resolutionPreset(99))
    }

    @Test fun removingPlayerKeepsTvAndPhoneSelectionsOnSamePlayers() {
        fun player(label: String) = PsPlayer(label, byteArrayOf(1), 0, byteArrayOf(2), byteArrayOf(3), "")
        val console = PsConsole("192.168.1.2", true, "PS5", mutableListOf(
            player("A"), player("B"), player("C")
        ), tvPlayer = 1, phonePlayer = 2)

        assertEquals(true, PsStore.removePlayer(console, 0))
        assertEquals(listOf("B", "C"), console.players.map { it.label })
        assertEquals(0, console.tvPlayer)
        assertEquals(1, console.phonePlayer)
    }

    @Test fun removingSelectedPlayerChoosesNearestRemainingSlot() {
        fun player(label: String) = PsPlayer(label, byteArrayOf(1), 0, byteArrayOf(2), byteArrayOf(3), "")
        val console = PsConsole("192.168.1.2", true, "PS5", mutableListOf(
            player("A"), player("B"), player("C")
        ), tvPlayer = 1, phonePlayer = 1)

        assertEquals(true, PsStore.removePlayer(console, 1))
        assertEquals(listOf("A", "C"), console.players.map { it.label })
        assertEquals(1, console.tvPlayer)
        assertEquals(1, console.phonePlayer)
        assertEquals(false, PsStore.removePlayer(console, 9))
    }

    @Test fun retriesUseBoundedBackoffAndStopAfterFiveAttempts() {
        assertEquals(1_500L, RemotePlayRetryPolicy.delayForAttempt(0))
        assertEquals(3_000L, RemotePlayRetryPolicy.delayForAttempt(1))
        assertEquals(6_000L, RemotePlayRetryPolicy.delayForAttempt(2))
        assertEquals(10_000L, RemotePlayRetryPolicy.delayForAttempt(3))
        assertEquals(15_000L, RemotePlayRetryPolicy.delayForAttempt(4))
        assertNull(RemotePlayRetryPolicy.delayForAttempt(5))
        assertNull(RemotePlayRetryPolicy.delayForAttempt(-1))
        assertEquals(5, RemotePlayRetryPolicy.maxAttempts)
    }

}
