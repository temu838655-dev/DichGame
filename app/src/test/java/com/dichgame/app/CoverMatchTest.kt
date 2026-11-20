package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMatchTest {
    private fun same(a: String, b: String) = CoverMatch.same(CoverMatch.norm(a), CoverMatch.norm(b))

    @Test fun exactIgnoresPunctuationAndCase() {
        assertTrue(same("Assassin's Creed Mirage", "assassins creed mirage"))
        assertTrue(same("Armored Core VI: Fires of Rubicon", "Armored Core VI Fires of Rubicon"))
        assertTrue(same("The Last of Us Part II", "Last of Us Part II"))
        assertTrue(same("Marvel's Spider-Man™", "Marvels Spider-Man"))
    }

    @Test fun editionSuffixIsSameGame() {
        assertTrue(same("Metro Exodus GOTY", "Metro Exodus"))
        assertTrue(same("Doom Eternal", "DOOM Eternal"))
    }

    @Test fun otherPartOfSeriesIsNotSameGame() {
        assertFalse(same("Alan Wake", "Alan Wake 2"))
        assertFalse(same("Resident Evil 4", "Resident Evil 3"))
        assertFalse(same("Final Fantasy VII", "Final Fantasy VII Rebirth"))
        assertFalse(same("Mass Effect", "Mass Effect 3"))
        assertFalse(same("God of War", "God of War II"))
        assertFalse(same("Assassin's Creed", "Assassin's Creed Mirage"))
        assertFalse(same("Hades", "Hades II"))
        assertFalse(same("Dark Souls", "Dark Souls III"))
    }

    @Test fun emptyNeverMatches() {
        assertFalse(CoverMatch.same("", "alanwake"))
        assertFalse(CoverMatch.same("alanwake", ""))
    }

    @Test fun wikipediaSuffixIsStripped() {
        assertEquals("Alan Wake 2", CoverMatch.stripGameSuffix("Alan Wake 2 (video game)"))
        assertEquals("Hades", CoverMatch.stripGameSuffix("Hades (2020 video game)"))
        assertEquals("Doom", CoverMatch.stripGameSuffix("Doom (game)"))
        assertEquals("Doom (franchise)", CoverMatch.stripGameSuffix("Doom (franchise)"))
        assertEquals("Animal Crossing: New Horizons", CoverMatch.stripGameSuffix("Animal Crossing: New Horizons"))
    }

    @Test fun flatImageIsDetected() {
        assertTrue(CoverMatch.isFlat(IntArray(500) { 75 }))
        assertTrue(CoverMatch.isFlat(IntArray(500) { if (it == 0) 77 else 75 }))
        assertTrue(CoverMatch.isFlat(IntArray(0)))
        // bìa đen có ít chữ trắng vẫn là ảnh thật
        assertFalse(CoverMatch.isFlat(IntArray(1000) { if (it % 20 == 0) 255 else 0 }))
        assertFalse(CoverMatch.isFlat(IntArray(500) { (it * 7) % 256 }))
    }
}
