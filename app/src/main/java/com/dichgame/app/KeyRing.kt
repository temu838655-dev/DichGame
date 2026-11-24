package com.dichgame.app

import java.util.Calendar
import java.util.TimeZone

/**
 * Sổ theo dõi các khóa Gemini: khóa nào đang hết lượt (và tới lúc nào dùng lại được), khóa nào sai.
 * Mỗi lần gọi chọn khóa đầu tiên còn dùng được, ưu tiên khóa vừa chạy tốt (không đổi khóa vô cớ). Khóa hết lượt thì tạm bỏ qua
 * tới giờ hồi, nên các câu sau không còn mất công thử lại khóa đã hết. Hạn mức của Gemini tính theo (dự án, model), vì vậy hết lượt
 * được ghi theo từng model; khóa sai thì sai với mọi model. Không phụ thuộc Android nên có kiểm thử đơn vị.
 */
object KeyRing {
    /** Khóa sai: nghỉ chừng này rồi mới thử lại (người dùng sửa khóa thì thành khóa mới nên dùng được ngay). */
    const val BAD_MS = 30 * 60_000L
    /** Hết lượt mà máy chủ không nói chờ bao lâu: coi như hết lượt theo phút. */
    const val DEFAULT_QUOTA_MS = 30_000L
    private const val MIN_QUOTA_MS = 1_000L
    private const val MAX_MINUTE_QUOTA_MS = 10 * 60_000L

    private val until = HashMap<String, Long>()
    private val bad = HashMap<String, Long>()
    private val current = HashMap<String, String>()

    private fun id(key: String, scope: String) = scope + "|" + key

    enum class State { OK, QUOTA, BAD }

    /** Tình trạng một khóa: [state], và còn [leftMs] mili giây nữa mới dùng lại được (0 nếu đang dùng được). */
    class Status(val state: State, val leftMs: Long, val active: Boolean)

    @Synchronized fun status(key: String, scope: String, now: Long = System.currentTimeMillis()): Status {
        val b = (bad[key] ?: 0L) - now
        if (b > 0) return Status(State.BAD, b, false)
        val q = (until[id(key, scope)] ?: 0L) - now
        if (q > 0) return Status(State.QUOTA, q, false)
        return Status(State.OK, 0L, current[scope] == key)
    }

