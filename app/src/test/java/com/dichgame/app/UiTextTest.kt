package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTextTest {
    private fun v(s: String, c: Float = 1f) = UiText.verdict(s, c)

    @Test fun realUiStringsAreTranslated() {
        for (s in listOf("Inventory", "Skill Tree", "Iron Sword", "Increases attack by 10%.", "Level 12", "Press X to continue", "Heal", "Map", "New Game", "Quest:", "On/Off", "Weapons"))
            assertEquals(s, UiText.Verdict.TRANSLATE, v(s))
    }

    @Test fun standaloneShortUiNamesNeedHighConfidenceAndRealLetters() {
        assertTrue(UiText.isStandaloneAutoCandidate("Map", 0.80f))
        assertTrue(UiText.isStandaloneAutoCandidate("Heal", 0.90f))
        assertTrue(UiText.isStandaloneAutoCandidate("Inventory", 0.75f))
        assertTrue(!UiText.isStandaloneAutoCandidate("Map", 0.60f))
        assertTrue(!UiText.isStandaloneAutoCandidate("A", 0.99f))
        assertTrue(!UiText.isStandaloneAutoCandidate("HP", 0.99f))
        assertTrue(!UiText.isStandaloneAutoCandidate("ATK 42", 0.99f))
        assertTrue(!UiText.isStandaloneAutoCandidate("item_sword_01", 0.99f))
        assertTrue(!UiText.isStandaloneAutoCandidate("100/200", 0.99f))
    }

    @Test fun numbersAndSymbolsAreSkipped() {
        assertEquals(UiText.Verdict.NO_LETTERS, v("100/200"))
        assertEquals(UiText.Verdict.NO_LETTERS, v("+15%"))
        assertEquals(UiText.Verdict.NO_LETTERS, v("--"))
        assertEquals(UiText.Verdict.MOSTLY_SYMBOLS, v("HP 100/200 +5"))
        assertEquals(UiText.Verdict.EMPTY, v("   "))
    }

    @Test fun idsCodesFilesAndUrlsAreSkipped() {
        for (s in listOf("item_sword_01", "UI_BTN_OK", "ID4521", "itemSword", "ui/menu/back", "icon.png", "https://example.com", "C:\\Games\\a"))
            assertEquals(s, UiText.Verdict.ID_LIKE, v(s))
    }

    @Test fun statAbbreviationsAreKept() {
        for (s in listOf("HP", "MP", "XP", "ATK", "DEF", "FPS", "NPC", "DLC", "L1", "R2", "ATK 42", "ATK 42 DEF 10"))
            assertEquals(s, UiText.Verdict.ABBREVIATION, v(s))
        // có từ thường lẫn vào thì vẫn là chữ cần dịch
        assertEquals(UiText.Verdict.TRANSLATE, v("Level 12"))
        assertEquals(UiText.Verdict.TRANSLATE, v("ATK up"))
    }

    @Test fun alreadyVietnameseIsSkipped() {
        assertEquals(UiText.Verdict.VIETNAMESE, v("Kiếm Sắt"))
        assertEquals(UiText.Verdict.VIETNAMESE, v("Tăng sức tấn công"))
        // tiếng Pháp có dấu nhưng không phải tiếng Việt
        assertEquals(UiText.Verdict.TRANSLATE, v("Épée de fer"))
    }

    @Test fun lowConfidenceAndSingleLettersAreSkipped() {
        assertEquals(UiText.Verdict.LOW_CONFIDENCE, v("Inventory", 0.3f))
        assertEquals(UiText.Verdict.TOO_SHORT, v("A"))
        assertEquals(UiText.Verdict.TOO_LONG, v("word ".repeat(100)))
    }

    @Test fun placeholdersAreProtectedAndRestored() {
        val p = UiText.protect("Deal {0} damage to [ENEMY_NAME] with <color=#ff0000>fire</color> %s")
        assertEquals(5, p.tokens.size)
        assertTrue(p.text.contains("⟦1⟧") && p.text.contains("⟦5⟧"))
        assertTrue(!p.text.contains("{0}") && !p.text.contains("[ENEMY_NAME]") && !p.text.contains("<color"))
        val vi = "Gây ⟦1⟧ sát thương lên ⟦2⟧ bằng ⟦3⟧lửa⟦4⟧ ⟦5⟧"
        assertEquals("Gây {0} sát thương lên [ENEMY_NAME] bằng <color=#ff0000>lửa</color> %s", UiText.restore(vi, p))
    }

    @Test fun lostOrExtraTokensRejectTheTranslation() {
        val p = UiText.protect("Hello {name}, you have %d items")
        assertNull(UiText.restore("Xin chào ⟦1⟧, bạn có vật phẩm", p))
        assertNull(UiText.restore("Xin chào ⟦1⟧, bạn có ⟦2⟧ ⟦2⟧ vật phẩm", p))
        assertNull(UiText.restore("Xin chào ⟦1⟧, bạn có ⟦2⟧ vật phẩm ⟦3⟧", p))
    }

    @Test fun textWithoutPlaceholdersIsUntouched() {
        val p = UiText.protect("Iron Sword")
        assertEquals("Iron Sword", p.text)
        assertEquals("Kiếm Sắt", UiText.restore("Kiếm Sắt", p))
    }

    @Test fun keyIgnoresCaseAndSpacing() {
        assertEquals(UiText.key("Skill  Tree "), UiText.key("skill tree"))
    }

    @Test fun joinLinesMergesWrappedParagraphs() {
        assertEquals("Increases attack by 10% for 30 seconds.", UiText.joinLines("Increases attack by\n10% for 30\nseconds.", false))
        assertEquals("super-powered strike", UiText.joinLines("super-\npowered strike", false).replace("superpowered", "super-powered"))
        assertEquals("攻撃力が上がる", UiText.joinLines("攻撃力が\n上がる", true))
    }
}
