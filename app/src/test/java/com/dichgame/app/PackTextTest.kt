package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.charset.Charset

/** Kiểm tra nhập file Việt hoá ở nhiều dạng (chạy trên máy tính, không cần Android). */
class PackTextTest {
    @Test fun gtaSixCastDoesNotReuseGtaFiveCharacters() {
        for (game in listOf("Grand Theft Auto VI", "GTA VI", "GTA 6")) {
            val characters = VietHoaPlus.castFor(game)
            assertTrue("Missing Lucia in $game", characters.any { it.startsWith("Lucia Caminos/Lucia = nữ") })
            assertTrue("Missing Jason in $game", characters.any { it.startsWith("Jason Duval/Jason = nam") })
            assertTrue("Wrong GTA V character in $game", characters.none { it.startsWith("Michael =") })
        }
        for (game in listOf("Grand Theft Auto V", "GTA V", "GTA 5")) {
            val characters = VietHoaPlus.castFor(game)
            assertTrue("Missing GTA V character in $game", characters.any { it.startsWith("Michael = nam") })
            assertTrue("Wrong GTA VI character in $game", characters.none { it.startsWith("Lucia Caminos/") })
        }
    }

    @Test fun decodeUtf16WithBom() {
        val b = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Xin chào".toByteArray(Charsets.UTF_16LE)
        assertEquals("Xin chào", PackText.decode(b))
    }

    @Test fun decodeUtf8WithBom() {
        val b = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Tiếng Việt".toByteArray(Charsets.UTF_8)
        assertEquals("Tiếng Việt", PackText.decode(b))
    }

    @Test fun decodeWindows1258() {
        assumeTrue(Charset.isSupported("windows-1258"))
        val text = "Xin chào Côte" // chỉ dùng chữ có sẵn trong Windows-1258; byte 0xE0 đứng riêng không phải UTF-8 hợp lệ
        val b = text.toByteArray(Charset.forName("windows-1258"))
        assertEquals(text, PackText.decode(b))
    }

    @Test fun jsonArrayOfObjects() {
        val p = PackText.parseAny("""[{"id":1,"original":"You died","vi":"Bạn đã chết"},{"source":"Press any button","translation":"Nhấn nút bất kỳ"}]""")
        assertEquals(listOf("You died" to "Bạn đã chết", "Press any button" to "Nhấn nút bất kỳ"), p.lines)
    }

    @Test fun jsonFlatMapWithEscapes() {
        val p = PackText.parseAny("""{ "Hello, world": "Chào thế giới", "Line\none": "Dòng \"một\"", "123": "x" }""")
        assertEquals(2, p.lines.size)
        assertEquals("Chào thế giới", p.lines[0].second)
        assertEquals("Dòng \"một\"", p.lines[1].second)
    }

    @Test fun gettextPo() {
        val po = """
            msgid ""
            msgstr "Project-Id-Version: x\n"

            #: a.c:1
            msgid "Save game"
            msgstr "Lưu game"

            msgid "A very long "
            "sentence here"
            msgstr "Một câu rất "
            "dài ở đây"

            msgid "Untranslated"
            msgstr ""
        """.trimIndent()
        val p = PackText.parseAny(po)
        assertEquals(listOf("Save game" to "Lưu game", "A very long sentence here" to "Một câu rất dài ở đây"), p.lines)
    }

    @Test fun csvWithHeaderAndQuotedComma() {
        val csv = "id,English,Vietnamese\n1,\"Hello, hero\",\"Chào, anh hùng\"\n2,Run away,Chạy đi\n"
        val p = PackText.parseAny(csv)
        assertEquals(listOf("Hello, hero" to "Chào, anh hùng", "Run away" to "Chạy đi"), p.lines)
    }

    @Test fun tsvWithoutHeader() {
        val p = PackText.parseAny("You died\tBạn đã chết\nQuest complete\tHoàn thành nhiệm vụ\n")
        assertEquals(2, p.lines.size)
        assertEquals("Hoàn thành nhiệm vụ", p.lines[1].second)
    }

    @Test fun plainEqualsFileSplitsTermsAndLines() {
        val p = PackText.parseAny("Hyrule = Hyrule\nLink = Link\nI will protect this land with my life = Tôi sẽ bảo vệ vùng đất này bằng cả mạng sống\n")
        assertEquals(2, p.terms.size)
        assertEquals(1, p.lines.size)
    }