    /** Khóa dùng được cho lần gọi này, hoặc null nếu mọi khóa đang hết lượt hay sai. Khóa vừa chạy tốt được thử trước. */
    @Synchronized fun pick(keys: List<String>, scope: String, now: Long = System.currentTimeMillis()): String? {
        if (keys.isEmpty()) return null
        val start = current[scope]?.let { keys.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        for (i in keys.indices) {
            val k = keys[(start + i) % keys.size]
            if (status(k, scope, now).state == State.OK) return k
        }
        return null
    }

    /** Khóa [key] gọi được: nhớ là khóa đang dùng. Trả về true nếu vừa phải đổi từ khóa khác sang khóa này. */
    @Synchronized fun success(key: String, scope: String): Boolean {
        val old = current.put(scope, key)
        return old != null && old != key
    }

    /** Khóa hết lượt tới mốc [now] + [ms]. */
    @Synchronized fun blockQuota(key: String, scope: String, ms: Long, now: Long = System.currentTimeMillis()) {
        val t = now + ms
        if (t > (until[id(key, scope)] ?: 0L)) until[id(key, scope)] = t
    }

    /** Khóa sai (hoặc bị thu hồi): bỏ qua một lúc ở mọi model. */
    @Synchronized fun blockBad(key: String, now: Long = System.currentTimeMillis()) { bad[key] = now + BAD_MS }

    /** Khóa người dùng vừa sửa hoặc thêm lại thì cho dùng ngay. */
    @Synchronized fun forget(key: String) {
        bad.remove(key)
        until.keys.removeAll { it.endsWith("|$key") }
    }

    @Synchronized fun reset() { until.clear(); bad.clear(); current.clear() }

    /**
     * Mọi khóa đều đang nghỉ: còn bao lâu nữa khóa sớm nhất dùng lại được (mili giây). 0 nếu có khóa dùng được ngay;
     * 0 cả khi chưa có khóa nào (khi đó không có gì để chờ).
     */
    @Synchronized fun waitMs(keys: List<String>, scope: String, now: Long = System.currentTimeMillis()): Long {
        if (keys.isEmpty()) return 0L
        var best = Long.MAX_VALUE
        for (k in keys) {
            val s = status(k, scope, now)
            if (s.state == State.OK) return 0L
            if (s.leftMs < best) best = s.leftMs
        }
        return best
    }

    /** Mọi khóa đều sai (không phải hết lượt): chờ cũng vô ích, phải sửa khóa. */
    @Synchronized fun allBad(keys: List<String>, now: Long = System.currentTimeMillis()): Boolean =
        keys.isNotEmpty() && keys.all { (bad[it] ?: 0L) > now }

    /** Số khóa đang dùng được cho [scope]. */
    @Synchronized fun usable(keys: List<String>, scope: String, now: Long = System.currentTimeMillis()): Int =
        keys.count { status(it, scope, now).state == State.OK }

    // ---- đọc lỗi từ máy chủ

    /** Phản hồi lỗi 429 nói hạn mức theo ngày (GenerateRequestsPerDay...). */
    fun isDailyQuota(body: String): Boolean = listOf("perday", "per day", "per_day", "daily quota", "requests/day").any { body.contains(it, ignoreCase = true) }

    fun isBillingLimit(body: String): Boolean = listOf("insufficient_quota", "credit_balance_exhausted", "organization_spend_limit_exceeded", "project_spend_limit_exceeded", "organization_usage_limit_exceeded", "billing_hard_limit_reached").any { body.contains(it, ignoreCase = true) }

    /** Lỗi cho biết chính khóa này không dùng được (sai, hết hạn, bị thu hồi). 403 khác (ví dụ model chưa mở cho khóa) thì không tính. */
    fun isBadKey(code: Int, body: String): Boolean {
        if (code == 401) return true
        if (code != 400 && code != 403) return false
        val b = body.lowercase()
        return b.contains("api_key_invalid") || b.contains("api key not valid") || b.contains("api key expired") ||
            b.contains("api key was reported as leaked") || b.contains("reported as leaked") || b.contains("api key is invalid") || b.contains("invalid_api_key") || b.contains("incorrect api key") || b.contains("invalid api key") || b.contains("api key revoked") || b.contains("api_key_service_blocked") || b.contains("api_key_http_referrer_blocked") || b.contains("api_key_ip_address_blocked")
    }

    /** Mốc 0 giờ sáng kế tiếp theo giờ Thái Bình Dương (Gemini hồi hạn mức theo ngày vào lúc đó), thêm một phút cho chắc. */
    fun nextPacificMidnight(now: Long): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"))
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis + 60_000L
    }

    /** Khóa hết lượt phải nghỉ bao lâu: tới nửa đêm Thái Bình Dương nếu hết theo ngày, không thì theo lời máy chủ (không nói thì [DEFAULT_QUOTA_MS]). */
    fun quotaBlockMs(retryAfterMs: Long, daily: Boolean, now: Long): Long = when {
        daily -> (nextPacificMidnight(now) - now).coerceAtLeast(MIN_QUOTA_MS)
        retryAfterMs > 0 -> retryAfterMs.coerceIn(MIN_QUOTA_MS, MAX_MINUTE_QUOTA_MS)
        else -> DEFAULT_QUOTA_MS
    }

    /**
     * Ghi lại kết quả lỗi [e] của một lần gọi bằng [key] ở [scope]. Trả về true nếu lỗi do chính khóa này (hết lượt hoặc sai)
     * và đã đánh dấu: nên đổi sang khóa khác ngay. False nếu lỗi khác (mạng, model, nội dung) thì đổi khóa cũng vô ích.
     */
    fun report(key: String, scope: String, e: AiException, now: Long = System.currentTimeMillis()): Boolean = when {
        e.badKey -> { blockBad(key, now); true }
        e.code == 429 -> {
            val delay = if (e.billingLimit) maxOf(60 * 60_000L, e.retryAfterMs) else quotaBlockMs(e.retryAfterMs, e.perDay, now)
            blockQuota(key, scope, delay, now); true
        }
        else -> false
    }
}
