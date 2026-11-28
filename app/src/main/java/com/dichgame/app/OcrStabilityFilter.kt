package com.dichgame.app

/**
 * Xác nhận OCR bằng cả số khung hình và thời gian tồn tại của cùng một nội dung.
 *
 * Bộ lọc này chỉ xử lý độ ổn định của chữ, không tự gộp hai câu gần giống nhau:
 * thay đổi nhỏ như "not", tên nhân vật hoặc con số vẫn phải được chuyển tiếp.
 * Mỗi phiên quét dùng một instance và gọi reset() khi màn hình/game context thay đổi.
 */
internal class OcrStabilityFilter {
    private var candidate = ""
    private var firstSeenAt = 0L
    private var frames = 0

    val frameCount: Int get() = frames
    val candidateSignature: String get() = candidate

    /**
     * @return true khi cùng signature đã xuất hiện đủ [requiredFrames] và đủ [minimumStableMs].
     * Thời gian được truyền từ bên ngoài để kiểm thử xác định, không đọc đồng hồ hệ thống ở đây.
     */
    fun observe(
        signature: String,
        requiredFrames: Int,
        nowMs: Long,
        minimumStableMs: Long
    ): Boolean {
        if (signature.isBlank()) {
            reset()
            return false
        }

        if (signature != candidate) {
            candidate = signature
            firstSeenAt = nowMs
            frames = 1
        } else {
            frames++
        }

        val neededFrames = requiredFrames.coerceAtLeast(1)
        val elapsed = (nowMs - firstSeenAt).coerceAtLeast(0L)
        return frames >= neededFrames && elapsed >= minimumStableMs.coerceAtLeast(0L)
    }

    fun reset() {
        candidate = ""
        firstSeenAt = 0L
        frames = 0
    }
}
