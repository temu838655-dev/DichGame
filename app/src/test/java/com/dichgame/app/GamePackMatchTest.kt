package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamePackMatchTest {
    @Test fun libraryHighlightsOnlyActuallySelectedGame() {
        assertTrue(GamePackMatch.selected("Grand Theft Auto VI", "grand theft auto vi"))
        assertFalse(GamePackMatch.selected("Grand Theft Auto VI", "Grand Theft Auto V"))
        assertFalse(GamePackMatch.selected("The Last of Us Part II", "The Last of Us Part I"))
        assertFalse(GamePackMatch.selected("Resident Evil 4", "Resident Evil 2"))
    }

    @Test fun gameTitleContainingCommaIsNotCutOff() {
        val title = "Mafia III, Definitive Edition"
        assertTrue(GamePackMatch.title(
            "$title, hành động", title, title, "Mặc định") == title)
        assertTrue(GamePackMatch.title(
            "$title, hành động", title, "", "Mặc định") == title)
        assertTrue(GamePackMatch.title(
            "Grand Theft Auto VI, thế giới mở", "Grand Theft Auto VI", "", "Mặc định") ==
                "Grand Theft Auto VI")
    }

    @Test fun versionedPacksDoNotMatchOtherInstallments() {
        assertFalse(GamePackMatch.contains("Grand Theft Auto VI", "grand theft auto v"))
        assertTrue(GamePackMatch.contains("Grand Theft Auto VI", "grand theft auto vi"))
        assertFalse(GamePackMatch.contains("Final Fantasy VIII", "final fantasy vii"))
        assertFalse(VietHoa.castFor("Final Fantasy VIII").any { it.startsWith("Cloud") })
        assertTrue(VietHoa.castFor("Final Fantasy VII Rebirth").any { it.startsWith("Cloud") })
        assertFalse(GamePackMatch.contains("Resident Evil 4", "resident evil 2"))
        assertTrue(GamePackMatch.contains("Final Fantasy VII Rebirth", "final fantasy vii"))
    }

    @Test fun broaderSeriesPackagesStillMatch() {
        assertTrue(GamePackMatch.contains("Grand Theft Auto VI", "grand theft auto"))
        assertTrue(GamePackMatch.contains("Marvel's Spider-Man 2", "spider-man"))
        assertTrue(GamePackMatch.contains("Black Myth: Wukong", "wukong"))
        assertFalse(GamePackMatch.contains("Controller Settings", "control"))
    }

    @Test fun differentGamesHaveDifferentCharacterCards() {
        val gta5 = VietHoa.castFor("Grand Theft Auto V").joinToString("|")
        val gta6 = VietHoa.castFor("Grand Theft Auto VI").joinToString("|")
        assertTrue(gta5.contains("Michael"))
        assertFalse(gta5.contains("Lucia"))
        assertTrue(gta6.contains("Lucia"))
        assertFalse(gta6.contains("Michael"))

        val re2 = VietHoa.castFor("Resident Evil 2").joinToString("|")
        val re4 = VietHoa.castFor("Resident Evil 4").joinToString("|")
        val village = VietHoa.castFor("Resident Evil Village").joinToString("|")
        assertTrue(re2.contains("Claire"))
        assertFalse(re2.contains("Ashley"))
        assertTrue(re4.contains("Ashley"))
        assertFalse(re4.contains("Lady Dimitrescu"))
        assertTrue(village.contains("Lady Dimitrescu"))
        assertFalse(village.contains("Leon"))

        val part1 = VietHoa.castFor("The Last of Us Part I").joinToString("|")
        val part2 = VietHoa.castFor("The Last of Us Part II").joinToString("|")
        assertTrue(part1.contains("Tess"))
        assertFalse(part1.contains("Abby"))
        assertTrue(part2.contains("Abby"))
        assertFalse(part2.contains("Tess"))
    }
}
