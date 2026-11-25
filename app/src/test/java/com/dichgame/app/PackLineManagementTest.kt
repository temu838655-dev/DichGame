package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PackLineManagementTest {
    @Test fun editingImportedLineReplacesSourceAndTranslationWithoutTouchingOtherRows() {
        val existing = listOf("Quest complete" to "Hoàn thành nhiệm vụ", "Old line" to "Dòng cũ")
        val edited = TranslationLineMerge.edit(existing, "Quest complete", "Quest completed", "Nhiệm vụ đã hoàn thành")
        assertEquals(listOf("Old line" to "Dòng cũ", "Quest completed" to "Nhiệm vụ đã hoàn thành"), edited)
    }

    @Test fun editingLineRejectsMissingSourceOrBlankTranslation() {
        val existing = listOf("Quest complete" to "Hoàn thành nhiệm vụ")
        assertEquals(null, TranslationLineMerge.edit(existing, "Missing", "New", "Mới"))
        assertEquals(null, TranslationLineMerge.edit(existing, "Quest complete", "New", "  "))
    }

    @Test fun deletingImportedLineRemovesOnlyExactSource() {
        val existing = listOf("Quest complete" to "Hoàn thành nhiệm vụ", "Quest completed" to "Nhiệm vụ xong")
        assertEquals(listOf("Quest completed" to "Nhiệm vụ xong"), TranslationLineMerge.delete(existing, "Quest complete"))
        assertEquals(null, TranslationLineMerge.delete(existing, "not found"))
    }
}
