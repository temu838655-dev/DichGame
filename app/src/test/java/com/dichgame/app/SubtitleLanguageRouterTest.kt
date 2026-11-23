package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleLanguageRouterTest {
    @Test fun shortWordsDoNotChangeSourceLanguage() {
        val r = SubtitleLanguageRouter("en")
        assertEquals("en", r.resolve("No!", "es", null))
        assertEquals("en", r.resolve("Yes.", "id", null))
        assertEquals("en", r.resolve("Go", "pt", null))
    }

    @Test fun switchesNonLatinScriptsWithoutManualSelection() {
        val r = SubtitleLanguageRouter("en")
        assertEquals("ja", r.resolve("今日はどこに行くのですか", null))
        assertEquals("ja", r.resolve("戦闘開始", null))
        assertEquals("ko", r.resolve("안녕하세요, 어디 가세요?", null))
        assertEquals("zh", r.resolve("你今天想去哪里？", null))
        assertEquals("ru", r.resolve("Привет, как дела сегодня?", null))
    }

    @Test fun recognizesVietnameseAndSkipsTranslation() {
        val r = SubtitleLanguageRouter("en")
        assertEquals("vi", r.resolve("Tôi muốn về nhà tối nay.", "fr"))
        assertEquals("vi", r.current)
    }

    @Test fun latinSwitchRequiresAgreementOrTwoConsistentCaptions() {
        val r = SubtitleLanguageRouter("en")
        assertEquals("en", r.resolve("Bonjour tout le monde ce matin", "fr", null))
        assertEquals("fr", r.resolve("Nous devons partir avant la nuit", "fr", null))
    }

    @Test fun trustedEnglishTextRejectsWrongLanguageFromMlKit() {
        val r = SubtitleLanguageRouter("en")
        val g = LangDetect.Guess("en", 0.9f, 'L')
        assertEquals("en", r.resolve("I think we should all go home now", "id", g))
        assertEquals("en", r.resolve("Where are you going tonight", "id", g))
    }

    @Test fun strongFrenchSentenceCanSwitchImmediately() {
        val r = SubtitleLanguageRouter("en")
        assertEquals("fr", r.resolve(
            "Je ne pense pas que nous devrions aller là-bas ce soir",
            "fr", LangDetect.Guess("fr", 0.8f, 'L')
        ))
    }
}
