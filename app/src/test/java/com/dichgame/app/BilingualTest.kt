package com.dichgame.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BilingualTest {
    private val en1 = "We need to leave before they find us."
    private val vi1 = "Ta phải đi trước khi chúng tìm ra ta."

    private fun found(raw: String, name: String): Bilingual.Found {
        val f = Bilingual.parse(raw, name)
        assertNotNull("không nhận ra file $name", f)
        return f!!
    }

    @Test fun vietnameseIsRecognisedFromTheText() {
        assertTrue(Viet.has(vi1))
        assertTrue(Viet.has("Đừng nhìn xuống."))
        assertFalse(Viet.has(en1))
        // tiếng Pháp, Đức, Tây Ban Nha có dấu nhưng không có chữ đặc trưng tiếng Việt
        assertFalse(Viet.has("Où est le château, très cher? Über Straße, señor, niño."))
        assertTrue(Viet.isVi(Viet.ratio(listOf(vi1, "Ai đó?", "Ở gần tôi."))))
        assertTrue(Viet.notVi(Viet.ratio(listOf(en1, "Who goes there?"))))
    }

    @Test fun csvWithHeaderPicksEnglishAndVietnameseColumns() {
        val raw = "key,en,vi\n" +
            "menu.start,Start game,Bắt đầu\n" +
            "menu.quit,Quit,Thoát\n" +
            "d.1,$en1,$vi1\n" +
            "d.2,Who goes there?,Ai đó?\n" +
            "d.3,Stay close to me.,Ở gần tôi.\n"
        val f = found(raw, "text.csv")
        assertEquals(5, f.pairs.size)
        assertTrue(f.pairs.contains("Who goes there?" to "Ai đó?"))
        assertTrue(f.pairs.contains(en1 to vi1))
    }

    @Test fun columnOrderDoesNotMatterWhenThereIsNoHeader() {
        val raw = "$vi1;$en1\nAi đó?;Who goes there?\nỞ gần tôi.;Stay close to me.\nĐừng nhìn xuống.;Do not look down.\n"
        val f = found(raw, "x.csv")
        assertEquals(4, f.pairs.size)
        assertEquals("Who goes there?" to "Ai đó?", f.pairs[1])
    }

    @Test fun jsonArrayOfObjectsWithTwoLanguageFields() {
        val raw = """[{"id":1,"en":"$en1","vi":"$vi1"},
            {"id":2,"en":"Who goes there?","vi":"Ai đó?"},
            {"id":3,"en":"Stay close to me.","vi":"Ở gần tôi."}]"""
        val f = found(raw, "dialog.json")
        assertEquals(3, f.pairs.size)
        assertEquals(en1 to vi1, f.pairs[0])
    }

    @Test fun jsonWithOneTablePerLanguage() {
        val raw = """{"en":{"menu":{"start":"Start game","quit":"Quit"},"d1":"$en1","d2":"Who goes there?"},
            "vi":{"menu":{"start":"Bắt đầu","quit":"Thoát"},"d1":"$vi1","d2":"Ai đó?"}}"""
        val f = found(raw, "lang.json")
        assertEquals(4, f.pairs.size)
        assertNotNull(f.join)
        assertTrue(f.pairs.contains("Start game" to "Bắt đầu"))
    }

    @Test fun xliff() {
        val raw = """<?xml version="1.0"?><xliff version="1.2"><file source-language="en" target-language="vi"><body>
            <trans-unit id="1"><source>$en1</source><target>$vi1</target></trans-unit>
            <trans-unit id="2"><source>Who goes there?</source><target>Ai đó?</target></trans-unit>
            </body></file></xliff>"""
        val f = found(raw, "a.xlf")
        assertEquals(2, f.pairs.size)
        assertEquals("XLIFF", f.how)
        assertTrue(f.pairs.contains("Who goes there?" to "Ai đó?"))
    }

    @Test fun tmx() {
        val raw = """<tmx version="1.4"><body>
            <tu><tuv xml:lang="en"><seg>We need to leave.</seg></tuv><tuv xml:lang="vi"><seg>Ta phải đi thôi.</seg></tuv></tu>
            <tu><tuv xml:lang="en"><seg>Who goes there?</seg></tuv><tuv xml:lang="vi"><seg>Ai đó?</seg></tuv></tu>
            </body></tmx>"""
        val f = found(raw, "a.tmx")
        assertEquals(2, f.pairs.size)
        assertEquals("We need to leave." to "Ta phải đi thôi.", f.pairs[0])
    }

    @Test fun renpyTranslateBlocks() {
        val raw = "translate vietnamese start_a1b2c3:\n\n" +
            "    # e \"$en1\"\n" +
            "    e \"$vi1\"\n\n" +
            "translate vietnamese strings:\n\n" +
            "    old \"Who goes there?\"\n" +
            "    new \"Ai đó?\"\n"
        val f = found(raw, "script.rpy")
        assertEquals(2, f.pairs.size)
        assertTrue(f.pairs.contains(en1 to vi1))
        assertTrue(f.pairs.contains("Who goes there?" to "Ai đó?"))
    }

    @Test fun srtWithTwoLanguagesPerCue() {
        val raw = "1\n00:00:01,000 --> 00:00:03,000\n$en1\n$vi1\n\n" +
            "2\n00:00:04,000 --> 00:00:06,000\nWho goes there?\nAi đó?\n\n" +
            "3\n00:00:07,000 --> 00:00:09,000\nStay close to me.\nỞ gần tôi.\n"
        val f = found(raw, "both.srt")
        assertEquals(3, f.pairs.size)
        assertEquals(en1 to vi1, f.pairs[0])
    }

    @Test fun pipeSeparatedLines() {
        val raw = "$en1 | $vi1\nWho goes there? | Ai đó?\nStay close to me. | Ở gần tôi.\nDo not look down. | Đừng nhìn xuống.\n"
        val f = found(raw, "x.txt")
        assertEquals(4, f.pairs.size)
        assertEquals("Who goes there?" to "Ai đó?", f.pairs[1])
    }

    @Test fun alternatingLinesEnglishThenVietnamese() {
        val raw = "$en1\n$vi1\nWho goes there?\nAi đó?\nStay close to me.\nỞ gần tôi.\n"
        val f = found(raw, "x.txt")
        assertEquals(3, f.pairs.size)
        assertEquals(en1 to vi1, f.pairs[0])
    }

    @Test fun twoEnglishColumnsAreNotAPair() {
        val raw = "key,en,fr\na.1,Start game,Commencer le jeu\na.2,Quit the game now,Quitter le jeu\na.3,Stay close to me,Reste près de moi\n"
        assertNull(Bilingual.parse(raw, "x.csv"))
    }

    @Test fun keyColumnIsNeverUsedAsTheOriginalText() {
        val raw = "menu.start;Bắt đầu\nmenu.quit;Thoát cửa\nquest.title;Nhiệm vụ đầu tiên của bạn\n"
        assertNull(Bilingual.parse(raw, "x.csv"))
    }

    @Test fun vietnameseOnlyFileIsReportedAsSuch() {
        val raw = "$vi1\nAi đó?\nỞ gần tôi.\nĐừng nhìn xuống.\n"
        assertEquals(4, Bilingual.viOnlyCount(raw, "x.txt"))
        assertEquals(0, Bilingual.viOnlyCount("$en1\nWho goes there?\nStay close to me.\n", "x.txt"))
    }

    // ---- gettext .mo ----

    private fun mo(pairs: List<Pair<String, String>>): ByteArray {
        val n = pairs.size
        val os = pairs.map { it.first.toByteArray(Charsets.UTF_8) }
        val ts = pairs.map { it.second.toByteArray(Charsets.UTF_8) }
        val buf = ByteArrayOutputStream()
        fun w(v: Int) { for (i in 0..3) buf.write((v ushr (8 * i)) and 255) }
        w(0x950412de.toInt()); w(0); w(n); w(28); w(28 + 8 * n); w(0); w(0)
        var o = 28 + 16 * n
        for (b in os) { w(b.size); w(o); o += b.size + 1 }
        for (b in ts) { w(b.size); w(o); o += b.size + 1 }
        for (b in os) { buf.write(b); buf.write(0) }
        for (b in ts) { buf.write(b); buf.write(0) }
        return buf.toByteArray()
    }

    @Test fun compiledGettextCatalogue() {
        val bytes = mo(listOf("" to "Content-Type: text/plain; charset=UTF-8\n", en1 to vi1, "Who goes there?" to "Ai đó?"))
        assertTrue(Bilingual.isMo(bytes))
        val f = Bilingual.fromMo(bytes)
        assertNotNull(f)
        assertEquals(2, f!!.pairs.size)
        assertTrue(f.pairs.contains(en1 to vi1))
    }

    @Test fun brokenMoIsRejectedNotCrashed() {
        val bytes = mo(listOf(en1 to vi1, "Who goes there?" to "Ai đó?"))
        assertNull(Bilingual.fromMo(bytes.copyOf(40)))
        assertNull(Bilingual.fromMo(ByteArray(10)))
    }

    // ---- nhiều file (trong .zip) ----

    @Test fun enAndViFilesInSeparateFoldersArePaired() {
        val en = """{"menu":{"start":"Start game","quit":"Quit"},"d1":"$en1"}"""
        val vi = """{"menu":{"start":"Bắt đầu","quit":"Thoát"},"d1":"$vi1"}"""
        val only = """{"x":{"a":"Chỉ có tiếng Việt thôi đấy.","b":"Thêm một dòng nữa cho đủ."}}"""
        val z = Bilingual.pairEntries(listOf(
            "lang/en/ui.json" to en,
            "lang/vi/ui.json" to vi,
            "lang/vi/extra.json" to only,
            "readme.txt" to "Hello",
        ))
        assertEquals(3, z.pairs.size)
        assertTrue(z.pairs.contains("Start game" to "Bắt đầu"))
        assertEquals(1, z.sources.size)
        assertEquals(listOf("extra.json"), z.viOnly)
        assertTrue(z.failed.isEmpty())
    }

    @Test fun zipFileIsReadAndNonTextEntriesAreSkipped() {
        val en = """{"menu":{"start":"Start game","quit":"Quit"},"d1":"$en1"}"""
        val vi = """{"menu":{"start":"Bắt đầu","quit":"Thoát"},"d1":"$vi1"}"""
        val f = File.createTempFile("viet", ".zip")
        try {
            ZipOutputStream(f.outputStream()).use { z ->
                fun add(name: String, data: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() }
                add("en/ui.json", en.toByteArray())
                add("vi/ui.json", vi.toByteArray())
                add("__MACOSX/._ui.json", ByteArray(20))
                add("logo.png", ByteArray(50))
            }
            val head = f.readBytes().copyOf(8)
            assertEquals("zip", Bilingual.archiveKind(head))
            val r = Bilingual.fromZipFile(f)
            assertEquals(3, r.pairs.size)
            assertEquals(2, r.skippedFiles)
        } finally { f.delete() }
    }

    // ---- file nhị phân, Office, nén khác ----

    @Test fun binaryPatchesAreNamedHonestly() {
        assertNotNull(Bilingual.binaryKind("Wukong-VietHoa.exe", "MZ\u0090\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertNotNull(Bilingual.binaryKind("patch.xdelta", ByteArray(40) { 1 }))
        assertNotNull(Bilingual.binaryKind("game_P.pak", ByteArray(40) { 1 }))
        assertEquals("file nhị phân", Bilingual.binaryKind("x.dat", ByteArray(100)))
        assertNull(Bilingual.binaryKind("lines.txt", "Hello world\nXin chào bạn\n".toByteArray()))
        assertFalse(Bilingual.looksBinary("﻿Hello world".toByteArray(Charsets.UTF_16LE)))
    }

    @Test fun officeAndOtherArchives() {
        assertNotNull(Bilingual.officeKind("Bang-dich.XLSX"))
        assertNull(Bilingual.officeKind("a.csv"))
        assertEquals("rar", Bilingual.archiveKind("Rar!\u001a\u0007\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("7z", Bilingual.archiveKind(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4)))
        assertNull(Bilingual.archiveKind("hello world".toByteArray()))
    }

    // ---- YAML (Paradox, Rails) và mã định dạng chữ ----

    @Test fun paradoxYamlIsPairedByKey() {
        val en = "l_english:\n MENU_START:0 \"Start game\"\n MENU_QUIT:0 \"Quit\"\n EVENT_1:0 \"$en1\"\n"
        val vi = "l_vietnamese:\n MENU_START:0 \"Bắt đầu\"\n MENU_QUIT:0 \"Thoát\"\n EVENT_1:0 \"$vi1\"\n"
        val a = LocFormat.parse(en, "en.yml")
        val b = LocFormat.parse(vi, "vi.yml")
        assertNotNull(a)
        assertNotNull(b)
        val j = LocFormat.join(a!!, b!!)
        assertEquals("khoá", j.how)
        assertEquals(3, j.pairs.size)
        assertTrue(j.pairs.contains(en1 to vi1))
    }

    @Test fun railsStyleNestedYaml() {
        val en = "en:\n  menu:\n    start: Start game\n    quit: Quit\n  dialog:\n    d1: \"$en1\"\n"
        val vi = "vi:\n  menu:\n    start: Bắt đầu\n    quit: Thoát\n  dialog:\n    d1: \"$vi1\"\n"
        val j = LocFormat.join(LocFormat.parse(en, "en.yml")!!, LocFormat.parse(vi, "vi.yml")!!)
        assertEquals("khoá", j.how)
        assertEquals(3, j.pairs.size)
    }

    @Test fun formattingTagsAreNotVariables() {
        assertFalse(LineClean.hasVars("{i}Wait{/i} for me"))
        assertFalse(LineClean.hasVars("§YGold§! and {color=#f00}red{/color}"))
        assertTrue(LineClean.hasVars("Xin chào [player]"))
        assertTrue(LineClean.hasVars("Hello {name}"))
        assertEquals("Wait for me", LineClean.clean("{i}Wait{/i} §Yfor§! me"))
    }
}
