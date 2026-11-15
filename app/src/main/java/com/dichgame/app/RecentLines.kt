package com.dichgame.app

/**
 * Các câu vừa dịch gần đây (câu gốc + bản dịch), chỉ giữ trong bộ nhớ để người dùng sửa lại bản dịch sai.
 * Không ghi ra đĩa; bị xoá khi bắt đầu một lần dịch mới hoặc khi app bị tắt. Câu nào người dùng sửa mới được lưu vào gói Việt hoá của game.
 * Không phụ thuộc Android.
 */
object RecentLines {
    class Entry(val orig: String, val vi: String, val speaker: String?, val unsure: Boolean, val at: Long)

    private const val MAX = 40
    private val items = ArrayList<Entry>()

    /** Thêm một câu. Cùng câu gốc đã có thì thay bản cũ (câu được dịch lại hoặc đọc lại). */
    @Synchronized
    fun add(orig: String, vi: String, speaker: String?, unsure: Boolean, at: Long) {
        if (orig.isBlank() || vi.isBlank()) return
        val k = OcrGuard.key(orig)
        if (k.isEmpty()) return
        items.removeAll { OcrGuard.key(it.orig) == k }
        items.add(Entry(orig.trim(), vi.trim(), speaker, unsure, at))
        while (items.size > MAX) items.removeAt(0)
    }

    /** Mới nhất trước. */
    @Synchronized
    fun snapshot(): List<Entry> = items.asReversed().toList()

    @Synchronized
    fun clear() { items.clear() }
}
