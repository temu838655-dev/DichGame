package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class NativePaletteTest {
    @Test fun lightChatBubbleKeepsItsOwnBackground() {
        val bg = 0xffd3e5f6.toInt()
        val ink = 0xff223344.toInt()
        val result = requireNotNull(NativePalette.estimate(List(270) { bg } + List(66) { ink }))
        assertEquals(bg, result.first)
        assertEquals(ink, result.second)
        assertTrue(NativePalette.contrast(result.first,result.second) >= 4.5)
    }

    @Test fun darkMenuPreservesLightText() {
        val bg = 0xff202c39.toInt()
        val ink = 0xffeeeeee.toInt()
        assertEquals(bg to ink, NativePalette.estimate(List(280) { bg } + List(56) { ink }))
    }

    @Test fun mediumBackgroundStillGetsReadableText() {
        val bg = 0xff808080.toInt()
        val result = requireNotNull(NativePalette.estimate(List(280) { bg } + List(56) { 0xffbbbbbb.toInt() }))
        assertTrue(NativePalette.contrast(result.first,result.second) >= 4.5)
    }

    @Test fun diversePhotoIsNotCoveredBySolidPatch() {
        val colors = (0 until 512).map { i ->
            (0xff shl 24) or ((i/64*32) shl 16) or ((i/8%8*32) shl 8) or (i%8*32)
        }
        assertNull(NativePalette.estimate(colors))
        assertNull(NativePalette.estimate(listOf(0xff000000.toInt())))
    }
}
