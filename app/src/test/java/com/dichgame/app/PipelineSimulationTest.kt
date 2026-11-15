package com.dichgame.app

import com.dichgame.app.SubtitleFusion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mô phỏng một buổi chơi bằng dòng thời gian có kịch bản, chạy qua đúng các bộ lọc, bộ hợp nhất và bộ giữ nhịp của app.
 * Đây KHÔNG phải đo nhận dạng thật (OCR và Whisper trên máy thật): nó đo phần logic của app, tức là có bỏ sót câu,
 * hiện trùng, hiện quá sớm hoặc quá muộn khi hai nguồn chạy cùng lúc hay không, với độ trễ nhận dạng giả định ghi rõ ở từng ca.
 */
class PipelineSimulationTest {
    class GameLine(
        val start: Long, val speechMs: Long, val text: String,
        /** Phụ đề hiện trên màn hình bao lâu (0 = game không có phụ đề). */
        val subtitleMs: Long, val subtitleDelay: Long = 0, val speaker: String? = null,
        /** Nhận dạng giọng nói nghe ra bản này (null = nghe đúng, "" = không nghe được). */
        val heard: String? = null,
    )

    class Config(
        val scanMs: Long = 400, val ocrLatency: Long = 250, val needStable: Int = 1,
        val asrOn: Boolean = true, val asrFixedMs: Long = 360 + 300, val asrRealtimeFactor: Float = 0.35f,
        val translateMs: Long = 700, val ocrOn: Boolean = true,
        val noise: List<Pair<Long, String>> = emptyList(),
    )

    class Result(
        val lines: List<GameLine>, val shownAt: Map<GameLine, Long>, val dupes: Int, val viaOcr: Int, val viaAsr: Int, val viaMixed: Int,
        val displays: List<Pair<Long, String>>,
    ) {
        val coverage: Double get() = shownAt.size.toDouble() / lines.size
        fun latencies() = shownAt.map { (l, t) -> t - l.start }.sorted()
        fun median(): Long = latencies().let { if (it.isEmpty()) -1 else it[it.size / 2] }
        fun worst(): Long = latencies().lastOrNull() ?: -1
        fun report(name: String) = println(
            "[mô phỏng] %-34s phủ %3.0f%%  trễ giữa %4d ms  trễ tệ nhất %5d ms  trùng %d  (OCR %d, ASR %d, ghép %d)".format(
                name, coverage * 100, median(), worst(), dupes, viaOcr, viaAsr, viaMixed
            )
        )
    }

    private class Ev(val at: Long, val kind: Char, val text: String, val level: Float = 0.2f, val speechMs: Long = 0, val speaker: String? = null)

