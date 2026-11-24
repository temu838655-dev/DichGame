package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class MenuSceneGuardTest {
    @Test fun translationPixelsDoNotTriggerSceneChange() {
        val a = IntArray(64*32) { 100 }
        val g = requireNotNull(MenuSceneGuard.create(a,640,320,listOf(intArrayOf(100,100,300,200))))
        val frame = a.copyOf()
        for (y in 10 until 20) for (x in 10 until 30) frame[y*64+x] = 230
        repeat(3) { assertFalse(g.changed(frame)) }
    }
    @Test fun sustainedSceneChangeClearsOldTranslation() {
        val a = IntArray(64*32) { 100 }
        val g = requireNotNull(MenuSceneGuard.create(a,640,320,emptyList()))
        val next = IntArray(a.size) { 160 }
        assertFalse(g.changed(next))
        assertTrue(g.changed(next))
    }
    @Test fun briefFlashIsIgnored() {
        val a = IntArray(64*32) { 100 }
        val g = requireNotNull(MenuSceneGuard.create(a,640,320,emptyList()))
        assertFalse(g.changed(IntArray(a.size) { 160 }))
        assertFalse(g.changed(a))
        assertFalse(g.changed(IntArray(a.size) { 160 }))
    }
}
