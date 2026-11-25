package com.dichgame.app

import java.net.URI

/** Only permit ordinary web pages in the in-app browser; never load local files or custom schemes. */
internal fun isAllowedWebTranslateUrl(raw: String): Boolean {
    val uri = try { URI(raw.trim()) } catch (_: Exception) { return false }
    val scheme = uri.scheme?.lowercase() ?: return false
    return (scheme == "http" || scheme == "https") &&
        !uri.host.isNullOrBlank() && uri.userInfo.isNullOrEmpty() &&
        (uri.port == -1 || uri.port in 1..65535)
}

/** Bounds text entering the JavaScript bridge so untrusted pages cannot flood AI requests. */
internal class WebCaptionGate(
    private val minIntervalMs: Long = 500L,
    private val maxLength: Int = 4000
) {
    private var lastAcceptedAt = -1L
    private var lastText = ""

    @Synchronized fun accept(raw: String, nowMs: Long): String? {
        val text = raw.trim()
        if (text.isEmpty() || text.length > maxLength) return null
        if (text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) return null
        if (text == lastText) return null
        if (lastAcceptedAt >= 0 && nowMs >= lastAcceptedAt && nowMs - lastAcceptedAt < minIntervalMs) return null
        lastText = text
        lastAcceptedAt = nowMs
        return text
    }

    @Synchronized fun reset() {
        lastAcceptedAt = -1L
        lastText = ""
    }
}
