package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechQueueTest {
    @Test fun readsInOrderWithoutDropping() {
        val q = SpeechQueue<String>()
        assertTrue(q.add("a", 0).isEmpty())
        assertTrue(q.add("b", 10).isEmpty())
        assertEquals("a", q.poll())
        assertEquals("b", q.poll())
        assertNull(q.poll())
    }

    @Test fun overflowDropsOldestAndKeepsNewest() {
        val q = SpeechQueue<String>(maxPending = 2)
        q.add("a", 0); q.add("b", 1)
        assertEquals(listOf("a"), q.add("c", 2))
        assertEquals(listOf("b", "c"), listOf(q.poll(), q.poll()))
    }

    @Test fun staleLinesAreDroppedOnlyFromTheFront() {
        val q = SpeechQueue<String>(maxPending = 5, staleMs = 10_000)
        q.add("cũ", 0); q.add("mới", 9_000)
        assertTrue(q.dropStale(10_000).isEmpty())          // đúng 10 giây: chưa quá
        assertEquals(listOf("cũ"), q.dropStale(10_001))
        assertEquals("mới", q.peek())
        assertTrue(q.dropStale(15_000).isEmpty())          // "mới" mới chờ 6 giây
    }

    @Test fun removeAllAndClearReturnTheRemovedItems() {
        val q = SpeechQueue<String>(maxPending = 5)
        q.add("a1", 0); q.add("b", 0); q.add("a2", 0)
        assertEquals(listOf("a1", "a2"), q.removeAll { it.startsWith("a") })
        assertEquals("b", q.firstOrNull { it == "b" })
        assertEquals(listOf("b"), q.clear())
        assertTrue(q.isEmpty())
        assertEquals(0, q.size)
    }

    @Test fun newestIsTheLastAdded() {
        val q = SpeechQueue<String>(maxPending = 5)
        assertNull(q.newest())
        q.add("a", 0); q.add("b", 0)
        assertEquals("b", q.newest())
    }

    @Test fun backlogSpeedsUpOnlyWhenAutoFastIsOn() {
        assertEquals(1f, SpeechPolicy.speedFactor(0, true), 0f)
        assertEquals(1.2f, SpeechPolicy.speedFactor(1, true), 0f)
        assertEquals(1.4f, SpeechPolicy.speedFactor(2, true), 0f)
        assertEquals(1.6f, SpeechPolicy.speedFactor(7, true), 0f)
        assertEquals(1f, SpeechPolicy.speedFactor(3, false), 0f)
    }

    @Test fun normIgnoresPunctuationCaseAndSpaces() {
        assertEquals(SpeechPolicy.norm("Xin chào!"), SpeechPolicy.norm("  xin   CHÀO… "))
        assertEquals("", SpeechPolicy.norm("... !?"))
    }

    @Test fun draftDetectionNeedsARealLongerPrefix() {
        val a = SpeechPolicy.norm("Xin chào các")
        val b = SpeechPolicy.norm("Xin chào các bạn")
        assertTrue(SpeechPolicy.isDraftOf(a, b))
        assertFalse(SpeechPolicy.isDraftOf(b, a))
        assertFalse(SpeechPolicy.isDraftOf(b, b))                       // giống hệt không phải bản dở
        assertFalse(SpeechPolicy.isDraftOf("ừ", SpeechPolicy.norm("ừ rồi")))   // quá ngắn: không đoán
        assertFalse(SpeechPolicy.isDraftOf(SpeechPolicy.norm("Đi thôi nào"), SpeechPolicy.norm("Chờ đã, đi thôi nào")))
    }

    @Test fun limitsAreSane() {
        assertTrue(SpeechPolicy.MAX_PENDING in 1..4)
        assertTrue(SpeechPolicy.STALE_MS in 3_000L..30_000L)
        assertTrue(SpeechPolicy.SYSTEM_MAX_BACKLOG >= 2)
    }
}
