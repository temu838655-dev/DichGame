package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePacerTest {
    private fun item(t: String, src: Src = Src.OCR, hold: Long = 3_000) = SubtitlePacer.Item(t, src, hold)

    @Test fun holdScalesWithTextAndSpeech() {
        val p = SubtitlePacer()
        assertEquals(2_000L, p.holdFor("Đi."))
        assertTrue(p.holdFor("Chúng ta cần rời khỏi nơi này trước khi lính canh tìm thấy chúng ta tối nay") > 3_000)
        assertEquals(8_000L, p.holdFor("x".repeat(500)))
        // nhân vật nói chậm 4 giây một câu ngắn: giữ ít nhất bằng thời gian nói
        assertTrue(p.holdFor("Đi nào.", speechMs = 4_000) >= 4_000)
    }

    @Test fun newCaptionImmediatelyReplacesOlderTimers() {
        val p = SubtitlePacer()
        p.showImmediately(item("a"), 100)
        p.submit(item("b"), 150)
        val duration = p.holdFor("c", fixedSeconds = 5)
        assertEquals("c", p.showImmediately(item("c", hold = duration), 200).text)
        assertEquals(3100L, p.previousUntil)
        assertEquals(0, p.queued)
        assertFalse(p.mayHide(5199))
        assertTrue(p.mayHide(5200))
        assertNull(p.poll(5200))
        p.showImmediately(item("d"), 6000)
        assertEquals(0L, p.previousUntil)
    }

    @Test fun firstItemShowsImmediately() {
        val p = SubtitlePacer()
        assertEquals("a", p.submit(item("a"), 0)?.text)
    }

    @Test fun nextItemWaitsUntilPreemptTimeThenShows() {
        val p = SubtitlePacer()
        p.submit(item("a"), 0)
        assertNull(p.submit(item("b"), 300))
        assertEquals(1, p.queued)
        assertNull(p.poll(500))
        assertEquals("b", p.poll(900)?.text)
        assertEquals(0, p.queued)
    }

    @Test fun lateItemShowsImmediately() {
        val p = SubtitlePacer()
        p.submit(item("a"), 0)
        assertEquals("b", p.submit(item("b"), 5_000)?.text)
    }

    @Test fun replaceIsImmediate() {
        val p = SubtitlePacer()
        p.submit(item("a"), 0)
        assertEquals("a full", p.submit(item("a full"), 100, replace = true)?.text)
    }

    @Test fun overflowMergesInsteadOfDropping() {
        val p = SubtitlePacer(maxQueue = 2)
        p.submit(item("a", hold = 4_000), 0)
        p.submit(item("b"), 100)
        p.submit(item("c"), 200)
        p.submit(item("d"), 300)
        assertEquals(2, p.queued)
        val first = p.poll(2_000)
        assertNotNull(first)
        assertTrue(first!!.text.contains("b") && first.text.contains("c"))
        val second = p.poll(4_000)
        assertEquals("d", second?.text)
    }

    @Test fun neverLosesAnyLine() {
        val p = SubtitlePacer()
        val shown = ArrayList<String>()
        fun add(i: SubtitlePacer.Item?) { i?.let { shown.add(it.text) } }
        var t = 0L
        val lines = (1..12).map { "dòng $it" }
        for (l in lines) { add(p.submit(item(l, Src.ASR, 2_500), t)); t += 250; add(p.poll(t)) }
        while (p.queued > 0 && t < 120_000) { t += 100; add(p.poll(t)) }
        val all = shown.joinToString("\n").lines().toSet()
        assertTrue(lines.all { it in all })
    }

    @Test fun mayHideOnlyAfterHold() {
        val p = SubtitlePacer()
        p.submit(item("a", hold = 3_000), 1_000)
        assertFalse(p.mayHide(2_000))
        assertTrue(p.mayHide(4_000))
        assertEquals(4_000L, p.hideAt())
        p.cleared()
        assertTrue(p.mayHide(0))
    }

    @Test fun speechRateEstimate() {
        val r = SubtitlePacer.SpeechRate()
        r.add("x".repeat(20), 2_000)    // 10 ký tự mỗi giây
        assertFalse(r.fast); assertFalse(r.slow)
        val fast = SubtitlePacer.SpeechRate()
        repeat(5) { fast.add("x".repeat(40), 2_000) }
        assertTrue(fast.fast)
        val slow = SubtitlePacer.SpeechRate()
        repeat(5) { slow.add("x".repeat(10), 2_000) }
        assertTrue(slow.slow)
        val none = SubtitlePacer.SpeechRate()
        none.add("hi", 100)
        assertEquals(0f, none.cps, 0f)
    }
}
