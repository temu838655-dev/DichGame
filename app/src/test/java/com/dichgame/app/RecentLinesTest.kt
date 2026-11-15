package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentLinesTest {
    @Test fun newestFirstAndCapped() {
        RecentLines.clear()
        for (i in 1..60) RecentLines.add("Line number $i here", "Câu số $i", null, false, i.toLong())
        val s = RecentLines.snapshot()
        assertEquals(40, s.size)
        assertEquals("Line number 60 here", s.first().orig)
        assertEquals("Line number 21 here", s.last().orig)
    }

    @Test fun sameOriginalReplacesOlderEntry() {
        RecentLines.clear()
        RecentLines.add("Stay close to me.", "Ở gần tôi.", null, true, 1)
        RecentLines.add("Something else entirely.", "Chuyện khác.", "Anna", false, 2)
        RecentLines.add("stay close to me", "Đứng gần tôi.", null, false, 3)
        val s = RecentLines.snapshot()
        assertEquals(2, s.size)
        assertEquals("Đứng gần tôi.", s[0].vi)
        assertTrue(!s[0].unsure)
        assertEquals("Anna", s[1].speaker)
    }

    @Test fun blanksAreIgnoredAndClearWorks() {
        RecentLines.clear()
        RecentLines.add("  ", "x", null, false, 1)
        RecentLines.add("Hello there friend", "", null, false, 1)
        RecentLines.add("...", "chấm", null, false, 1)
        assertTrue(RecentLines.snapshot().isEmpty())
        RecentLines.add("Hello there friend", "Chào bạn", null, false, 1)
        RecentLines.clear()
        assertTrue(RecentLines.snapshot().isEmpty())
    }
}
