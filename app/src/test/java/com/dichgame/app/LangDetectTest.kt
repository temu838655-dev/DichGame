package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LangDetectTest {
    private fun lang(s: String) = LangDetect.guess(s)?.lang

    @Test fun nonLatinScriptsAreCertain() {
        assertEquals("ja", lang("おはようございます、元気ですか"))
        assertEquals("ja", lang("今日は戦うぞ"))          // Hán lẫn kana là tiếng Nhật
        assertEquals("zh", lang("你今天想去哪里"))
        assertEquals("ko", lang("안녕하세요 오늘 어때요"))
        assertEquals("th", lang("สวัสดีครับ วันนี้เป็นอย่างไรบ้าง"))
        assertEquals("ru", lang("Привет, как дела сегодня?"))
        assertEquals("uk", lang("Привіт, як справи? Я їду додому"))
        assertEquals("ar", lang("مرحبا كيف حالك اليوم"))
        assertEquals("el", lang("Γεια σου, τι κάνεις σήμερα"))
        assertEquals("he", lang("שלום מה שלומך היום"))
        assertEquals("hi", lang("नमस्ते आप कैसे हैं"))
    }

    @Test fun latinLanguagesByCommonWords() {
        assertEquals("en", lang("I don't think we should go there tonight"))
        assertEquals("fr", lang("Je ne pense pas que nous devrions aller là-bas ce soir"))
        assertEquals("de", lang("Ich glaube nicht, dass wir heute Abend dorthin gehen sollten"))
        assertEquals("es", lang("No creo que debamos ir allí esta noche, ¿verdad?"))
        assertEquals("pt", lang("Eu não acho que nós devemos ir lá esta noite, você não acha?"))
        assertEquals("it", lang("Non credo che dovremmo andare là questa sera, sei sicuro?"))
        assertEquals("id", lang("Saya tidak yakin kita harus pergi ke sana malam ini"))
        assertEquals("tr", lang("Bu gece oraya gitmemiz gerektiğini sanmıyorum ama sen bilirsin"))
        assertEquals("pl", lang("Nie sądzę, że powinniśmy tam dziś iść, ale ty jesteś pewny"))
        assertEquals("vi", lang("Tôi không nghĩ chúng ta nên đến đó tối nay"))
    }

    @Test fun tooShortOrNoLettersGivesNoGuess() {
        assertNull(LangDetect.guess(""))
        assertNull(LangDetect.guess("12345 !!!"))
        assertNull(LangDetect.guess("Go"))
        assertNull(LangDetect.guess("Geralt"))
    }

    @Test fun scriptFitRejectsWrongScript() {
        assertTrue(LangDetect.scriptFits("Where are you going?", "en"))
        assertTrue(LangDetect.scriptFits("今日は戦うぞ", "ja"))
        assertTrue(LangDetect.scriptFits("戦う", "ja"))
        assertFalse(LangDetect.scriptFits("ご視聴ありがとうございました", "en"))
        assertFalse(LangDetect.scriptFits("Спасибо за просмотр", "en"))
        assertTrue(LangDetect.scriptFits("123 ...", "en"))
    }

    @Test fun confidenceIsBounded() {
        val g = LangDetect.guess("The quick brown fox jumps over the lazy dog and the cat")
        assertNotNull(g)
        assertTrue(g!!.confidence in 0f..1f)
    }
}
