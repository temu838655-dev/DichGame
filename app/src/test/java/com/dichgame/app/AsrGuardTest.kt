package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrGuardTest {
    private fun ok(s: String, level: Float = 0.2f, lang: String? = null) = AsrGuard.check(s, level, lang).ok

    @Test fun realDialoguePasses() {
        assertTrue(ok("We need to leave before they find us."))
        assertTrue(ok("Don't shoot!"))
        assertTrue(ok("今日は戦うぞ", lang = "ja"))
        assertTrue(ok("Where is the key?", lang = "en"))
    }

    @Test fun soundTagsAndMusicSymbolsAreDropped() {
        assertFalse(ok("[Music]"))
        assertFalse(ok("(gunfire)"))
        assertFalse(ok("*sighs*"))
        assertFalse(ok("♪ ♪"))
        assertFalse(ok("♪♪♪"))
        assertEquals("Run!", AsrGuard.check("[Music] Run!", 0.2f).text)
    }

    @Test fun knownHallucinationsAreDroppedAtAnyVolume() {
        assertFalse(ok("Thanks for watching!", level = 0.5f))
        assertFalse(ok("Subtitles by the Amara.org community", level = 0.5f))
        assertFalse(ok("ご視聴ありがとうございました"))
        assertFalse(ok("请不吝点赞 订阅 转发 打赏支持明镜与点点栏目"))
        assertFalse(ok("Субтитры сделал DimaTorzok"))
        assertFalse(ok("시청해주셔서 감사합니다"))
    }

    @Test fun ambiguousShortPhrasesDependOnVolume() {
        assertFalse(ok("Thank you.", level = 0.005f))
        val v = AsrGuard.check("Thank you.", 0.2f)
        assertTrue(v.ok)
        assertTrue(v.ambiguous)
        assertTrue(ok("Thank you for saving my village.", level = 0.005f))
    }

    @Test fun loopsAreCollapsedOrDropped() {
        val r = AsrGuard.check("I'm sorry. I'm sorry. I'm sorry. I'm sorry. Let's go.", 0.2f)
        assertTrue(r.ok)
        assertEquals("I'm sorry. Let's go.", r.text)
        assertFalse(ok("the the the the the the"))
        assertFalse(ok("a b a b a b a b a b a b a b a b a b"))
        assertFalse(ok("ああああああああ"))
    }

    @Test fun wrongScriptIsRejectedWhenLanguageIsKnown() {
        assertFalse(ok("字幕提供", lang = "en"))
        assertTrue(ok("Where are you going?", lang = "en"))
        assertTrue(ok("Where are you going?", lang = "auto"))
    }

    @Test fun trivialTextIsRejected() {
        assertFalse(ok(""))
        assertFalse(ok("..."))
        assertFalse(ok("A"))
        assertFalse(ok("123"))
    }

    @Test fun rmsOfSilenceAndTone() {
        assertEquals(0f, AsrGuard.rms(ShortArray(100)), 0f)
        val tone = ShortArray(1000) { (Math.sin(it / 5.0) * 10000).toInt().toShort() }
        assertTrue(AsrGuard.rms(tone) in 0.18f..0.27f)
    }

    @Test fun switchNeedsThreeConsistentGuesses() {
        val t = LangSwitchTracker("en")
        val fr = LangDetect.guess("Je ne pense pas que nous devrions aller là-bas ce soir")
        assertNotNull(fr)
        assertNull(t.observe(fr))
        assertNull(t.observe(fr))
        assertEquals("fr", t.observe(fr))
        assertEquals("fr", t.current)
    }

    @Test fun oneStrayGuessDoesNotSwitch() {
        val t = LangSwitchTracker("ja")
        val en = LangDetect.guess("I don't think we should go there tonight")
        assertNull(t.observe(en))
        val ja = LangDetect.guess("おはようございます、元気ですか")
        assertNull(t.observe(ja))   // trở về ngôn ngữ hiện tại: xoá chuỗi
        assertNull(t.observe(en))
        assertNull(t.observe(en))
        assertEquals("ja", t.current)
    }

    @Test fun bareKanjiInJapaneseGameIsNotChinese() {
        val t = LangSwitchTracker("ja")
        val zh = LangDetect.guess("勝利")
        repeat(5) { assertNull(t.observe(zh)) }
        assertEquals("ja", t.current)
    }
}
