package com.dichgame.app

import java.util.Locale

/** Nguồn của một dòng phụ đề: đọc từ màn hình, nghe từ âm thanh, hoặc ghép cả hai. */
enum class Src { OCR, ASR, MIXED }

/**
 * Hợp nhất hai nguồn lời thoại: chữ đọc từ màn hình (OCR) và chữ nghe từ âm thanh game (ASR).
 *
 * - OCR là nguồn chính vì đúng chính tả và tên riêng. Câu ASR trùng với câu OCR vừa đọc thì bỏ, không hiện hai lần.
 * - Khi OCR đang đọc tốt mà ASR nghe ra một câu chưa có trên màn hình, câu ASR được giữ lại một lúc chờ OCR xác nhận
 *   (tránh hiện bản nhận dạng sai khi phụ đề sắp hiện); hết hạn mà vẫn chưa có thì mới hiện (phụ đề thiếu hoặc quá ngắn).
 * - Khi OCR không đọc được gì, câu ASR hiện ngay.
 * - OCR chỉ đọc được nửa đầu câu mà ASR nghe cả câu thì ghép: lấy câu ASR nhưng giữ cách viết của OCR ở phần chồng nhau.
 *   Chỉ ghép khi phần chồng nhau đủ dài để chắc chắn là cùng một câu.
 *
 * Không phụ thuộc Android; thời gian truyền vào từng lời gọi để kiểm thử được.
 */
