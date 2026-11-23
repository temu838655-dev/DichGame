package com.dichgame.app

import java.util.Locale

/**
 * Bộ lọc cho "Đọc Vietsub": chỉ đưa lời thoại có chứng cứ là tiếng Việt tới TTS.
 * Không gọi Gemini, ML Kit Translation hay tự dịch/chữa nội dung. Những câu quá ngắn
 * hoặc không rõ ngôn ngữ bị bỏ qua thay vì đọc nhầm phụ đề tiếng nước ngoài.
 * Thuần Kotlin để unit test được mà không cần máy Android.
 */
object VietsubGuard {
    private val vietnameseWords = setOf(
        "tôi", "ta", "tao", "mày", "bạn", "anh", "em", "chị", "cô", "chú", "bác", "ông",
        "bà", "mẹ", "cha", "con", "cậu", "hắn", "họ", "chúng", "mình", "của", "và",
        "với", "nhưng", "rồi", "đã", "đang", "sẽ", "được", "không", "có", "chưa",
        "còn", "đây", "đó", "này", "ấy", "kia", "ở", "đi", "đến", "lại",
        "làm", "gì", "sao", "vì", "nếu", "thì", "thôi", "nữa", "rất", "quá",
        "nào", "đâu", "thế", "vậy", "nhé", "nhỉ", "ạ", "dạ", "vâng", "ừ",
        "tại", "nên", "phải", "muốn", "cần", "biết", "thấy", "nghe", "hiểu",
        "xin", "chào", "cảm", "ơn", "lỗi", "giúp", "về", "nhà", "người", "nói"
    )
    private val word = Regex("[\\p{L}]+")
    private val noAccentWords = setOf("toi", "ban", "khong", "chung", "minh", "nha",
        "roi", "sao", "chao", "cam", "on", "nhieu", "mot", "nhung", "noi", "can",
        "phai", "hieu", "biet", "nay", "day", "duoc", "muon", "nguoi", "bao")
    // Các chữ mang dấu/chữ cái đặc trưng của tiếng Việt. Không tính dấu câu hay chữ Latin thường.
    private val viDiacritics = "ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"
    private val commonTone = "áàéèíìóòúùýỳ"
    private val safeShort = setOf("có", "dạ", "vâng", "không", "đi", "ừ", "được", "chạy", "cứu", "đừng", "thôi", "nhé")

    fun isVietnamese(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty()) return false
        val guess = LangDetect.guess(text)
        if (guess?.lang == "vi") return true
        val low = text.lowercase(Locale.ROOT)
        val tokens = word.findAll(low).map { it.value }.toList()
        val count = tokens.count { it in vietnameseWords }
        // Lời thoại Vietsub ngắn như "Có!" hoặc "Xin chào!" có thể chỉ chứa dấu sắc/huyền
        // (cũng tồn tại ở ngôn ngữ Latin khác); yêu cầu từ vựng Việt rõ thay vì bỏ sót.
        if (tokens.size == 1 && tokens[0] in safeShort) return true
        if (count >= 2 && low.any { it in commonTone } && guess == null) return true
        val noAccentCount = tokens.count { it in noAccentWords }
        val accents = low.count { it in viDiacritics }
        // Phụ đề không dấu có thể bị bộ đoán ngôn ngữ đánh nhầm chỉ bởi 1-2 từ
        // thông dụng trùng tiếng Tây Ban Nha/Pháp. Chỉ nhận khi ít nhất 3 tín hiệu Việt rõ.
        if (accents == 0 && noAccentCount >= 3 &&
            (count + noAccentCount) * 3 >= tokens.size) return true
        if (guess != null && guess.lang != "vi") return false
        return when {
            accents >= 2 && count >= 1 -> true
            accents >= 1 && count >= 1 -> true
            // Phụ đề viết không dấu vẫn có thể đọc, nhưng phải đủ tín hiệu để tránh đọc tiếng Anh.
            accents == 0 && (count + noAccentCount) >= 3 && (count + noAccentCount) * 3 >= tokens.size -> true
            else -> false
        }
    }
}
