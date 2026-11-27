package com.dichgame.app

/** Chặn OCR gửi lại cùng một câu hoặc câu chỉ khác vài ký tự trong cửa sổ ngắn. */
class RecentTextGate(private val windowMs: Long = 3_500L, private val threshold: Float = 0.90f) {
    private var lastText = ""
    private var lastAt = Long.MIN_VALUE

    @Synchronized
    fun shouldAccept(text: String, now: Long): Boolean {
        val clean = OcrGuard.clean(text)
        if (clean.isBlank()) return false
        val age = now - lastAt
        if (lastText.isNotEmpty() && age >= 0L && age <= windowMs &&
            OcrGuard.similarity(lastText, clean) >= threshold) return false
        lastText = clean
        lastAt = now
        return true
    }

    @Synchronized
    fun reset() { lastText = ""; lastAt = Long.MIN_VALUE }
}
