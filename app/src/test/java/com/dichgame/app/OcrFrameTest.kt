package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class OcrFrameTest {
    @Test fun staticAndSmallNoise() {
        val a = IntArray(288) { 100 }
        val b = IntArray(288) { 102 }
        assertTrue(Ocr.same(a, b))
        assertTrue(Ocr.sameStrict(a, b))
    }
    @Test fun localizedChangeIsNotStatic() {
        val a = IntArray(288) { 100 }
        val b = a.copyOf().apply { this[140] = 140 }
        assertFalse(Ocr.same(a, b))
        assertFalse(Ocr.sameStrict(a, b))
    }
    @Test fun invalidSignaturesNeverMatch() {
        assertFalse(Ocr.same(intArrayOf(), intArrayOf()))
        assertFalse(Ocr.sameStrict(intArrayOf(), intArrayOf()))
        assertFalse(Ocr.same(null, intArrayOf(1)))
        assertFalse(Ocr.sameStrict(intArrayOf(1), intArrayOf(1, 2)))
    }
    @Test fun broadChangeIsNotStatic() {
        assertFalse(Ocr.same(IntArray(288) { 100 }, IntArray(288) { 106 }))
        assertFalse(Ocr.sameStrict(IntArray(288) { 100 }, IntArray(288) { 103 }))
    }
}