    private fun run(lines: List<GameLine>, c: Config): Result {
        val evs = ArrayList<Ev>()
        for (l in lines) {
            if (c.ocrOn && l.subtitleMs > 0) {
                val s0 = l.start + l.subtitleDelay
                val s1 = s0 + l.subtitleMs
                val first = ((s0 + c.scanMs - 1) / c.scanMs) * c.scanMs
                val lastScan = first + (c.needStable - 1) * c.scanMs
                if (lastScan < s1) evs.add(Ev(lastScan + c.ocrLatency, 'O', l.text, speaker = l.speaker))
            }
            if (c.asrOn) {
                val heard = l.heard ?: l.text
                if (heard.isNotEmpty()) {
                    val at = l.start + l.speechMs + c.asrFixedMs + (l.speechMs * c.asrRealtimeFactor).toLong()
                    evs.add(Ev(at, 'A', heard, speechMs = l.speechMs))
                }
            }
        }
        for ((t, x) in c.noise) evs.add(Ev(t, 'A', x, level = 0.01f))
        evs.sortBy { it.at }

        val fusion = SubtitleFusion()
        val pacer = SubtitlePacer()
        val displays = ArrayList<Pair<Long, String>>()
        var viaOcr = 0; var viaAsr = 0; var viaMixed = 0
        // đường dịch: mỗi dòng cần translateMs rồi qua bộ giữ nhịp
        class Tr(val readyAt: Long, val text: String, val src: Src, val replace: Boolean, val speechMs: Long)
        val translating = ArrayList<Tr>()

        fun accept(outs: List<SubtitleFusion.Out>, now: Long, speechMs: Long) {
            for (o in outs) when (o.kind) {
                Kind.SHOW, Kind.REPLACE -> translating.add(Tr(now + c.translateMs, o.text, o.src, o.kind == Kind.REPLACE, speechMs))
                else -> {}
            }
        }

        val end = (lines.maxOfOrNull { it.start + it.speechMs } ?: 0) + 20_000
        var ei = 0
        var t = 0L
        while (t <= end) {
            while (ei < evs.size && evs[ei].at <= t) {
                val e = evs[ei++]
                if (e.kind == 'O') {
                    val v = OcrGuard.check(OcrGuard.clean(e.text))
                    if (v.ok) accept(fusion.onOcr(v.text, e.speaker, t), t, 0)
                } else {
                    val v = AsrGuard.check(e.text, e.level, "en")
                    if (v.ok) accept(fusion.onAsr(v.text, t), t, e.speechMs)
                }
            }
            accept(fusion.poll(t), t, 0)
            val it = translating.iterator()
            while (it.hasNext()) {
                val tr = it.next()
                if (tr.readyAt > t) continue
                it.remove()
                val item = SubtitlePacer.Item(tr.text, tr.src, pacer.holdFor(tr.text, tr.speechMs))
                val show = pacer.submit(item, t, tr.replace)
                if (show != null) { displays.add(t to show.text); when (show.src) { Src.OCR -> viaOcr++; Src.ASR -> viaAsr++; Src.MIXED -> viaMixed++ } }
            }
            pacer.poll(t)?.let { s -> displays.add(t to s.text); when (s.src) { Src.OCR -> viaOcr++; Src.ASR -> viaAsr++; Src.MIXED -> viaMixed++ } }
            t += 50
        }

        val shownAt = HashMap<GameLine, Long>()
        var dupes = 0
        for (l in lines) {
            val hits = displays.filter { (_, txt) -> txt.lines().any { part -> OcrGuard.similarity(part, l.text) >= 0.7f } }
            if (hits.isNotEmpty()) shownAt[l] = hits.first().first
            // trùng: cùng một câu hiện hai lần cách nhau dưới 6 giây (khác với câu được nói lại thật sự)
            if (hits.size > 1 && hits.zipWithNext().any { (a, b) -> b.first - a.first < 6_000 && a.second == b.second }) dupes++
        }
        return Result(lines, shownAt, dupes, viaOcr, viaAsr, viaMixed, displays)
    }

    private val script = listOf(
        "We need to leave before they find us.", "Don't be afraid, I'm right here.", "Where did you get that sword?",
        "The bridge is out, we have to go around.", "I never wanted any of this to happen.", "Stay behind me and keep your head down.",
        "They are coming from the north gate.", "Give me the key, quickly!", "It was my fault, all of it.", "Let's finish this together.",
    )

    private fun linesOf(spacing: Long, speech: Long, subtitleMs: Long, delay: Long = 0, heard: ((Int) -> String?)? = null) =
        script.mapIndexed { i, t -> GameLine(2_000 + i * spacing + (i * 137L) % 400, speech, t, subtitleMs, delay, heard = heard?.invoke(i)) }

    @Test fun longSubtitlesOcrWorks() {
        val r = run(linesOf(6_000, 2_500, 4_000), Config())
        r.report("phụ đề dài, OCR tốt")
        assertEquals(1.0, r.coverage, 0.0)
        assertEquals(0, r.dupes)
        assertTrue("trễ giữa ${r.median()}", r.median() < 1_500)
        assertTrue(r.viaOcr >= 9)
    }

    @Test fun longSubtitlesAsrAddsNoDuplicates() {
        val r = run(linesOf(6_000, 2_500, 4_000), Config(asrOn = true))
        val ocrOnly = run(linesOf(6_000, 2_500, 4_000), Config(asrOn = false))
        assertEquals(ocrOnly.displays.size, r.displays.size)
    }

    @Test fun veryShortSubtitlesAreRecoveredByAsr() {
        // phụ đề chỉ hiện 250 ms, quét mỗi 400 ms: OCR bỏ sót nhiều câu
        val withoutAsr = run(linesOf(5_000, 2_000, 250), Config(asrOn = false))
        val withAsr = run(linesOf(5_000, 2_000, 250), Config())
        withoutAsr.report("phụ đề 250 ms, chỉ OCR")
        withAsr.report("phụ đề 250 ms, OCR + ASR")
        assertTrue(withoutAsr.coverage < 0.8)
        assertEquals(1.0, withAsr.coverage, 0.0)
        assertEquals(0, withAsr.dupes)
    }

