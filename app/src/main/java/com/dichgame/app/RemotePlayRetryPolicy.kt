package com.dichgame.app

/**
 * Chính sách thử kết nối Remote Play có giới hạn.
 * Tách khỏi Activity để kiểm thử thứ tự backoff mà không cần thiết bị Android.
 */
internal object RemotePlayRetryPolicy {
    private val delaysMs = longArrayOf(1_500L, 3_000L, 6_000L, 10_000L, 15_000L)

    val maxAttempts: Int
        get() = delaysMs.size

    /** Trả thời gian chờ cho lần thử tiếp theo (0-based), null khi hết lượt hoặc chỉ số không hợp lệ. */
    fun delayForAttempt(attempt: Int): Long? = delaysMs.getOrNull(attempt)
}
