package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGuardTest {
    private fun ok(s: String) = OcrGuard.check(OcrGuard.clean(s)).ok

    @Test fun realDialoguePasses() {
        assertTrue(ok("I don't think that's a good idea."))
        assertTrue(ok("Don't move!"))
        assertTrue(ok("Where were you last night?"))
        assertTrue(ok("おはよう、元気？"))
        assertTrue(ok("你今天想去哪里？"))
        assertTrue(ok("Mission 2: Find the McDonalds."))
        assertTrue(ok("Ich habe 3 Schwerter und 12 Pfeile."))
    }

    @Test fun garbageIsRejected() {
        assertFalse(ok(""))
        assertFalse(ok("   \n  "))
        assertFalse(ok("12:45:30"))
        assertFalse(ok("100/100  250"))
        assertFalse(ok("|||| ---- ////"))
        assertFalse(ok("IIIIIIII"))
        assertFalse(ok("llllll"))
        assertFalse(ok("xzkqwpltrn"))
        assertFalse(ok("ghjkl mnbvc xcvbn"))
        assertFalse(ok("aBcDeFgH iJkLmN"))
        assertFalse(ok("@#\$%^&*()+=<>"))
    }

    @Test fun lowConfidenceIsRejected() {
        assertFalse(OcrGuard.check("Hello there my friend", conf = 0.2f).ok)
        assertTrue(OcrGuard.check("Hello there my friend", conf = 0.9f).ok)
    }

    @Test fun shortWordsPassButAreFlaggedForConfirmation() {
        val v = OcrGuard.check("No")
        assertTrue(v.ok)
        assertTrue(v.short)
        assertFalse(OcrGuard.check("Run away now").short)
    }

    @Test fun cleanStripsEdgeJunkAndInvisibleChars() {
        assertEquals("Hello world", OcrGuard.clean("  | Hello​   world _ "))
        assertEquals("a\nb", OcrGuard.clean("a\n\n  b  "))
    }

    @Test fun similarityGroupsNearIdenticalReads() {
        assertTrue(OcrGuard.similarity("I can't believe it.", "I cant believe it") >= 0.9f)
        assertTrue(OcrGuard.similarity("I can't believe it.", "Let's go to the castle") < 0.5f)
        assertEquals(1f, OcrGuard.similarity("", ""), 0f)
        assertTrue(OcrGuard.sameText("Hello, World!", "hello world"))
    }
}
