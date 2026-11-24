package com.dichgame.app

import java.io.IOException
import org.json.JSONObject

/**
 * Chính sách khi gọi AI dịch: phân loại lỗi, thử lại đúng chỗ, đọc phản hồi Gemini cho chắc, giải thích lỗi bằng lời người dùng hiểu.
 * Trước đây MỘT lần gọi AI lỗi (hết lượt, máy chủ quá tải 503, quá thời gian, mạng chập chờn) là câu đó bị đẩy sang dịch máy ngay,
 * không thử lại. Không phụ thuộc Android nên có kiểm thử đơn vị.
 */
object AiRetry {
    enum class Kind {
        /** 429: hết lượt (theo phút hoặc theo ngày). */
        QUOTA,
        /** Mạng chập chờn, quá thời gian, máy chủ quá tải (5xx): thử lại thường được. */
        NETWORK,
        /** AI từ chối câu này vì bộ lọc nội dung: thử lại vô ích. */
        BLOCKED,
        /** Khóa sai, sai model hoặc địa chỉ (4xx khác): thử lại vô ích, người dùng phải sửa cài đặt. */
        BAD,
        /** Phản hồi rỗng hoặc dữ liệu lạ. */
        OTHER,
    }

    /** Mã giả cho lỗi "AI từ chối vì bộ lọc nội dung" (không phải mã HTTP). */
    const val CODE_BLOCKED = -1

    fun kindOf(e: Throwable): Kind = when (e) {
        is AiException -> when {
            e.code == 429 -> Kind.QUOTA
            e.code == CODE_BLOCKED -> Kind.BLOCKED
            e.code == 408 || e.code in 500..599 -> Kind.NETWORK
            e.code in 400..499 -> Kind.BAD
            else -> Kind.OTHER
        }
        is IOException -> Kind.NETWORK   // gồm hết thời gian chờ, không phân giải được tên miền, mất kết nối
        else -> Kind.OTHER
    }

    /** Tên bộ đếm thống kê cho mỗi loại lỗi (xem [SessionStats]). */
    fun statKey(k: Kind): String = when (k) {
        Kind.QUOTA -> "may_het_luot"
        Kind.NETWORK -> "may_mang"
        Kind.BLOCKED -> "may_chan"
        Kind.BAD -> "may_khoa"
        Kind.OTHER -> "may_khac"
    }

    class Plan(
        /** Tổng số lần gọi tối đa cho một câu. */
        val maxAttempts: Int = 3,
        /** Hết lượt mà máy chủ bảo chờ lâu hơn mức này thì bỏ cuộc ngay (không treo câu phụ đề), nhường dịch máy. */
        val maxWaitMs: Long = 3000,
        /** Chờ giữa các lần thử: backoffMs × số lần đã thử. */
        val backoffMs: Long = 500,
        /** Quá chừng này kể từ lần gọi đầu thì không thử thêm. */
        val budgetMs: Long = 22000,
    )

    /**
     * Gọi [call] tới khi được hoặc hết cách. QUOTA: chờ rồi thử lại nếu máy chủ bảo chờ ngắn, còn lâu thì bỏ cuộc ngay.
     * NETWORK, OTHER: chờ rồi thử lại. BLOCKED, BAD: không thử lại. Ném lại lỗi cuối cùng nếu hết cách.
     */
    fun <T> run(plan: Plan, sleep: (Long) -> Unit, now: () -> Long = System::currentTimeMillis, call: () -> T): T {
        val start = now()
        var attempt = 0
        while (true) {
            attempt++
            try {
                return call()
            } catch (e: Exception) {
                val kind = kindOf(e)
                if (kind == Kind.BLOCKED || kind == Kind.BAD || (e as? AiException)?.billingLimit == true) throw e
                if (attempt >= plan.maxAttempts) throw e
                var wait = plan.backoffMs * attempt
                if (kind == Kind.QUOTA) {
                    val told = (e as? AiException)?.retryAfterMs ?: 0L
                    if (told > plan.maxWaitMs) throw e
                    wait = maxOf(wait, told)
                }
                if (now() - start + wait > plan.budgetMs) throw e
                sleep(wait)
            }
        }
    }

    private val RETRY_DELAY = Regex("\"retryDelay\"\\s*:\\s*\"([0-9]+(?:\\.[0-9]+)?)s\"")

