package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocFormatTest {
    private fun join(a: String, an: String, b: String, bn: String): LocFormat.Join {
        val x = LocFormat.parse(a, an)
        val y = LocFormat.parse(b, bn)
        assertNotNull("file gốc không đọc được", x)
        assertNotNull("file Việt không đọc được", y)
        return LocFormat.join(x!!, y!!)
    }

    @Test fun nestedJsonPairedByKey() {
        val en = """{"menu":{"start":"Start game","quit":"Quit"},"dialog":{"d1":"We need to leave before they find us.","d2":"Who goes there?"}}"""
        val vi = """{"menu":{"start":"Bắt đầu","quit":"Thoát"},"dialog":{"d1":"Ta phải đi trước khi chúng tìm ra ta.","d2":"Ai đó?"}}"""
        val j = join(en, "en.json", vi, "vi.json")
        assertEquals("khoá", j.how)
        assertEquals(4, j.pairs.size)
        assertTrue(j.pairs.contains("We need to leave before they find us." to "Ta phải đi trước khi chúng tìm ra ta."))
    }

    @Test fun jsonWithArraysEscapesAndUnicode() {
        val en = """{"lines":["Hello\nthere","It's fine"],"n":5,"ok":true,"none":null}"""
        val vi = """{"lines":["Xin chào\nbạn","Không sao đâu"],"n":5,"ok":true,"none":null}"""
        val j = join(en, "a.json", vi, "b.json")
        assertEquals(2, j.pairs.size)
        assertEquals("Hello there", j.pairs[0].first)
        assertEquals("It's fine", j.pairs[1].first)
    }

    @Test fun brokenJsonIsRejected() {
        assertNull(LocFormat.parse("""{"a": "b", "c": }""", "x.json"))
        assertNull(LocFormat.parse("""{"a": "b" "c": "d"}""", "x.json"))
    }

    @Test fun androidXmlPairedByName() {
        val en = """<?xml version="1.0"?><resources><string name="start">Start</string><string name="warn">Don&apos;t look back &amp; run.</string><string name="bye">Goodbye, friend.</string></resources>"""
        val vi = """<?xml version="1.0"?><resources><string name="start">Bắt đầu</string><string name="warn">Đừng ngoái lại và chạy đi.</string><string name="bye">Tạm biệt, bạn.</string></resources>"""
        val j = join(en, "strings.xml", vi, "strings.xml")
        assertEquals("khoá", j.how)
        assertTrue(j.pairs.contains("Don't look back & run." to "Đừng ngoái lại và chạy đi."))
    }

    @Test fun propertiesAndIniWithSections() {
        val en = "# comment\n[ui]\nstart=Start game\nquit=Quit game\n[story]\nintro=Long ago, a king ruled.\n"
        val vi = "# bình luận\n[ui]\nstart=Bắt đầu chơi\nquit=Thoát game\n[story]\nintro=Ngày xưa có một vị vua cai trị.\n"
        val j = join(en, "en.ini", vi, "vi.ini")
        assertEquals("khoá", j.how)
        assertEquals(3, j.pairs.size)
    }

    @Test fun stringsFile() {
        val en = "\"a\" = \"Open the door.\";\n\"b\" = \"Close the door.\";\n\"c\" = \"Run away!\";\n"
        val vi = "\"a\" = \"Mở cửa ra.\";\n\"b\" = \"Đóng cửa lại.\";\n\"c\" = \"Chạy đi!\";\n"
        assertEquals(3, join(en, "Localizable.strings", vi, "Localizable.strings").pairs.size)
    }

    @Test fun srtPairedByStartTime() {
        val en = "1\n00:00:01,000 --> 00:00:03,000\nWe must hurry.\n\n2\n00:00:04,000 --> 00:00:06,000\n<i>Follow me.</i>\n\n3\n00:00:07,000 --> 00:00:09,000\nWatch out!\n"
        val vi = "1\n00:00:01,000 --> 00:00:03,000\nChúng ta phải nhanh lên.\n\n2\n00:00:04,000 --> 00:00:06,000\nĐi theo tôi.\n\n3\n00:00:07,000 --> 00:00:09,000\nCẩn thận!\n"
        val j = join(en, "en.srt", vi, "vi.srt")
        assertEquals("khoá", j.how)
        assertTrue(j.pairs.contains("Follow me." to "Đi theo tôi."))
    }

    @Test fun srtWithDifferentTimingsPairsByOrder() {
        val en = "1\n00:00:01,000 --> 00:00:03,000\nWe must hurry.\n\n2\n00:00:04,000 --> 00:00:06,000\nFollow me.\n\n3\n00:00:07,000 --> 00:00:09,000\nWatch out!\n"
        val vi = "1\n00:00:01,500 --> 00:00:03,500\nChúng ta phải nhanh lên.\n\n2\n00:00:04,500 --> 00:00:06,500\nĐi theo tôi.\n\n3\n00:00:07,500 --> 00:00:09,500\nCẩn thận!\n"
        val j = join(en, "en.srt", vi, "vi.srt")
        assertEquals("thứ tự", j.how)
        assertEquals(3, j.pairs.size)
    }

    @Test fun plainLinesPairedByOrderOnlyWhenSameLength() {
        val en = "Hello there.\nHow are you today?\nI am fine, thank you.\nSee you later.\n"
        val vi = "Xin chào.\nHôm nay bạn thế nào?\nTôi khỏe, cảm ơn.\nHẹn gặp lại.\n"
        assertEquals("thứ tự", join(en, "en.txt", vi, "vi.txt").how)
        val shorter = "Xin chào.\nHôm nay bạn thế nào?\n"
        val j = join(en, "en.txt", shorter, "vi.txt")
        assertNull(j.how)
        assertTrue(j.note, j.note.contains("Không ghép được"))
    }

    @Test fun unrelatedKeysAreNotJoined() {
        val a = """{"a":"One two three","b":"Four five six","c":"Seven eight nine"}"""
        val b = """{"x":"Một","y":"Hai","z":"Ba"}"""
        assertNull(join(a, "a.json", b, "b.json").how)
    }

    @Test fun untranslatedVariablesAndEmptyAreFilteredWithCounts() {
        val en = """{"a":"Hello {player}, welcome!","b":"Press START","c":"Take %d coins now.","d":"12345","e":"Fine.","f":"<color=red>Danger</color> ahead,<br>be careful."}"""
        val vi = """{"a":"Chào {player}, hoan nghênh!","b":"Press START","c":"Nhận %d xu ngay.","d":"12345","e":"Ổn.","f":"<color=red>Nguy hiểm</color> phía trước,<br>cẩn thận."}"""
        val j = join(en, "a.json", vi, "b.json")
        assertEquals(6, j.total)
        assertEquals(2, j.withVariables)
        assertEquals(1, j.untranslated)
        assertEquals(1, j.noText)
        assertEquals(2, j.pairs.size)
        assertTrue(j.pairs.contains("Danger ahead, be careful." to "Nguy hiểm phía trước, cẩn thận."))
    }

    @Test fun cleanStripsTagsAndLineBreaks() {
        assertEquals("Hello world", LineClean.clean("<b>Hello</b>\\nworld"))
        assertEquals("a b", LineClean.clean("a<br/>b"))
        assertTrue(LineClean.hasVars("Hi {0}"))
        assertTrue(LineClean.hasVars("Score: %s"))
        assertTrue(!LineClean.hasVars("100% sure"))
    }

    @Test fun csvTwoColumns() {
        val en = "k1,Open the gate\nk2,Close the gate\nk3,\"Wait, what?\"\n"
        val vi = "k1,Mở cổng\nk2,Đóng cổng\nk3,\"Khoan, gì cơ?\"\n"
        val j = join(en, "en.csv", vi, "vi.csv")
        assertEquals(3, j.pairs.size)
        assertTrue(j.pairs.contains("Wait, what?" to "Khoan, gì cơ?"))
    }
}
