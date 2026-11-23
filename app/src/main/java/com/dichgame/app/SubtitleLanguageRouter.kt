package com.dichgame.app

/**
 * Giữ ngôn ngữ ổn định giữa các câu phụ đề ngắn. ML Kit có thể nhầm "Yes", "No",
 * tên nhân vật thành một ngôn ngữ Latin khác, dẫn tới tải mô hình liên tục.
 * Chữ viết đặc trưng (Nhật/Hàn/Trung/Nga...) vẫn được đổi ngay.
 * Không phụ thuộc Android để unit test được.
 */
class SubtitleLanguageRouter(initial: String = "en") {
    var current: String = initial
        private set

    private var pending = ""
    private var consistent = 0

    fun resolve(text: String, ml: String?, guess: LangDetect.Guess? = LangDetect.guess(text)): String {
        val trimmed = text.trim()
        if (trimmed.count { it.isLetter() } < 2) return current
        val valid = Prefs.LANGS.map { it.first }.toSet()
        val model = ml?.substringBefore('-')?.lowercase(java.util.Locale.ROOT)
            ?.takeIf { it in valid }
        val scripted = guess?.lang?.takeIf { it in valid && guess.script != 'L' }
        if (scripted != null) {
            // Chữ Hán không có kana có thể là tiếng Nhật: chỉ giữ tiếng Nhật
            // khi đã có ngữ cảnh Nhật trước đó, không đoán chắc chắn là Trung.
            val chosen = if (current == "ja" && scripted == "zh") "ja" else scripted
            current = chosen
            pending = ""
            consistent = 0
            return current
        }

        val heuristic = guess?.lang?.takeIf { it in valid }
        val distinctiveVietnamese = heuristic == "vi" && trimmed.any {
            it in "ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹĂÂĐÊÔƠƯ"
        }
        if (distinctiveVietnamese) {
            current = "vi"
            pending = ""
            consistent = 0
            return current
        }
        // Câu ngắn không có dấu thường không đủ thông tin để đổi ngôn ngữ.
        val letters = trimmed.count { it.isLetter() }
        if (letters < 9) return current

        val chosen = model ?: heuristic ?: return current
        if (chosen == current) {
            pending = ""
            consistent = 0
            return current
        }
        // Khi ML Kit và các dấu hiệu từ vựng đồng ý trên một câu đủ dài,
        // có thể chuyển ngay; trường hợp còn lại cần 2 câu nhất quán.
        val agreed = model != null && heuristic == model && letters >= 20
        if (agreed) {
            current = chosen
            pending = ""
            consistent = 0
            return current
        }
        if (pending == chosen) consistent++ else {
            pending = chosen
            consistent = 1
        }
        if (consistent >= 2) {
            current = chosen
            pending = ""
            consistent = 0
        }
        return current
    }
}
