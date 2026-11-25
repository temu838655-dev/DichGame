package com.dichgame.app

/**
 * Chuỗi model dùng để dịch. Google tính hạn mức Gemini riêng cho từng (dự án, model), nên khi mọi khóa đã hết lượt ở model đang chọn
 * thì vẫn còn lượt ở model khác. Model Flash-Lite có hạn mức miễn phí cao hơn nhiều so với Flash. Không phụ thuộc Android.
 */
object ModelChain {
    const val DEFAULT = "gemini-3.5-flash-lite"
    private val FALLBACKS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

    /** Model dự phòng máy chủ báo không tồn tại (404): bỏ qua tới khi mở lại app. */
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Model người dùng chọn luôn đứng đầu, sau đó tới các model dự phòng. */
    fun models(primary: String): List<String> = (listOf(primary) + FALLBACKS.filter { it !in missing }).distinct()

    fun reset() = missing.clear()

    /** Còn bao lâu nữa mới có một (khóa, model) dùng được; 0 nếu dùng được ngay. */
    fun waitMs(keys: List<String>, primary: String, now: Long = System.currentTimeMillis()): Long =
        models(primary).minOf { KeyRing.waitMs(keys, it, now) }

    /**
     * Gọi [call] lần lượt theo chuỗi model, bỏ qua model mà mọi khóa đang nghỉ. Chỉ đổi model khi hết lượt (429)
     * hoặc model dự phòng không tồn tại (404). Lỗi khác ném lại ngay.
     */
    fun <T> run(keys: List<String>, primary: String, call: (String) -> T): T {
        var last: AiException? = null
        for (m in models(primary)) {
            if (KeyRing.waitMs(keys, m) > 0L) continue
            try {
                return call(m)
            } catch (e: AiException) {
                when {
                    e.code == 429 -> last = e
                    e.code == 404 && m != primary -> missing.add(m)
                    else -> throw e
                }
            }
        }
        if (KeyRing.allBad(keys)) throw AiException(401, "Mọi khóa Gemini đều sai hoặc bị từ chối", badKey = true)
        throw AiException(429, "Mọi khóa Gemini đều hết lượt ở mọi model", waitMs(keys, primary), perDay = last?.perDay == true)
    }
}
