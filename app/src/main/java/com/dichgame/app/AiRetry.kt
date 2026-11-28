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
        /** 429 per-key/project quota with a provider-supplied reset window. */
        QUOTA,
        /** Provider/account-wide billing or spend cap; rotating another key will not fix it. */
        BILLING,
        /** Mạng chập chờn, quá thời gian, máy chủ quá tải (5xx): thử lại thường được. */
        NETWORK,
        /** AI từ chối câu này vì bộ lọc nội dung: thử lại vô ích. */
        BLOCKED,
        /** Khóa sai, sai model hoặc địa chỉ (4xx khác): thử lại vô ích, người dùng phải sửa cài đặt. */
        BAD,
        /** Phản hồi rỗng hoặc dữ liệu lạ. */
        OTHER,
    }

    /** Chỉ thử lại request Gemini 400 khi response chỉ rõ safetySettings không được model/endpoint chấp nhận. */
    fun shouldRetryWithoutSafetySettings(code: Int, body: String): Boolean {
        if (code != 400) return false
        val s = body.lowercase(java.util.Locale.ROOT)
        if (!s.contains("safetysettings")) return false
        return listOf("unknown", "unsupported", "not supported", "not allowed", "unrecognized", "invalid field")
            .any { s.contains(it) }
    }

    /** Chỉ chuyển từ Interactions sang generateContent khi endpoint/JSON Interactions không tương thích. */
    fun shouldFallbackTtsEndpoint(code: Int, body: String): Boolean {
        val s = body.lowercase(java.util.Locale.ROOT)
        if (code == 404) {
            return s.contains("interactions") &&
                listOf("not found", "unknown", "unsupported", "not supported").any { s.contains(it) }
        }
        if (code != 400) return false
        val mentionsInteractionsShape = listOf("interactions", "response_format", "generation_config", "speech_metadata", "user_input")
            .any { s.contains(it) }
        val rejectsShape = listOf("unknown", "unsupported", "not supported", "not found", "unrecognized", "invalid")
            .any { s.contains(it) }
        return mentionsInteractionsShape && rejectsShape
    }

    /** Lấy thông báo có ích từ response body của Google; giữ status và details nhưng không làm lộ request headers. */
    fun providerErrorMessage(body: String, maxChars: Int = 1200): String {
        val root = runCatching { JSONObject(body) }.getOrNull()
        val error = root?.optJSONObject("error")
        val message = error?.optString("message").orEmpty().trim()
        val status = error?.optString("status").orEmpty().trim()
        val details = error?.optJSONArray("details")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { d ->
                    val reason = d.optString("reason").takeIf { it.isNotBlank() }
                    val domain = d.optString("domain").takeIf { it.isNotBlank() }
                    listOfNotNull(reason, domain).joinToString("/")
                }
            }.filter { it.isNotBlank() }.distinct().joinToString(", ")
        }.orEmpty()
        val summary = listOf(message, status, details).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        return (summary.ifBlank { body.trim() }).take(maxChars)
    }

    /** Mã giả cho lỗi "AI từ chối vì bộ lọc nội dung" (không phải mã HTTP). */
    const val CODE_BLOCKED = -1

    fun kindOf(e: Throwable): Kind = when (e) {
        is AiException -> when {
            e.billingLimit -> Kind.BILLING
            e.code == 429 -> Kind.QUOTA
            e.code == CODE_BLOCKED -> Kind.BLOCKED
            e.code == 408 || e.code in 500..599 -> Kind.NETWORK
            e.code in 400..499 -> Kind.BAD
            else -> Kind.OTHER
        }
        is IOException -> Kind.NETWORK   // gồm hết thời gian chờ, không phân giải được tên miền, mất kết nối
        else -> Kind.OTHER
    }

    /** Chỉ chuyển nhà cung cấp khi lỗi kết nối, hạn mức hoặc cấu hình; không thử vượt bộ lọc nội dung. */
    fun canFallback(e: Throwable): Boolean = when (kindOf(e)) {
        Kind.QUOTA, Kind.BILLING, Kind.NETWORK, Kind.BAD -> true
        else -> false
    }

    /** Tên bộ đếm thống kê cho mỗi loại lỗi (xem [SessionStats]). */
    fun statKey(k: Kind): String = when (k) {
        Kind.QUOTA -> "may_het_luot"
        Kind.BILLING -> "may_billing"
        Kind.NETWORK -> "may_mang"
        Kind.BLOCKED -> "may_chan"
        Kind.BAD -> "may_khoa"
        Kind.OTHER -> "may_khac"
    }

    /** TTS chỉ thử lại lỗi tạm thời nhanh một lần; không kéo dài hàng chờ thoại khi request đã gần hết timeout HTTP. */
    fun ttsPlan() = Plan(maxAttempts = 2, maxWaitMs = 1_000L, backoffMs = 350L, maxBackoffMs = 350L, jitterRatio = 0.0, budgetMs = 22_000L)

    class Plan(
        /** Tổng số lần gọi tối đa cho một câu. */
        val maxAttempts: Int = 3,
        /** Hết lượt mà máy chủ bảo chờ lâu hơn mức này thì bỏ cuộc ngay (không treo câu phụ đề), nhường dịch máy. */
        val maxWaitMs: Long = 3000,
        /** Base delay for exponential backoff. */
        val backoffMs: Long = 500,
        /** Maximum exponential delay before jitter. */
        val maxBackoffMs: Long = 4000,
        /** Randomized delay ratio for transient network failures (0.2 = +/-20%). */
        val jitterRatio: Double = 0.2,
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
                if (kind == Kind.BLOCKED || kind == Kind.BAD || kind == Kind.BILLING) throw e
                if (attempt >= plan.maxAttempts) throw e
                val shift = (attempt - 1).coerceIn(0, 20)
                val baseBackoff = plan.backoffMs.coerceAtLeast(0L)
                val backoffCap = plan.maxBackoffMs.coerceAtLeast(0L)
                val multiplier = 1L shl shift
                val exponential = if (baseBackoff > backoffCap / multiplier) backoffCap else baseBackoff * multiplier
                val told = if (kind == Kind.QUOTA) (e as? AiException)?.retryAfterMs ?: 0L else 0L
                if (told > plan.maxWaitMs) throw e

                // Honor Retry-After as a lower bound. Otherwise apply bounded jitter so
                // many game clients do not synchronize retries against a recovering API.
                val base = maxOf(exponential, told)
                val ratio = plan.jitterRatio.coerceIn(0.0, 1.0)
                val wait = if (kind == Kind.QUOTA && told > 0L) {
                    base
                } else if (ratio == 0.0 || base == 0L) {
                    base
                } else {
                    val spread = (base * ratio).toLong()
                    val low = (base - spread).coerceAtLeast(0L)
                    val high = (base + spread).coerceAtMost(backoffCap)
                    if (high <= low) low else java.util.concurrent.ThreadLocalRandom.current().nextLong(low, high + 1)
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
    fun explain(kind: Kind, code: Int, coolMs: Long, detail: String? = null): String = when (kind) {
        Kind.QUOTA ->
            "Nhà cung cấp AI báo hết lượt hoặc đã chạm hạn mức sử dụng; các khóa cùng dự án hoặc tài khoản có thể dùng chung hạn mức." +
                if (coolMs > 0) " Tạm dịch bằng dịch máy khoảng ${humanWait(coolMs)} rồi thử lại AI." else " Câu này tạm dịch bằng dịch máy."
        Kind.BILLING ->
            "Tài khoản hoặc dự án AI đã chạm giới hạn thanh toán/chi tiêu. Hãy kiểm tra gói thanh toán hoặc hạn mức chi tiêu; app tạm dùng dịch máy."
        Kind.NETWORK -> "Không gọi được AI sau vài lần thử (mạng chậm hoặc máy chủ AI quá tải). Câu này tạm dịch bằng dịch máy."
        Kind.BLOCKED -> "AI từ chối câu này (bộ lọc nội dung). Câu này dịch bằng dịch máy."
        Kind.BAD -> when (code) {
            401, 403 -> "Khóa AI sai hoặc không có quyền (lỗi $code). Kiểm tra lại ở Cài đặt › Cách dịch. Đang dùng dịch máy."
            400 -> "Gemini từ chối yêu cầu (400): " + (detail?.take(320)?.takeIf { it.isNotBlank() } ?: "yêu cầu hoặc cấu hình không hợp lệ") + ". Kiểm tra model, API Key và cấu hình request. Đang dùng dịch máy."
            404 -> "Sai tên model hoặc địa chỉ AI (lỗi 404). Kiểm tra lại ở Cài đặt › Cách dịch. Đang dùng dịch máy."
            else -> "AI báo lỗi $code. Đang dùng dịch máy."
        }
        Kind.OTHER -> "AI trả về dữ liệu lạ. Câu này tạm dịch bằng dịch máy."
    }
}
