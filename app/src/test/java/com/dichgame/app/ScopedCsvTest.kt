package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopedCsvTest {
    @Test fun roundTripPreservesCommasQuotesAndGame() {
        val lines = listOf("Wait, Lucia!" to "Đợi đã, Lucia!", "He said \"go\"." to "Anh ấy nói \"đi\".")
        val csv = PackText.formatScopedCsv("Grand Theft Auto VI", lines)
        val parsed = requireNotNull(PackText.parseScopedCsv(csv))
        assertEquals("Grand Theft Auto VI", parsed.name)
        assertEquals(lines, parsed.lines)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mixedGamesRejected() {
        PackText.parseScopedCsv("source,vi,game\nHello,Xin chào,GTA V\nBye,Tạm biệt,GTA VI")
    }

    @Test fun genericCsvStillUsesExistingImporter() {
        assertNull(PackText.parseScopedCsv("en,vi\nHello,Xin chào"))
    }
}
