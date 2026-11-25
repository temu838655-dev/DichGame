package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class RemotePlayScopeTest {
    @Test fun onlyConsoleRemotePlayAppsAreListed() {
        assertEquals(setOf(Plat.PS5, Plat.PS4, Plat.XBOX), RemotePlay.APPS.flatMap { it.plats }.toSet())
        assertTrue(RemotePlay.APPS.none { it.name.contains("Steam", ignoreCase = true) ||
            it.name.contains("Moonlight", ignoreCase = true) ||
            it.name.contains("GeForce", ignoreCase = true) })
    }

    @Test fun unsupportedPlatformDoesNotOfferRemotePlayApps() {
        assertTrue(RemotePlay.ordered(Plat.PC).isEmpty())
        assertTrue(RemotePlay.ordered(Plat.STEAM).isEmpty())
        assertTrue(RemotePlay.ordered(Plat.SWITCH).isEmpty())
        assertTrue(RemotePlay.forPlatform(Plat.PC).isEmpty())
    }

    @Test fun consoleAppsAreFilteredCorrectly() {
        assertEquals(setOf("PS Remote Play", "Chiaki"), RemotePlay.forPlatform(Plat.PS5).map { it.name }.toSet())
        assertEquals(setOf("PS Remote Play", "Chiaki"), RemotePlay.forPlatform(Plat.PS4).map { it.name }.toSet())
        assertEquals(listOf("Xbox"), RemotePlay.forPlatform(Plat.XBOX).map { it.name })
    }

    @Test fun unsupportedPlatformGuideExplainsPcIsOnlyADataSource() {
        val guide = RemotePlay.guide(Plat.PC)
        assertTrue(guide.contains("PS5, PS4 và Xbox"))
        assertTrue(guide.contains("nguồn dữ liệu"))
    }
}