class SubtitleFusion(
    private val shortWindowMs: Long = 5_000L,
    private val longWindowMs: Long = 12_000L,
    private val confirmHoldMs: Long = 1_300L,
    private val healthyMs: Long = 6_000L,
    /** Màn hình không còn câu thoại liên tục từng này thì câu đã hiện coi như đã biến mất: hiện lại cùng chữ là lần hiển thị mới. */
    private val goneAfterMs: Long = 1_200L,
) {
    enum class Kind { SHOW, REPLACE, DROP, HOLD }

    class Out(val kind: Kind, val text: String, val speaker: String?, val src: Src, val reason: String)

    private inner class Seen(val text: String, val speaker: String?, val src: Src, var at: Long) {
        /** Chữ này đã biến mất khỏi màn hình (đủ lâu, không phải nháy một lần). Chỉ ảnh hưởng tới OCR; câu nghe được vẫn coi là trùng. */
        var gone = false
        /** Câu dài được nhớ lâu hơn: nhận dạng giọng nói chỉ có sau khi nhân vật nói xong, có thể muộn hơn chữ hiện trên màn hình nhiều giây. */
        val windowMs: Long = if (OcrGuard.key(text).length < 14) shortWindowMs else longWindowMs
    }
    private class Pending(val text: String, val deadline: Long)

    private val shown = ArrayList<Seen>()
    private val pending = ArrayList<Pending>()
    private var lastOcrAt = Long.MIN_VALUE / 2
    private var emptySince = -1L

    /** Số lần mỗi quyết định đã xảy ra, để thống kê. */
    val counts = HashMap<String, Int>()

    private fun note(k: String) { counts[k] = (counts[k] ?: 0) + 1 }

    private fun prune(now: Long) {
        shown.removeAll { now - it.at > it.windowMs }
    }

    /** Một câu OCR đã ổn định (đã qua bộ lọc chữ rác và chống nhấp nháy). */
    fun onOcr(text: String, speaker: String?, now: Long): List<Out> {
        prune(now)
        lastOcrAt = now
        emptySince = -1L
        val out = ArrayList<Out>()
        // câu ASR đang chờ mà OCR vừa xác nhận: bỏ, đã có bản chữ trên màn hình
        val it = pending.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (relation(text, p.text) != Rel.NONE) { it.remove(); note("asr_xac_nhan_boi_ocr") }
        }
        for (s in shown) {
            val r = relation(text, s.text)
            if (r == Rel.NONE) continue
            if (s.gone && s.src != Src.ASR) {
                // câu giống hệt đã biến mất khỏi màn hình rồi hiện lại (nhắc lại lời thoại, xem lại nhật ký...): hiển thị mới, không phải trùng
                shown.remove(s)
                shown.add(Seen(text, speaker, Src.OCR, now))
                note("ocr_hien_lai")
                out.add(Out(Kind.SHOW, text, speaker, Src.OCR, "hiện lại sau khi biến mất"))
                return out
            }
            if (s.src == Src.ASR && r == Rel.A_EXTENDS_B) {
                // câu ASR đã hiện chỉ là nửa câu, OCR có đủ: thay bằng bản đủ
                shown.remove(s)
                shown.add(Seen(text, speaker, Src.OCR, now))
                note("ocr_thay_asr_thieu")
                out.add(Out(Kind.REPLACE, text, speaker, Src.OCR, "OCR có đủ câu hơn bản nghe được"))
                return out
            }
            if (s.src != Src.ASR && r == Rel.A_EXTENDS_B && s.speaker == speaker) {
                // chữ hiện dần từng chữ (hiệu ứng máy đánh chữ) hoặc lần đọc trước thiếu: bản dài hơn mới là câu đầy đủ, không được coi là trùng
                shown.remove(s)
                shown.add(Seen(text, speaker, Src.OCR, now))
                note("ocr_mo_rong")
                out.add(Out(Kind.REPLACE, text, speaker, Src.OCR, "chữ trên màn hình dài ra"))
                return out
            }
            s.at = now
            note("ocr_trung")
            out.add(Out(Kind.DROP, text, speaker, Src.OCR, "đã hiện"))
            return out
        }
        shown.add(Seen(text, speaker, Src.OCR, now))
        note("ocr_hien")
        out.add(Out(Kind.SHOW, text, speaker, Src.OCR, ""))
        return out
    }

    /**
     * Lần quét không thấy câu thoại nào (sau khi lọc chữ rác). Vắng đủ [goneAfterMs] thì các câu OCR đã hiện được đánh dấu là đã biến mất.
     * Gọi lặp lại được (mỗi nhịp quét); lần gọi đầu ghi mốc thời gian.
     */
    fun onScreenEmpty(now: Long) {
        if (emptySince < 0) emptySince = now
        if (now - emptySince >= goneAfterMs) for (s in shown) if (s.src != Src.ASR) s.gone = true
    }

    /** Một câu nhận dạng từ âm thanh (đã qua AsrGuard). */
    fun onAsr(text: String, now: Long): List<Out> {
        prune(now)
        for (s in shown) {
            val r = relation(text, s.text)
            if (r == Rel.NONE) continue
            if (s.src == Src.OCR && r == Rel.A_EXTENDS_B && s.speaker == null && extendsEnough(s.text, text)) {
                // OCR mới đọc được nửa câu, ASR nghe đủ: ghép
                val fused = fuse(s.text, text)
                shown.remove(s)
                shown.add(Seen(fused, s.speaker, Src.MIXED, now))
                note("ghep_ocr_asr")
                return listOf(Out(Kind.REPLACE, fused, s.speaker, Src.MIXED, "OCR đọc được nửa đầu, ASR nghe phần còn lại"))
            }
            s.at = now
            note("asr_trung")
            return listOf(Out(Kind.DROP, text, null, Src.ASR, "đã có " + (if (s.src == Src.OCR) "OCR" else "ASR")))
        }
        for (p in pending) if (relation(text, p.text) != Rel.NONE) { note("asr_trung_cho"); return listOf(Out(Kind.DROP, text, null, Src.ASR, "đang chờ")) }

        if (now - lastOcrAt <= healthyMs) {
            pending.add(Pending(text, now + confirmHoldMs))
            note("asr_cho_ocr")
            return listOf(Out(Kind.HOLD, text, null, Src.ASR, "chờ OCR xác nhận"))
        }
        shown.add(Seen(text, null, Src.ASR, now))
        note("asr_hien")
        return listOf(Out(Kind.SHOW, text, null, Src.ASR, "OCR không đọc được"))
    }

    /** Gọi định kỳ: trả các câu ASR đã chờ hết hạn mà OCR không xác nhận. */
    fun poll(now: Long): List<Out> {
        if (pending.isEmpty()) return emptyList()
        prune(now)
        val out = ArrayList<Out>()
        val it = pending.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (now < p.deadline) continue
            it.remove()
            shown.add(Seen(p.text, null, Src.ASR, now))
            note("asr_hien_sau_cho")
            out.add(Out(Kind.SHOW, p.text, null, Src.ASR, "OCR không có câu này"))
        }
        return out
    }

    fun clear() { shown.clear(); pending.clear(); lastOcrAt = Long.MIN_VALUE / 2; emptySince = -1L }

    // ---- so sánh hai câu ----

    internal enum class Rel { NONE, SAME, A_EXTENDS_B, B_EXTENDS_A }

    private fun tokens(s: String): List<String> =
        s.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun unspaced(s: String) = s.any { LangDetect.scriptOf(it).let { c -> c == 'H' || c == 'K' || c == 'G' || c == 'T' } }

    /** Quan hệ giữa câu [a] và [b]: giống nhau, hay một câu là phần đầu/giữa của câu kia. */
    internal fun relation(a: String, b: String): Rel {
        val ka = OcrGuard.key(a); val kb = OcrGuard.key(b)
        if (ka.isEmpty() || kb.isEmpty()) return Rel.NONE
        if (ka == kb) return Rel.SAME
        val cjk = unspaced(a) || unspaced(b)
        val minLen = if (cjk) 3 else 8
        val short = if (ka.length <= kb.length) ka else kb
        val long = if (ka.length <= kb.length) kb else ka
        if (OcrGuard.similarity(ka, kb) >= 0.78f && minOf(ka.length, kb.length) * 100 / maxOf(ka.length, kb.length) >= 70) return Rel.SAME
        if (short.length >= minLen && short.length * 100 / long.length >= 30) {
            val inside = long.contains(short) || fuzzyPrefix(long, short)
            if (inside) return if (ka.length > kb.length) Rel.A_EXTENDS_B else Rel.B_EXTENDS_A
        }
        return Rel.NONE
    }

    /** [short] khớp gần đúng với phần đầu của [long] (nhận dạng có thể sai một vài chữ). */
    private fun fuzzyPrefix(long: String, short: String): Boolean {
        if (long.length < short.length) return false
        val head = long.take(short.length)
        return OcrGuard.similarity(head, short) >= 0.86f
    }

    /** Phần mở rộng đủ dài để tin chắc là cùng một câu và có thêm nội dung: thêm ít nhất 2 từ (hoặc 3 ký tự CJK). */
    private fun extendsEnough(ocr: String, asr: String): Boolean {
        val cjk = unspaced(ocr) || unspaced(asr)
        if (cjk) {
            val a = OcrGuard.key(ocr); val b = OcrGuard.key(asr)
            return a.length >= 4 && b.length - a.length >= 3
        }
        val t1 = tokens(ocr); val t2 = tokens(asr)
        return t1.size >= 3 && t2.size - t1.size >= 2
    }

    /** Phần đầu lấy chữ của OCR (đúng chính tả), phần còn lại lấy từ ASR. */
    internal fun fuse(ocr: String, asr: String): String {
        val cjk = unspaced(ocr) || unspaced(asr)
        if (cjk) {
            val k = OcrGuard.key(ocr).length
            var seen = 0
            var cut = asr.length
            for (i in asr.indices) {
                if ((asr[i]).isLetterOrDigit()) seen++
                if (seen >= k) { cut = i + 1; break }
            }
            return ocr.trimEnd() + asr.substring(cut).trimEnd()
        }
        val n = tokens(ocr).size
        val parts = asr.trim().split(Regex("\\s+"))
        var count = 0
        var idx = 0
        for (i in parts.indices) {
            if (tokens(parts[i]).isNotEmpty()) count++
            if (count >= n) { idx = i + 1; break }
        }
        val rest = parts.drop(idx).joinToString(" ")
        return (ocr.trimEnd().trimEnd('.', '!', '?', '…', ',') + " " + rest).trim()
    }
}
