package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class VietsubGuardTest {
    @Test fun readsActualVietnameseWithoutTranslation() {
        assertTrue(VietsubGuard.isVietnamese("Tôi không biết chuyện gì đang xảy ra."))
        assertTrue(VietsubGuard.isVietnamese("Dạ, em hiểu rồi ạ."))
        assertTrue(VietsubGuard.isVietnamese("Không!"))
        assertTrue(VietsubGuard.isVietnamese("Có!"))
        assertTrue(VietsubGuard.isVietnamese("Xin chào!"))
    }

    @Test fun ignoresForeignAndUncertainSubtitles() {
        assertFalse(VietsubGuard.isVietnamese("I don't know what is happening."))
        assertFalse(VietsubGuard.isVietnamese("Bonjour, comment allez vous ?"))
        assertFalse(VietsubGuard.isVietnamese("ありがとうございます"))
        assertFalse(VietsubGuard.isVietnamese("12345"))
        assertFalse(VietsubGuard.isVietnamese("Hello"))
    }

    @Test fun cautiouslyHandlesNonDiacriticVietnamese() {
        assertTrue(VietsubGuard.isVietnamese("toi va ban se di ve nha"))
        assertFalse(VietsubGuard.isVietnamese("Game Over"))
    }
}
