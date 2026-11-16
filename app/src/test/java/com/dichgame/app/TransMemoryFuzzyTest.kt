package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransMemoryFuzzyTest {
    private val pairs = listOf(
        "We need to leave before they find us." to "Ta phải đi trước khi chúng tìm ra ta.",
        "The king has been dead for three hundred years." to "Nhà vua đã chết ba trăm năm rồi.",
        "Take 5 potions and meet me at the old bridge." to "Lấy 5 bình thuốc rồi gặp ta ở cây cầu cũ.",
        "Take 6 potions and meet me at the old bridge." to "Lấy 6 bình thuốc rồi gặp ta ở cây cầu cũ.",
        "Open the left door to continue." to "Mở cánh cửa bên trái để tiếp tục.",
        "Open the right door to continue." to "Mở cánh cửa bên phải để tiếp tục.",
        "Yes." to "Vâng.",
    )
    private val mem = TransMemory(pairs)

    @Test fun exactStillWinsAndIsNotFuzzy() {
        val h = mem.lookupEx("We need to leave before they find us.")
        assertNotNull(h)
        assertTrue(!h!!.fuzzy)
    }

    @Test fun ocrNoiseStillMatches() {
        val h = mem.lookupEx("We neod to Ieave bef0re they fnd us.")
        assertNotNull(h)
        assertEquals("Ta phải đi trước khi chúng tìm ra ta.", h!!.vi)
    }

    @Test fun missingFirstWordOrTailStillMatchesLongLine() {
        val h = mem.lookupEx("The king has been dead for three hundred year")
        assertNotNull(h)
        assertEquals("Nhà vua đã chết ba trăm năm rồi.", h!!.vi)
    }

    @Test fun differentNumbersAreNeverConfused() {
        assertEquals("Lấy 5 bình thuốc rồi gặp ta ở cây cầu cũ.", mem.lookupEx("Take 5 potions and meet me at the old bridge.")!!.vi)
        assertEquals("Lấy 6 bình thuốc rồi gặp ta ở cây cầu cũ.", mem.lookupEx("Take 6 potions and meet me at the old bridge.")!!.vi)
        assertNull(mem.lookupEx("Take 7 potions and meet me at the old bridge."))
    }

    @Test fun nearIdenticalDifferentMeaningIsNotGuessed() {
        // "left"/"right": hai câu giống nhau gần hết nhưng khác nghĩa; câu OCR đọc nhập nhằng thì không chọn bừa
        val h = mem.lookupEx("Open the left door to continue.")
        assertEquals("Mở cánh cửa bên trái để tiếp tục.", h!!.vi)
        val amb = mem.lookupEx("Open the ?eft door to continue")
        if (amb != null) assertEquals("Mở cánh cửa bên trái để tiếp tục.", amb.vi)
    }

    @Test fun unrelatedAndShortTextsDoNotMatchFuzzily() {
        assertNull(mem.lookupEx("This is a completely different sentence about dragons."))
        assertNull(mem.lookupEx("Yess"))
        assertNull(mem.lookupEx("Ye"))
    }

    @Test fun cjkLinesMatchWithNoise() {
        val m = TransMemory(listOf("王様は三百年前に亡くなりました。" to "Nhà vua đã mất cách đây ba trăm năm."))
        val h = m.lookupEx("王様は三百年前に亡くなりまじた。")
        assertNotNull(h)
        assertTrue(h!!.fuzzy)
    }

    @Test fun largeMemoryBuildsAndSearchesFast() {
        val rnd = java.util.Random(7)
        val letters = "abcdefghijklmnoprstuvwy"
        val words = Array(4000) { _ -> (0 until 3 + rnd.nextInt(7)).map { letters[rnd.nextInt(letters.length)] }.joinToString("") }
        fun word(): String { val u = rnd.nextDouble(); return words[(u * u * words.size).toInt()] }
        val list = ArrayList<Pair<String, String>>()
        for (i in 0 until 60_000) {
            val n = 8 + rnd.nextInt(14)
            val s = (0 until n).joinToString(" ") { word() } + " #$i."
            list.add(s to "Bản dịch số $i")
        }
        val t0 = System.nanoTime()
        val big = TransMemory(list)
        big.warm()
        val build = (System.nanoTime() - t0) / 1_000_000
        var hits = 0
        val t1 = System.nanoTime()
        for (i in 0 until 200) {
            val src = list[rnd.nextInt(list.size)].first
            // làm lệch vài ký tự giữa câu
            val sb = StringBuilder(src)
            sb.setCharAt(src.length / 2, 'x')
            val h = big.lookupEx(sb.toString())
            if (h != null) hits++
        }
        val avg = (System.nanoTime() - t1) / 1_000_000.0 / 200
        println("[đo] 60000 câu: dựng ${build} ms, tra gần đúng trung bình ${"%.2f".format(avg)} ms/lần, tìm thấy $hits/200")
        assertTrue("dựng quá chậm: $build ms", build < 15_000)
        assertTrue("tra quá chậm: $avg ms", avg < 50.0)
        assertTrue("tìm thấy quá ít: $hits/200", hits >= 190)
    }
}