    @Test fun gameWithoutSubtitlesUsesAsrOnly() {
        val r = run(linesOf(5_000, 2_200, 0), Config())
        r.report("game không có phụ đề (chỉ ASR)")
        assertEquals(1.0, r.coverage, 0.0)
        assertEquals(10, r.viaAsr)
        // không bao giờ hiện trước khi nhân vật nói xong câu (ASR chỉ chạy sau khi hết câu)
        assertTrue(r.shownAt.all { (l, t) -> t >= l.start + l.speechMs })
        assertTrue("trễ tệ nhất ${r.worst()}", r.worst() < 2_200 + 3_500)
    }

    @Test fun fastTalkNothingIsLost() {
        val r = run(linesOf(1_300, 1_100, 0), Config())
        r.report("nói nhanh, mỗi 1,3 giây một câu")
        assertEquals(1.0, r.coverage, 0.0)
        assertEquals(0, r.dupes)
    }

    @Test fun slowTalkIsNotShownEarly() {
        val r = run(linesOf(9_000, 6_000, 0), Config())
        r.report("nói chậm, mỗi câu 6 giây")
        assertEquals(1.0, r.coverage, 0.0)
        assertTrue(r.shownAt.all { (l, t) -> t >= l.start + l.speechMs })
    }

    @Test fun noiseAndMusicProduceNoSubtitles() {
        val noise = listOf(3_000L to "Thanks for watching!", 9_000L to "[Music]", 15_000L to "you", 21_000L to "Thank you.", 27_000L to "the the the the the the")
        val lines = listOf(GameLine(40_000, 2_000, "Move, move, move!", 0))
        val r = run(lines, Config(noise = noise))
        r.report("nhạc và tiếng ồn xen kẽ")
        assertEquals(1, r.displays.size)
        assertEquals("Move, move, move!", r.displays[0].second)
    }

    @Test fun imperfectAsrStillMatchesOcrNoDuplicates() {
        // ASR nghe sai một vài chữ mỗi câu, OCR đọc đúng: không được hiện hai lần
        val r = run(linesOf(6_000, 2_500, 4_000, heard = { i -> script[i].replace("the", "a").replace("we", "he") }), Config())
        r.report("ASR nghe sai vài chữ + OCR")
        assertEquals(1.0, r.coverage, 0.0)
        assertEquals(0, r.dupes)
    }

    @Test fun subtitleAppearsLateOcrConfirmsHeldAsr() {
        // phụ đề hiện trễ 700 ms sau khi bắt đầu nói; ASR xong sau ~3.8 s, OCR đã có từ lâu: chỉ hiện một lần
        val r = run(linesOf(7_000, 2_500, 3_500, delay = 700), Config())
        r.report("phụ đề hiện trễ so với giọng nói")
        assertEquals(1.0, r.coverage, 0.0)
        assertEquals(0, r.dupes)
    }

    @Test fun partialSubtitleThenFullSpeechIsFused() {
        // phụ đề chỉ hiện nửa câu (bị che), ASR nghe đủ: ghép lại thành câu đủ
        val full = "We need to leave this place before the guards find us tonight."
        val lines = listOf(GameLine(2_000, 3_500, full, 1_200))
        val r = run(lines, Config(), partialOcr = "We need to leave this place before")
        r.report("OCR nửa câu + ASR đủ câu")
        assertEquals(1.0, r.coverage, 0.0)
    }

    private fun run(lines: List<GameLine>, c: Config, partialOcr: String): Result {
        // dùng cùng bộ máy nhưng ép OCR đọc bản thiếu
        val partial = lines.map { GameLine(it.start, it.speechMs, partialOcr, it.subtitleMs, it.subtitleDelay, it.speaker, heard = it.text) }
        val r = run(partial, c)
        val shownAt = HashMap<GameLine, Long>()
        for (l in lines) {
            val hit = r.displays.firstOrNull { (_, t) -> OcrGuard.similarity(t, l.text) >= 0.7f }
            if (hit != null) shownAt[l] = hit.first
        }
        return Result(lines, shownAt, 0, r.viaOcr, r.viaAsr, r.viaMixed, r.displays)
    }
}
