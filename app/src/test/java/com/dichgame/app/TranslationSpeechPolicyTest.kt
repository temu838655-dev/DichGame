package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationSpeechPolicyTest {
    @Test fun speaksValidTranslationEvenWhenConfidenceIsLow() {
        val candidates = listOf(
            TranslationSpeechPolicy.Candidate(" Bản dịch cần kiểm tra ", uncertain = true),
            TranslationSpeechPolicy.Candidate("Bản dịch chắc chắn", uncertain = false),
            TranslationSpeechPolicy.Candidate("   ", uncertain = false),
        )
        assertEquals(
            listOf("Bản dịch cần kiểm tra", "Bản dịch chắc chắn"),
            TranslationSpeechPolicy.speakable(candidates),
        )
    }

    @Test fun emptyTranslationNeverCreatesAnUtterance() {
        assertEquals(
            emptyList<String>(),
            TranslationSpeechPolicy.speakable(listOf(
                TranslationSpeechPolicy.Candidate("", uncertain = true),
                TranslationSpeechPolicy.Candidate("  ", uncertain = false),
            )),
        )
    }
}
