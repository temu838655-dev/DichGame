package com.dichgame.app

/** Điều phối vòng đời khóa API; KeyRing giữ state/cooldown, KeyManager retry và ghi log đã che khóa. */
object KeyManager {
    fun <T> withKeys(provider: String, sourceKeys: List<String>, scope: String, call: (String) -> T): T {
        val keys = sourceKeys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) {
            TranslationLog.setHealth(TranslationLog.Health.FAILED, "Chưa cấu hình khóa " + provider)
            throw AiException(401, "Chưa có khóa API cho " + provider, badKey = true)
        }
        var last: AiException? = null
        while (true) {
            val key = KeyRing.acquire(keys, scope) ?: break
            val index = keys.indexOf(key) + 1
            try {
                val result = call(key)
                if (KeyRing.success(key, scope)) SessionStats.live.inc("khoa_doi")
                TranslationLog.write("INFO", provider, index, key, null, "Key #" + index + " hoạt động; request thành công")
                return result
            } catch (e: AiException) {
                last = e
                val marked = KeyRing.report(key, scope, e)
                if (!marked) {
                    TranslationLog.write("ERROR", provider, index, key, e.code, "Lỗi không thể khắc phục bằng đổi key; không retry vô hạn")
                    throw e
                }
                val next = KeyRing.pick(keys, scope)
                val action = when {
                    e.billingLimit -> "Giới hạn billing cấp tài khoản/dự án; dừng xoay key"
                    next != null -> "Key #" + index + " gặp HTTP " + e.code + " -> đã chuyển sang Key #" + (keys.indexOf(next) + 1)
                    e.badKey -> "Key #" + index + " không hợp lệ -> vô hiệu hóa trong phiên; không còn key hợp lệ"
                    else -> "Key #" + index + " gặp HTTP 429 -> cooldown; không còn key khả dụng"
                }
                TranslationLog.write(if (e.badKey) "ERROR" else "WARN", provider, index, key, e.code, action)
                if (e.billingLimit) {
                    TranslationLog.setHealth(TranslationLog.Health.FALLBACK, "Giới hạn billing của " + provider)
                    throw e
                }
            } catch (e: Exception) {
                TranslationLog.write("ERROR", provider, index, key, null, "Request thất bại do lỗi mạng/runtime; giải phóng key")
                throw e
            } finally {
                KeyRing.release(key, scope)
            }
        }
        if (KeyRing.allBad(keys)) {
            TranslationLog.setHealth(TranslationLog.Health.FAILED, "Tất cả khóa " + provider + " sai hoặc bị thu hồi")
            throw last ?: AiException(401, "Mọi khóa " + provider + " đều sai hoặc bị thu hồi", badKey = true)
        }
        if (KeyRing.hasInFlight(keys, scope)) {
            TranslationLog.setHealth(TranslationLog.Health.FALLBACK, "Các khóa " + provider + " đang được request khác sử dụng")
            throw AiException(503, "Các khóa API đang được request khác sử dụng; thử lại sau.", retryAfterMs = 500L)
        }
        TranslationLog.setHealth(TranslationLog.Health.FALLBACK, "Tất cả khóa " + provider + " đang cooldown; dùng nhà cung cấp dự phòng")
        throw KeyRing.unavailableError(keys, scope) ?: last
            ?: AiException(429, "Mọi khóa " + provider + " đều tạm nghỉ", KeyRing.waitMs(keys, scope))
    }

    fun reportSingle(provider: String, key: String, scope: String, error: AiException) {
        val clean = key.trim()
        val marked = clean.isNotEmpty() && KeyRing.report(clean, scope, error)
        val action = when {
            error.billingLimit -> "Đã khóa dự phòng do giới hạn billing; đổi credit hoặc tài khoản"
            error.badKey && marked -> "Key dự phòng không hợp lệ -> vô hiệu hóa trong phiên"
            error.code == 429 && marked -> "Key dự phòng gặp 429 -> cooldown theo Retry-After/quota"
            else -> "Nhà cung cấp dự phòng trả lỗi; chuyển dịch máy nếu cần"
        }
        TranslationLog.write(if (marked) "WARN" else "ERROR", provider, if (clean.isEmpty()) null else 1, clean, error.code, action)
        if (marked) TranslationLog.setHealth(
            if (error.badKey) TranslationLog.Health.FAILED else TranslationLog.Health.FALLBACK,
            if (error.badKey) provider + " key sai hoặc bị thu hồi" else provider + " đang cooldown"
        )
    }

    fun successSingle(provider: String, key: String, scope: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        if (KeyRing.success(clean, scope)) SessionStats.live.inc("khoa_doi")
        TranslationLog.write("INFO", provider, 1, clean, null, "Request thành công")
    }
}
