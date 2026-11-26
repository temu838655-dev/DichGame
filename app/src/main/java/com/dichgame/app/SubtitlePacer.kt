package com.dichgame.app

import kotlin.math.max
import kotlin.math.min

/**
 * Nhịp hiện phụ đề: mỗi dòng ở lại đủ lâu để đọc hết, dòng mới không đè lên dòng cũ ngay lập tức (tránh nhấp nháy),
 * nhưng cũng không bắt dòng mới chờ quá lâu. Tốc độ đọc và tốc độ nói của nhân vật đều được tính.
 * Không phụ thuộc Android; thời gian truyền vào từng lời gọi.
 */
class SubtitlePacer(
    private val readCps: Float = 17f,
    private val minHoldMs: Long = 2_000L,
    private val maxHoldMs: Long = 8_000L,
    private val maxQueue: Int = 2,
    /** Cách gộp phần dữ liệu đính kèm của hai dòng khi hàng chờ đầy (nếu không đặt thì lấy của dòng sau). */
    private val mergePayload: ((Any?, Any?) -> Any?)? = null,
) {
    class Item(val text: String, val src: Src, val holdMs: Long, val speaker: String? = null, val payload: Any? = null)

    private class Queued(val item: Item, val readyAt: Long)

    private var current: Item? = null
    var previousUntil = 0L
        private set
    private var shownAt = 0L
    private val queue = ArrayList<Queued>()

    /** Dòng đang hiện (nếu có). */
    val showing: Item? get() = current

    /** Số dòng đang chờ tới lượt. */
    val queued: Int get() = queue.size

    /**
     * Thời gian giữ một dòng: đủ để đọc (khoảng [readCps] ký tự mỗi giây, cộng một nhịp phản ứng) và không ngắn hơn
     * thời gian nhân vật nói câu đó ([speechMs], 0 nếu không biết).
     */
    fun holdFor(text: String, speechMs: Long = 0L, fixedSeconds: Int = 0): Long {
        if (fixedSeconds in 2..8) return fixedSeconds * 1000L
        val chars = text.count { !it.isWhitespace() }
        val read = (chars / readCps * 1000f).toLong() + 600L
        return max(min(max(read, (speechMs * 1.05f).toLong()), maxHoldMs), minHoldMs)
    }

    /** Sau bao lâu một dòng đang hiện có thể bị dòng mới thay: lời nghe được (đã nói xong) giữ lâu hơn chữ trên màn hình (game đã đổi). */
    private fun preemptAfter(i: Item): Long = when (i.src) {
        Src.OCR -> min(700L, i.holdMs * 2 / 5)
        else -> min(1_500L, i.holdMs * 3 / 5)
    }

    /**
     * Gửi một dòng mới. Trả về dòng cần hiện ngay, hoặc null nếu dòng đó được xếp hàng chờ.
     * [replace] = true khi dòng này sửa lại dòng đang hiện (cùng một câu, bản đầy đủ hơn): thay ngay, không xếp hàng.
     */
    fun submit(item: Item, now: Long, replace: Boolean = false): Item? {
        val cur = current
        if (cur == null || replace) return start(item, now)
        if (queue.isEmpty() && now >= shownAt + preemptAfter(cur)) return start(item, now)
        // chưa tới lúc thay: xếp hàng. Hàng đầy thì gộp hai dòng cũ nhất thành một thẻ hai dòng, không bỏ dòng nào
        queue.add(Queued(item, shownAt + preemptAfter(cur)))
        while (queue.size > maxQueue) {
            val a = queue.removeAt(0)
            val b = queue.removeAt(0)
            val merged = Item(a.item.text + "\n" + b.item.text, b.item.src, min(maxHoldMs, (a.item.holdMs + b.item.holdMs) * 7 / 10), b.item.speaker,
                mergePayload?.invoke(a.item.payload, b.item.payload) ?: b.item.payload)
            queue.add(0, Queued(merged, min(a.readyAt, b.readyAt)))
        }
        return poll(now)
    }

    fun showImmediately(item: Item, now: Long): Item {
        previousUntil = hideAt().takeIf { it > now && showing?.text != item.text } ?: 0L
        queue.clear()
        return start(item, now)
    }

    private fun start(item: Item, now: Long): Item {
        current = item
        shownAt = now
        return item
    }

    /** Gọi định kỳ: trả dòng kế tiếp nếu dòng đang hiện đã đủ lâu. */
    fun poll(now: Long): Item? {
        if (queue.isEmpty()) return null
        val cur = current
        val head = queue[0]
        if (cur != null && now < max(head.readyAt, shownAt + preemptAfter(cur))) return null
        queue.removeAt(0)
        return start(head.item, now)
    }

    /**
     * Dòng đang hiện có được phép ẩn lúc [now] không? Chữ trên màn hình biến mất sớm thì vẫn giữ bản dịch tới hết thời gian đọc;
     * lời nghe được (không có chữ nào trên màn hình để theo dõi) tự ẩn khi hết thời gian đọc và không còn dòng chờ.
     */
    fun mayHide(now: Long): Boolean {
        val cur = current ?: return true
        return queue.isEmpty() && now >= shownAt + cur.holdMs
    }

    /** Thời điểm sớm nhất được ẩn dòng đang hiện (để hẹn giờ), hoặc 0 nếu không có dòng. */
    fun hideAt(): Long = current?.let { shownAt + it.holdMs } ?: 0L

    /** Đã ẩn dòng đang hiện (hoặc dừng dịch). */
    fun cleared() { previousUntil = 0; current = null; queue.clear() }

    /** Ước lượng tốc độ nói (ký tự mỗi giây) từ một câu nghe được; dùng để biết nhân vật nói nhanh hay chậm. */
    class SpeechRate(private val alpha: Float = 0.3f) {
        var cps = 0f
            private set

        fun add(text: String, speechMs: Long) {
            if (speechMs < 400L) return
            val c = text.count { !it.isWhitespace() } * 1000f / speechMs
            if (c < 1f || c > 40f) return
            cps = if (cps == 0f) c else cps * (1 - alpha) + c * alpha
        }

        /** Nhanh hơn 1.25 lần so với tốc độ nói bình thường của tiếng Anh (khoảng 14 ký tự mỗi giây). */
        val fast: Boolean get() = cps > 17.5f
        val slow: Boolean get() = cps in 0.1f..9f
    }
}
