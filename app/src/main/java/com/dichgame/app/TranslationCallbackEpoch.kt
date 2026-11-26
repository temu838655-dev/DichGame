package com.dichgame.app

/**
 * Vòng đời của một yêu cầu dịch. Mỗi lần bắt đầu hoặc hủy tăng epoch;
 * callback chỉ được phép giao kết quả nếu epoch vẫn là hiện hành.
 *
 * Điều này ngăn một request AI bị treo quá watchdog rồi trả kết quả muộn
 * ghi đè phụ đề của request mới.
 */
internal class TranslationCallbackEpoch {
    private var current = 0L

    @Synchronized
    fun begin(): Long {
        current += 1L
        return current
    }

    @Synchronized
    fun invalidate() {
        current += 1L
    }

    @Synchronized
    fun isCurrent(token: Long): Boolean = token == current
}
