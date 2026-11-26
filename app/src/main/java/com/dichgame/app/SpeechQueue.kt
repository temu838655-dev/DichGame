package com.dichgame.app

/** Quy tắc đọc to khi lời thoại dồn lại. Không phụ thuộc Android nên có kiểm thử đơn vị. */
object SpeechPolicy {
    /** Số câu tối đa chờ sau câu đang đọc (giọng AI và giọng offline). Dồn thêm thì bỏ câu cũ nhất. */
    const val MAX_PENDING = 2
    /** Câu chờ lâu hơn mức này thì bỏ: lời thoại trong game đã qua, đọc nữa chỉ làm lệch. */
    const val STALE_MS = 10_000L
    /** Giọng máy: tổng số câu đang đọc và đang chờ tới mức này thì xóa hàng, đọc câu mới nhất. */
    const val SYSTEM_MAX_BACKLOG = 3
    /** Hai lần gửi cùng một câu trong chừng này thời gian thì chỉ đọc một lần (chữ OCR nháy, bản dịch được sửa lại). */
    const val DUPLICATE_MS = 6_000L

    /** Hệ số đọc nhanh khi sau câu này còn [backlog] câu đang chờ (cần bật "tự đọc nhanh"). Không chờ thì đọc đúng tốc độ. */
    fun speedFactor(backlog: Int, autoFast: Boolean): Float = when {
        !autoFast || backlog <= 0 -> 1f
        backlog == 1 -> 1.2f
        backlog == 2 -> 1.4f
        else -> 1.6f
    }

    fun playbackDurationMs(durationMs: Long, appliedSpeed: Float): Long =
        kotlin.math.ceil(durationMs / appliedSpeed.toDouble()).toLong().coerceAtLeast(1L)

    /** Dạng so sánh của một câu: chỉ chữ và số, viết thường (bỏ dấu câu, khoảng trắng). */
    fun norm(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) if (ch.isLetterOrDigit()) sb.append(ch.lowercaseChar())
        return sb.toString()
    }

    /** [older] là bản chưa đầy đủ của [newer] (chữ đang hiện dần): câu cũ chưa đọc thì khỏi đọc nữa. Câu quá ngắn không tính. */
    fun isDraftOf(older: String, newer: String): Boolean = older.length >= 4 && newer.length > older.length && newer.startsWith(older)
}

/**
 * Hàng chờ đọc to: câu mới không cắt câu đang đọc mà xếp hàng đọc sau. Dồn quá [maxPending] câu thì bỏ câu cũ nhất,
 * câu chờ quá [staleMs] thì bỏ. Các hàm trả về những câu bị bỏ để người gọi giải phóng (âm thanh đã tạo sẵn...).
 */
class SpeechQueue<T : Any>(private val maxPending: Int = SpeechPolicy.MAX_PENDING, private val staleMs: Long = SpeechPolicy.STALE_MS) {
    private class Entry<T>(val item: T, val at: Long)

    private val q = java.util.ArrayDeque<Entry<T>>()

    val size: Int get() = q.size
    fun isEmpty(): Boolean = q.isEmpty()

    /** Xếp [item] vào cuối hàng, trả về các câu bị bỏ vì hàng đầy (cũ nhất trước). */
    fun add(item: T, now: Long): List<T> {
        q.addLast(Entry(item, now))
        val out = ArrayList<T>()
        while (q.size > maxPending) out.add(q.removeFirst().item)
        return out
    }

    /** Bỏ các câu đã chờ quá [staleMs] ở đầu hàng, trả về chúng. */
    fun dropStale(now: Long): List<T> {
        val out = ArrayList<T>()
        while (true) {
            val h = q.peekFirst() ?: break
            if (now - h.at <= staleMs) break
            out.add(q.removeFirst().item)
        }
        return out
    }

    fun peek(): T? = q.peekFirst()?.item
    fun poll(): T? = q.pollFirst()?.item
    fun newest(): T? = q.peekLast()?.item
    fun firstOrNull(pred: (T) -> Boolean): T? { for (e in q) if (pred(e.item)) return e.item; return null }

    /** Bỏ mọi câu thỏa [pred], trả về chúng. */
    fun removeAll(pred: (T) -> Boolean): List<T> {
        val out = ArrayList<T>()
        val it = q.iterator()
        while (it.hasNext()) { val e = it.next(); if (pred(e.item)) { out.add(e.item); it.remove() } }
        return out
    }

    fun clear(): List<T> {
        val out = ArrayList<T>(q.size)
        for (e in q) out.add(e.item)
        q.clear()
        return out
    }
}
