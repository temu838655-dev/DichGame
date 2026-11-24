package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AiGlossaryPromptTest {
    @Test fun selectsRelevantTermsIgnoringCase() {
        assertEquals(listOf("Sword" to "Kiếm"),
            Ai.relevantGlossary("Shield=Khiên\nSword=Kiếm\nPotion=Thuốc", "Equip SWORD"))
    }

    @Test fun ignoresInvalidAndDuplicateEntries() {
        assertEquals(listOf("HP" to "Máu"),
            Ai.relevantGlossary("=bad\nHP=\nHP=Máu\nHP=Máu\ninvalid", "HP 100"))
    }

    @Test fun findsRelevantTermBeyondFirstForty() {
        val glossary = (1..50).joinToString("\n") { "unused$it=other" } + "\nQuest=Nhiệm vụ"
        assertEquals(listOf("Quest" to "Nhiệm vụ"), Ai.relevantGlossary(glossary, "New quest"))
    }
}
