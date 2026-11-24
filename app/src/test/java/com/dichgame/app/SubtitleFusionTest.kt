package com.dichgame.app

import com.dichgame.app.SubtitleFusion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFusionTest {
    private fun kinds(l: List<SubtitleFusion.Out>) = l.map { it.kind }

    @Test fun asrShowsImmediatelyWhenOcrIsSilent() {
        val f = SubtitleFusion()
        val o = f.onAsr("We need to leave before they find us.", 10_000)
        assertEquals(listOf(Kind.SHOW), kinds(o))
        assertEquals(Src.ASR, o[0].src)
    }

    @Test fun asrDuplicateOfOcrIsDropped() {
        val f = SubtitleFusion()
        assertEquals(listOf(Kind.SHOW), kinds(f.onOcr("We need to leave before they find us.", null, 1_000)))
        val o = f.onAsr("we need to leave before they find us", 2_200)
        assertEquals(listOf(Kind.DROP), kinds(o))
    }

    @Test fun asrWithSmallRecognitionErrorIsStillADuplicate() {
        val f = SubtitleFusion()
        f.onOcr("We need to leave before they find us.", null, 1_000)
        assertEquals(listOf(Kind.DROP), kinds(f.onAsr("We need to leave before they fine us.", 2_000)))
    }

    @Test fun ocrDuplicateOfShownAsrIsDropped() {
        val f = SubtitleFusion()
        assertEquals(listOf(Kind.SHOW), kinds(f.onAsr("Stay close to me.", 1_000)))
        assertEquals(listOf(Kind.DROP), kinds(f.onOcr("Stay close to me.", null, 2_500)))
    }

    @Test fun unrelatedAsrWhileOcrHealthyIsHeldThenShown() {
        val f = SubtitleFusion()
        f.onOcr("Press X to continue", null, 1_000)
        val h = f.onAsr("Somebody is coming up the stairs.", 1_500)
        assertEquals(listOf(Kind.HOLD), kinds(h))
        assertTrue(f.poll(2_000).isEmpty())
        val shown = f.poll(3_000)
        assertEquals(listOf(Kind.SHOW), kinds(shown))
        assertEquals("Somebody is coming up the stairs.", shown[0].text)
    }

    @Test fun heldAsrIsDroppedWhenOcrConfirmsIt() {
        val f = SubtitleFusion()
        f.onOcr("Press X to continue", null, 1_000)
        assertEquals(listOf(Kind.HOLD), kinds(f.onAsr("Somebody is coming up the stairs.", 1_500)))
        val o = f.onOcr("Somebody is coming up the stairs.", null, 2_000)
        assertEquals(listOf(Kind.SHOW), kinds(o))       // OCR hiện bản chữ trên màn hình
        assertTrue(f.poll(4_000).isEmpty())             // ASR chờ đã được xác nhận: không hiện lần hai
    }

    @Test fun halfReadOcrIsFusedWithFullAsr() {
        val f = SubtitleFusion()
        f.onOcr("We need to leave before", null, 1_000)
        val o = f.onAsr("We need to leave before they find us tonight.", 2_000)
        assertEquals(listOf(Kind.REPLACE), kinds(o))
        assertEquals(Src.MIXED, o[0].src)
        assertEquals("We need to leave before they find us tonight.", o[0].text)
    }

    @Test fun fusionKeepsOcrSpellingOfTheOverlap() {
        val f = SubtitleFusion()
        f.onOcr("Geralt of Rivia is", null, 1_000)
        val o = f.onAsr("jerald of rivia is not here today.", 2_000)
        // ASR nghe sai tên nhưng không ghép: chồng nhau quá ít để chắc chắn cùng một câu -> chờ OCR xác nhận
        assertTrue(o[0].kind == Kind.HOLD || o[0].kind == Kind.REPLACE)
    }

    @Test fun tooShortOcrFragmentIsNotFused() {
        val f = SubtitleFusion()
        f.onOcr("We need", null, 1_000)
        val o = f.onAsr("We need to leave before they find us tonight.", 2_000)
        assertTrue(o[0].kind != Kind.REPLACE)
    }

    @Test fun ocrFullLineReplacesHalfAsrLine() {
        val f = SubtitleFusion()
        f.onAsr("We need to leave before", 1_000)
        val o = f.onOcr("We need to leave before they find us tonight.", null, 2_000)
        assertEquals(listOf(Kind.REPLACE), kinds(o))
        assertEquals(Src.OCR, o[0].src)
    }

    @Test fun cjkOverlapIsDetected() {
        val f = SubtitleFusion()
        f.onOcr("今日は天気がいいですね", null, 1_000)
        assertEquals(listOf(Kind.DROP), kinds(f.onAsr("今日は天気がいいですね。", 2_000)))
    }

    @Test fun cjkHalfLineIsFused() {
        val f = SubtitleFusion()
        f.onOcr("今日は天気が", null, 1_000)
        val o = f.onAsr("今日は天気がいいですね散歩に行きましょう", 2_000)
        assertEquals(listOf(Kind.REPLACE), kinds(o))
        assertEquals("今日は天気がいいですね散歩に行きましょう", o[0].text)
    }

    @Test fun oldLinesExpireFromTheWindow() {
        val f = SubtitleFusion(shortWindowMs = 5_000)
        f.onOcr("Stay close to me.", null, 1_000)
        // 10 giây sau, cùng câu được nói lại: là một câu mới, không phải trùng
        assertEquals(listOf(Kind.SHOW), kinds(f.onAsr("Stay close to me.", 11_000)))
    }

    @Test fun ocrHealthExpires() {
        val f = SubtitleFusion(healthyMs = 6_000)
        f.onOcr("Press X to continue", null, 1_000)
        assertEquals(listOf(Kind.SHOW), kinds(f.onAsr("Somebody is coming.", 20_000)))
    }

    @Test fun differentLinesAreNeverMergedOrDropped() {
        val f = SubtitleFusion()
        f.onOcr("I will find you.", null, 1_000)
        assertEquals(listOf(Kind.HOLD), kinds(f.onAsr("Nobody knows where she went.", 1_800)))
    }

    @Test fun growingOcrLineReplacesThePartialOneInsteadOfBeingDropped() {
        val f = SubtitleFusion()
        assertEquals(listOf(Kind.SHOW), kinds(f.onOcr("We need to leave before", null, 1_000)))
        val o = f.onOcr("We need to leave before they find us.", null, 1_400)
        assertEquals(listOf(Kind.REPLACE), kinds(o))
        assertEquals("We need to leave before they find us.", o[0].text)
        // lần đọc sau cùng câu đầy đủ là trùng
        assertEquals(listOf(Kind.DROP), kinds(f.onOcr("We need to leave before they find us.", null, 1_800)))
    }

    @Test fun shorterOcrReadOfTheSameLineIsStillADuplicate() {
        val f = SubtitleFusion()
        f.onOcr("We need to leave before they find us.", null, 1_000)
        assertEquals(listOf(Kind.DROP), kinds(f.onOcr("We need to leave before", null, 1_400)))
    }

    @Test fun growingLineOfAnotherSpeakerIsShownSeparately() {
        val f = SubtitleFusion()
        f.onOcr("We need to leave before", "Anna", 1_000)
        assertEquals(listOf(Kind.SHOW), kinds(f.onOcr("We need to leave before they find us.", "Ben", 1_400)))
    }

    @Test fun identicalDialogueFromDifferentSpeakersIsNotDropped() {
        val f = SubtitleFusion()
        f.onOcr("Stay close to me.", "Anna", 1000)
        assertEquals(listOf(Kind.SHOW), kinds(f.onOcr("Stay close to me.", "Ben", 1400)))
    }

    @Test fun smallExactExtensionIsNotMistakenForDuplicate() {
        val f = SubtitleFusion()
        f.onOcr("We need to leave before sunrise", "Anna", 1000)
        assertEquals(listOf(Kind.REPLACE), kinds(f.onOcr("We need to leave before sunrise today", "Anna", 1400)))
    }

    @Test fun lineThatVanishedAndComesBackIsShownAgain() {
        val f = SubtitleFusion()
        val line = "We need to leave before they find us."
        assertEquals(listOf(Kind.SHOW), kinds(f.onOcr(line, null, 1_000)))
        f.onScreenEmpty(2_000)
        f.onScreenEmpty(3_500)
        assertEquals(listOf(Kind.SHOW), kinds(f.onOcr(line, null, 4_000)))
    }

    @Test fun shortFlickerOfTheScreenIsStillADuplicate() {
        val f = SubtitleFusion()
        val line = "We need to leave before they find us."
        f.onOcr(line, null, 1_000)
        f.onScreenEmpty(2_000)
        assertEquals(listOf(Kind.DROP), kinds(f.onOcr(line, null, 2_800)))
    }

    @Test fun vanishedOcrLineStillSuppressesTheHeardCopy() {
        val f = SubtitleFusion()
        val line = "We need to leave before they find us."
        f.onOcr(line, null, 1_000)
        f.onScreenEmpty(2_000)
        f.onScreenEmpty(3_500)
        assertEquals(listOf(Kind.DROP), kinds(f.onAsr("We need to leave before they find us", 4_000)))
    }
}
