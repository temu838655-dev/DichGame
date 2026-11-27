package com.dichgame.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class GameLibraryJsonTest {
    private fun item(id: String, en: String, vi: String, norm: String? = GameLibraryJson.normalize(en)) =
        JSONObject().put("id", id).put("en_raw", en).put("vi", vi).also { if (norm != null) it.put("en_normalized", norm) }

    private fun lib(vararg items: JSONObject, game: String = "Sample Game") =
        JSONObject().put("schema", 1).put("game", game).put("version", 2).put("items", JSONArray(items.toList())).toString()

    @Test fun normalizeLowercasesAndDropsExtraPunctuation() {
        assertEquals("press x to open the door", GameLibraryJson.normalize("  Press X to open the door.  "))
        assertEquals("we cant go back now", GameLibraryJson.normalize("We can’t go back now!!!"))
        assertEquals("wait what", GameLibraryJson.normalize("“Wait… what?!”"))
        assertEquals("checkpoint reached", GameLibraryJson.normalize("CHECKPOINT\n   REACHED"))
    }

    @Test fun parsesValidLibrary() {
        val l = GameLibraryJson.parse(lib(item("1", "Press X to open the door.", "Nhấn X để mở cửa.")), "Sample Game")
        assertEquals(2, l.version)
        assertEquals(listOf("Press X to open the door." to "Nhấn X để mở cửa."), l.lines)
        assertEquals(0, l.renormalized)
    }

    @Test fun roundTripsThroughFormat() {
        val src = listOf("Open the door" to "Mở cửa", "Don't move!" to "Đứng yên!")
        val back = GameLibraryJson.parse(GameLibraryJson.format("Sample Game", 3, src))
        assertEquals(src, back.lines)
        assertEquals(3, back.version)
    }

    @Test fun wrongDeclaredNormalizedIsCountedAndIgnored() {
        val l = GameLibraryJson.parse(lib(item("1", "Open the door", "Mở cửa", norm = "something else")))
        assertEquals(1, l.renormalized)
        assertEquals("Open the door", l.lines.single().first)
    }

    @Test fun rejectsBadFiles() {
        fun bad(json: String, game: String? = null) = assertTrue(runCatching { GameLibraryJson.parse(json, game) }.isFailure)
        bad(JSONObject().put("schema", 2).put("game", "Sample Game").put("items", JSONArray().put(item("1", "a b", "x"))).toString())
        bad(lib(item("1", "Open the door", "Mở cửa"), item("1", "Close the door", "Đóng cửa")))
        bad(lib(item("1", "Open the door", "")))
        bad(lib(item("1", "!!!", "Chấm than")))
        bad(lib(item("", "Open the door", "Mở cửa")))
        bad(lib(), game = null)
        bad(lib(item("1", "Open the door", "Mở cửa")), game = "Another Game")
    }

    @Test fun detectsJsonLibraryButNotCsv() {
        assertTrue(GameLibraryJson.looksLike("﻿  " + lib(item("1", "Open the door", "Mở cửa"))))
        assertFalse(GameLibraryJson.looksLike(PackText.formatScopedCsv("Sample Game", listOf("Open the door" to "Mở cửa"))))
    }

    @Test fun cloudManifestAcceptsJsonLibrary() {
        val bytes = GameLibraryJson.format("Sample Game", 1, listOf("Open the door" to "Mở cửa")).toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val manifest = JSONObject().put("schema", 1).put("game", "Sample Game").put("version", 1)
            .put("url", "https://packs.example.org/sample-game.json").put("bytes", bytes.size).put("sha256", hash).toString()
        val m = RemotePack.parse(manifest, "Sample Game")
        assertEquals("Mở cửa", RemotePack.verify(m, bytes).lines.single().second)
        assertTrue(runCatching { RemotePack.verify(m, bytes + 32) }.isFailure)
    }

    @Test fun lookupIsFastAndNeverReturnsAWrongSentence() {
        // Dữ liệu SINH GIẢ chỉ để đo tốc độ và độ an toàn khi khớp gần đúng, không phải bản dịch thật.
        val rnd = java.util.Random(7)
        val vocab = List(900) { i -> Integer.toString(rnd.nextInt(Int.MAX_VALUE), 36).take(3 + i % 6) + "ar" }
        val en = HashSet<String>()
        while (en.size < 20_000) en.add(List(6 + rnd.nextInt(10)) { vocab[rnd.nextInt(vocab.size)] }.joinToString(" ").replaceFirstChar { it.uppercase() } + ".")
        val rows = en.mapIndexed { i, s -> s to "vi-$i" }
        val library = GameLibraryJson.parse(GameLibraryJson.format("Sample Game", 1, rows), "Sample Game")
        val t0 = System.nanoTime()
        val mem = TransMemory(library.lines)
        mem.warm()
        val buildMs = (System.nanoTime() - t0) / 1e6

        fun noisy(s: String): String {
            val c = s.toCharArray()
            val k = c.indices.filter { c[it].isLetter() }
            c[k[rnd.nextInt(k.size)]] = when (c[k[rnd.nextInt(k.size)]]) { 'o' -> '0'; 'l' -> '1'; else -> 'q' }
            return String(c)
        }
        val exact = ArrayList<Double>(); val fuzzy = ArrayList<Double>(); val miss = ArrayList<Double>()
        var exactOk = 0; var fuzzyHit = 0; var fuzzyWrong = 0; var missWrong = 0
        repeat(60) { mem.lookupEx(rows[it].first) }
        val picks = rnd.ints(300, 0, rows.size).toArray()
        for (i in picks) {
            val q = rows[i]
            var t = System.nanoTime(); val h = mem.lookupEx(q.first); exact.add((System.nanoTime() - t) / 1e6)
            if (h != null && !h.fuzzy && h.vi == q.second) exactOk++
            t = System.nanoTime(); val f = mem.lookupEx(noisy(q.first)); fuzzy.add((System.nanoTime() - t) / 1e6)
            if (f != null) { fuzzyHit++; if (f.vi != q.second) fuzzyWrong++ }
            t = System.nanoTime(); val m = mem.lookupEx("Zzz unknown line number $i that is not in the library at all"); miss.add((System.nanoTime() - t) / 1e6)
            if (m != null) missWrong++
        }
        fun p(l: List<Double>, q: Double) = l.sorted()[((l.size - 1) * q).toInt()]
        println("BENCH_THUVIEN dòng=${rows.size} dựng_chỉ_mục=%.0fms | chính xác p50=%.3fms p95=%.3fms | gần đúng p50=%.3fms p95=%.3fms | không có p50=%.3fms p95=%.3fms | trúng_gần_đúng=%d/300 sai=%d"
            .format(buildMs, p(exact, .5), p(exact, .95), p(fuzzy, .5), p(fuzzy, .95), p(miss, .5), p(miss, .95), fuzzyHit, fuzzyWrong))
        assertEquals(300, exactOk)
        assertEquals(0, fuzzyWrong)
        assertEquals(0, missWrong)
        assertTrue("p95 khớp chính xác phải < 50ms", p(exact, .95) < 50.0)
        assertTrue("p95 khớp gần đúng phải < 50ms", p(fuzzy, .95) < 50.0)
        assertTrue("p95 câu không có phải < 50ms", p(miss, .95) < 50.0)
    }
}
