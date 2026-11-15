package com.dichgame.app

import com.dichgame.app.TranslationGuard.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationGuardTest {
    private fun lv(src: String, out: String, names: List<Pair<String, String>> = emptyList()) =
        TranslationGuard.check(src, TranslationGuard.clean(out, src), names).level

    @Test fun goodTranslationsPass() {
        assertEquals(Level.OK, lv("I don't think that's a good idea.", "Tôi không nghĩ đó là ý hay."))
        assertEquals(Level.OK, lv("Don't move!", "Đứng yên!"))
        assertEquals(Level.OK, lv("今日は天気がいいですね。散歩に行きましょう。", "Hôm nay trời đẹp nhỉ. Chúng ta đi dạo nhé."))
        assertEquals(Level.OK, lv("I have 3 swords and 12 arrows.", "Tôi có 3 thanh kiếm và 12 mũi tên."))
    }

    @Test fun cleanStripsWrappers() {
        assertEquals("Đứng yên!", TranslationGuard.clean("Bản dịch: \"Đứng yên!\"", "Don't move!"))
        assertEquals("Đứng yên!", TranslationGuard.clean("```\nĐứng yên!\n```", "Don't move!"))
        assertEquals("Đứng yên!", TranslationGuard.clean("Đứng yên!\n\nLưu ý: câu này mang sắc thái ra lệnh.", "Don't move!"))
        assertEquals("Tôi ổn.", TranslationGuard.clean("“Tôi ổn.”", "I'm fine."))
    }

    @Test fun refusalsAndExplanationsAreRejected() {
        assertEquals(Level.REJECT, lv("I will find you.", "Xin lỗi, tôi không thể dịch nội dung này."))
        assertEquals(Level.REJECT, lv("I will find you.", "Here is the translation: Tôi sẽ tìm ra bạn."))
        assertEquals(Level.REJECT, lv("I will find you.", "  "))
    }

    @Test fun untranslatedEchoIsRejected() {
        assertEquals(Level.REJECT, lv("We need to leave before they find us.", "We need to leave before they find us."))
        assertEquals(Level.REJECT, lv("今日は戦うぞ、みんな準備はいいか", "今日は戦うぞ、みんな準備はいいか"))
        assertEquals(Level.OK, lv("Geralt of Rivia", "Geralt xứ Rivia"))
    }

    @Test fun foreignScriptInOutputIsRejected() {
        assertEquals(Level.REJECT, lv("We need to leave before they find us.", "我们需要在他们找到我们之前离开"))
    }

    @Test fun lengthAnomaliesAreFlagged() {
        val src = "We need to leave this place before the soldiers find us tonight"
        assertEquals(Level.REJECT, lv(src, "Đi."))
        val verdict = lv(src, "Chúng ta cần rời đi.")
        assertTrue(verdict == Level.SUSPECT || verdict == Level.REJECT)
        assertEquals(Level.REJECT, lv("Run!!! Now go go go away", "Chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi chạy đi"))
    }

    @Test fun loopsAreRejected() {
        assertEquals(Level.REJECT, lv("Wait for me here, friend.", "Đợi đợi đợi đợi đợi tôi ở đây."))
    }

    @Test fun missingNumbersAreSuspect() {
        assertEquals(Level.SUSPECT, lv("You have 250 gold left.", "Bạn còn lại ít vàng."))
        assertEquals(Level.SUSPECT, lv("You have 250 gold left.", "Bạn còn lại 520 vàng."))
    }

    @Test fun changedNamesAreSuspect() {
        val names = listOf("Geralt" to "Geralt", "Wolf School" to "Trường phái Sói")
        assertEquals(Level.OK, lv("Geralt belongs to the Wolf School.", "Geralt thuộc Trường phái Sói.", names))
        assertEquals(Level.SUSPECT, lv("Geralt belongs to the Wolf School.", "Anh ấy thuộc về một trường phái.", names))
    }

    @Test fun reasonsAreReported() {
        val v = TranslationGuard.check("You have 250 gold left.", "Bạn còn ít vàng.")
        assertTrue(v.reasons.isNotEmpty())
    }

    @Test fun smallNumbersSpelledOutAreAccepted() {
        assertEquals(Level.OK, lv("I have 3 swords.", "Tôi có ba thanh kiếm."))
        assertEquals(Level.SUSPECT, lv("I have 30 swords.", "Tôi có ba thanh kiếm."))
    }
}
