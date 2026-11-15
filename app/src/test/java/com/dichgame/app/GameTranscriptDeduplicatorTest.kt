package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTranscriptDeduplicatorTest {
    @Test fun detectsAsrPhraseContainedInLongerOcrLine() {
        assertTrue(GameTranscriptDeduplicator.isPartialOverlap("don't know", "I don't know"))
    }

    @Test fun detectsOcrPhraseContainedInLongerAsrLine() {
        assertTrue(GameTranscriptDeduplicator.isPartialOverlap("I don't know what", "don't know"))
    }

    @Test fun ignoresUnrelatedLines() {
        assertFalse(GameTranscriptDeduplicator.isPartialOverlap("open the door", "where are you"))
    }

    @Test fun keepsOneWordReplies() {
        assertFalse(GameTranscriptDeduplicator.isPartialOverlap("No", "I said no"))
        assertFalse(GameTranscriptDeduplicator.isPartialOverlap("Go", "we should go now"))
    }

    @Test fun doesNotTreatExactMatchAsPartialOverlap() {
        assertFalse(GameTranscriptDeduplicator.isPartialOverlap("I don't know", "I don't know"))
    }

    @Test fun ignoresVeryShortCommonPhrases() {
        assertFalse(GameTranscriptDeduplicator.isPartialOverlap("I am", "I am ready"))
    }

    @Test fun detectsChinesePhraseOverlapWithoutSpaces() {
        assertTrue(GameTranscriptDeduplicator.isPartialOverlap("你在哪里", "我想知道你在哪里"))
    }

    @Test fun detectsJapanesePhraseOverlapWithoutSpaces() {
        assertTrue(GameTranscriptDeduplicator.isPartialOverlap("ここに行こう", "早くここに行こうよ"))
    }

    @Test fun keepsVeryShortCjkReplies() {
        assertFalse(GameTranscriptDeduplicator.isPartialOverlap("行け", "早く行けよ"))
    }

    @Test fun ignoresUnrelatedCjkLines() {
        assertFalse(GameTranscriptDeduplicator.isPartialOverlap("開けて", "どこにいる"))
    }
}
