package com.dichgame.app

/**
 * Chọn nội dung được chuyển sang thuyết minh.
 *
 * Cờ uncertain chỉ cảnh báo độ tin cậy của bản dịch trên phụ đề; không có nghĩa là
 * bản dịch rỗng hoặc không hợp lệ để đọc. Vì vậy chỉ loại chuỗi rỗng/trắng.
 */
internal object TranslationSpeechPolicy {
    data class Candidate(val text: String, val uncertain: Boolean)

    fun speakable(candidates: List<Candidate>): List<String> =
        candidates.map { it.text.trim() }.filter { it.isNotEmpty() }
}