    /** Thời gian máy chủ bảo chờ (mili giây): từ tiêu đề Retry-After (giây) hoặc trường retryDelay trong phản hồi lỗi của Gemini. 0 nếu không có. */
    fun retryDelayMs(header: String?, body: String): Long {
        header?.trim()?.toDoubleOrNull()?.let { if (it > 0) return (it * 1000).toLong() }
        RETRY_DELAY.find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.let { if (it > 0) return (it * 1000).toLong() }
        return 0L
    }

    /** Hết lượt hết khóa: tạm bỏ AI chừng này mili giây (cho dịch máy ngay, khỏi để mỗi câu chờ vô ích), rồi thử lại AI. */
    fun cooldownMs(retryAfterMs: Long): Long = if (retryAfterMs > 0) retryAfterMs.coerceIn(5000L, 120000L) else 20000L

    /**
     * Lấy bản dịch từ phản hồi JSON của Gemini. Ném [AiException] mã [CODE_BLOCKED] nếu bị bộ lọc nội dung chặn, mã 0 nếu phản hồi
     * không có chữ. Bỏ các phần "suy nghĩ" của model; nối các phần chữ còn lại.
     */
    fun geminiText(json: String): String {
        val o = JSONObject(json)
        o.optJSONObject("promptFeedback")?.optString("blockReason", "")?.takeIf { it.isNotEmpty() }?.let {
            throw AiException(CODE_BLOCKED, "Bộ lọc nội dung chặn câu này: $it")
        }
        val cands = o.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw AiException(0, "Phản hồi không có bản dịch")
        val c = cands.getJSONObject(0)
        val parts = c.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            sb.append(part.optString("text", ""))
        }
        val text = sb.toString().trim()
        if (text.isNotEmpty()) return text
        val fr = c.optString("finishReason", "")
        if (fr in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY")) {
            throw AiException(CODE_BLOCKED, "Bộ lọc nội dung chặn câu này: $fr")
        }
        throw AiException(0, "Phản hồi rỗng" + if (fr.isNotEmpty()) " ($fr)" else "")
    }

    /** "21 giây", "5 phút", "3 giờ": thời gian chờ viết cho người đọc. */
    fun humanWait(ms: Long): String = when {
        ms < 90_000L -> "${(ms + 999) / 1000} giây"
        ms < 90 * 60_000L -> "${(ms + 59_999) / 60_000} phút"
        else -> "${(ms + 3_599_999) / 3_600_000} giờ"
    }

    /** Câu báo cho người dùng khi một câu phải dịch bằng dịch máy vì AI lỗi. [coolMs]: còn bao lâu nữa mới thử lại AI. */
    fun explain(kind: Kind, code: Int, coolMs: Long): String = when (kind) {
        Kind.QUOTA ->
            "AI báo hết lượt dùng (các khóa có thể dùng chung hạn mức của dự án hoặc tài khoản)." +
                if (coolMs > 0) " Tạm dịch bằng dịch máy khoảng ${humanWait(coolMs)} rồi thử lại AI." else " Câu này tạm dịch bằng dịch máy."
        Kind.NETWORK -> "Không gọi được AI sau vài lần thử (mạng chậm hoặc máy chủ AI quá tải). Câu này tạm dịch bằng dịch máy."
        Kind.BLOCKED -> "AI từ chối câu này (bộ lọc nội dung). Câu này dịch bằng dịch máy."
        Kind.BAD -> when (code) {
            401, 403 -> "Khóa AI sai hoặc không có quyền (lỗi $code). Kiểm tra lại ở Cài đặt › Cách dịch. Đang dùng dịch máy."
            400 -> "Khóa AI hoặc tên model sai (lỗi 400). Kiểm tra lại ở Cài đặt › Cách dịch. Đang dùng dịch máy."
            404 -> "Sai tên model hoặc địa chỉ AI (lỗi 404). Kiểm tra lại ở Cài đặt › Cách dịch. Đang dùng dịch máy."
            else -> "AI báo lỗi $code. Đang dùng dịch máy."
        }
        Kind.OTHER -> "AI trả về dữ liệu lạ. Câu này tạm dịch bằng dịch máy."
    }
}