    @Test fun appFormatKeepsSections() {
        val p = PackText.parseAny("tên: Game thử\n[thuật ngữ]\nMana = Ma lực\n[câu thoại]\nYOU DIED = BẠN ĐÃ CHẾT\n")
        assertEquals("Game thử", p.name)
        assertEquals(listOf("Mana" to "Ma lực"), p.terms)
        assertEquals(listOf("YOU DIED" to "BẠN ĐÃ CHẾT"), p.lines)
    }

    @Test fun reliableFuzzyMatchIsSeparatedFromOcrFoldSuggestion() {
        val reliable = TransMemory(listOf(
            "The quick brown fox jumps over the lazy dog" to "Con cáo nâu nhanh nhẹn nhảy qua con chó lười"
        )).lookupEx("The quick brown fox jumps over the lazy doq")
        assertNotNull(reliable)
        assertTrue(reliable!!.fuzzy)
        assertTrue(reliable.reliable)

        val ocrOnly = TransMemory(listOf("YOU DIED" to "BẠN ĐÃ CHẾT")).lookupEx("you d1ed")
        assertNotNull(ocrOnly)
        assertTrue(ocrOnly!!.fuzzy)
        assertTrue(!ocrOnly.reliable)
    }
    @Test fun verifiedKnowledgeCsvRoundTripsQuotedCellsAndKeepsGameScope() {
        val original = listOf(
            "Hello, \"hero\"" to "Chào, \"anh hùng\"",
            "A line\ncontinued" to "Một dòng\ntiếp tục"
        )
        val csv = PackText.formatScopedCsv("Game thử", original)
        val pack = PackText.parseScopedCsv(csv)
        assertNotNull(pack)
        assertEquals("Game thử", pack!!.name)
        assertEquals(
            listOf("Hello, \"hero\"" to "Chào, \"anh hùng\"", "A line continued" to "Một dòng tiếp tục"),
            pack.lines
        )
    }

    @Test fun scopedCsvRejectsMissingSourceOrTranslation() {
        val missingSource = "source,vi,game\n,\"Bản dịch\",\"Game thử\"\n"
        val missingTranslation = "source,vi,game\n\"Original\",,\"Game thử\"\n"
        assertTrue(runCatching { PackText.parseScopedCsv(missingSource) }.isFailure)
        assertTrue(runCatching { PackText.parseScopedCsv(missingTranslation) }.isFailure)
    }

    @Test fun scopedCsvRejectsMixedGamesAndEmptyData() {
        val mixed = "source,vi,game\n\"One\",\"Một\",\"Game A\"\n\"Two\",\"Hai\",\"Game B\"\n"
        val empty = "source,vi,game\n"
        assertTrue(runCatching { PackText.parseScopedCsv(mixed) }.isFailure)
        assertTrue(runCatching { PackText.parseScopedCsv(empty) }.isFailure)
    }

    @Test fun verifiedKnowledgeImportPreservesExistingPlayerCorrection() {
        val existing = listOf(
            "Quest complete" to "Nhiệm vụ đã xong (bản sửa tay)",
            "Old line" to "Dòng cũ"
        )
        val verified = listOf(
            "Quest complete" to "Hoàn thành nhiệm vụ",
            "New line" to "Dòng mới"
        )
        val merged = TranslationLineMerge.merge(existing, verified, preserveExisting = true)
        assertEquals("Nhiệm vụ đã xong (bản sửa tay)", merged.first { it.first == "Quest complete" }.second)
        assertEquals("Dòng mới", merged.first { it.first == "New line" }.second)
        assertEquals(3, merged.size)
    }

    @Test fun normalPackImportCanStillReplaceExistingTranslation() {
        val existing = listOf("Quest complete" to "Bản cũ")
        val incoming = listOf("Quest complete" to "Bản mới")
        val merged = TranslationLineMerge.merge(existing, incoming, preserveExisting = false)
        assertEquals("Bản mới", merged.single().second)
    }

    @Test fun importedLinesWorkInTransMemory() {
        val p = PackText.parseAny("""{"original":"YOU DIED","vi":"BẠN ĐÃ CHẾT"}""".let { "[$it]" })
        val tm = TransMemory(p.lines)
        assertNotNull(tm.lookup("YOU DIED"))
        assertTrue(tm.lookup("you d1ed")!!.contains("BẠN"))
    }
}
