package com.dichgame.update

/** Kiểm tra Content-Range trước khi nối dữ liệu tải tiếp vào APK tạm. */
internal data class DownloadRange(val endExclusive: Long, val total: Long)

internal object DownloadRangeParser {
    fun parse(
        header: String,
        requestedStart: Long,
        contentLength: Long,
        maxBytes: Long,
        expectedSize: Long
    ): DownloadRange? {
        val match = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$").matchEntire(header.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeIf { it != "*" }?.toLongOrNull() ?: -1L
        if (start != requestedStart || end < start || end >= maxBytes) return null
        val length = end - start + 1
        if (contentLength > 0 && contentLength != length) return null
        if (total > 0 && (total <= end || total > maxBytes)) return null
        if (expectedSize > 0 && total > 0 && total != expectedSize) return null
        if (expectedSize > 0 && end >= expectedSize) return null
        return DownloadRange(end + 1, total)
    }
}
