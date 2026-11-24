package com.dichgame.update

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/**
 * Hỏi GitHub xem có bản phát hành mới không. Dùng chung cho app điện thoại và app TV (mỗi app một file APK riêng trong cùng bản phát hành).
 * Mỗi bản phát hành tự động có tên "build-<số>" và số đó chính là số bản dựng ghi trong app (BuildConfig.BUILD_NO).
 */
object Update {
    const val REPO = "temu838655-dev/DichGame"
    const val API = "https://api.github.com/repos/$REPO/releases?per_page=20"

    /** Chỉ dùng khi kiểm thử bản debug trên máy giả lập: trỏ việc kiểm tra sang một máy chủ giả. Bản phát hành bỏ qua giá trị này. */
    @Volatile var testApi: String? = null

    /** [no] số bản dựng, [apk] đường dẫn tải file APK, [size] dung lượng (byte, -1 nếu chưa biết), [notes] ghi chú "có gì mới". */
    class Info(val no: Int, val page: String, val apk: String, val size: Long, val notes: String, val sha256: String? = null)

    private val TAG = Regex("^build-(\\d+)$")
    private val NAME = Regex("^Bản dựng (\\d+)")

    /** Số bản dựng của một bản phát hành: lấy từ tag build-N, hoặc từ tên "Bản dựng N" (bản phát hành gắn tag v*). */
    internal fun buildNo(tag: String, name: String): Int? =
        TAG.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: NAME.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Đọc danh sách bản phát hành của GitHub, trả về bản có số lớn nhất mà đã có file [assetName]
     * (bản đang đẩy file lên dở, bản nháp và bản thử nghiệm bị bỏ qua).
     */
    fun parse(json: String, assetName: String): Info? {
        val arr = JSONArray(json)
        var best: Info? = null
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val n = buildNo(r.optString("tag_name"), r.optString("name")) ?: continue
            val cur = best
            if (cur != null && n <= cur.no) continue
            val assets = r.optJSONArray("assets") ?: continue
            var url: String? = null
            var size = -1L
            var sha256: String? = null
            for (k in 0 until assets.length()) {
                val a = assets.getJSONObject(k)
                if (a.optString("name") == assetName && a.optString("state", "uploaded") == "uploaded") {
                    url = a.optString("browser_download_url").ifEmpty { null }
                    size = a.optLong("size", -1L)
                    sha256 = digest(a.optString("digest"))
                }
            }
            if (url == null) continue
            best = Info(n, r.optString("html_url"), url, size, cleanNotes(r.optString("body")), sha256)
        }
        return best
    }

    internal fun digest(raw: String): String? =
        raw.takeIf { Regex("^sha256:[0-9a-fA-F]{64}$").matches(it) }?.substringAfter(':')?.lowercase(java.util.Locale.ROOT)

    internal fun validAssetUrl(url: String, assetName: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host.equals("github.com", true) &&
            uri.userInfo == null && uri.port in listOf(-1, 443) && uri.query == null && uri.fragment == null &&
            uri.path.startsWith("/$REPO/releases/download/") && uri.path.substringAfterLast('/') == assetName &&
            uri.normalize().path == uri.path
    }.getOrDefault(false)

    internal fun verifyDigest(file: java.io.File, expected: String?): Boolean {
        if (expected == null) return true // older releases / redirect fallback rely on Android signature
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        return actual == expected
    }

    /** Ghi chú bản phát hành lấy từ lời nhắn commit: bỏ dòng ký tên tự động, dòng "Full Changelog", gọn lại còn tối đa 700 ký tự. */
    fun cleanNotes(raw: String): String {
        val keep = raw.replace("\r", "").lines().filter {
            val t = it.trim()
            !t.startsWith("Co-Authored-By", true) && !t.startsWith("Claude-Session", true) &&
                !t.startsWith("**Full Changelog**") && !t.startsWith("## What's Changed")
        }
        val s = keep.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return if (s.length <= 700) s else s.take(700).trimEnd() + "…"
    }

    /** Chặn luồng, hãy gọi từ luồng nền. Ném lỗi nếu không hỏi được GitHub. null nếu chưa có bản phát hành nào dùng được. */
    fun latest(assetName: String): Info? {
        val test = testApi
        if (test != null) return parse(get(test), assetName)
        try {
            return parse(get(API), assetName)?.takeIf { validAssetUrl(it.apk, assetName) }
        } catch (e: Exception) {
            // API của GitHub giới hạn số lượt gọi theo địa chỉ mạng (60 lượt mỗi giờ): hỏi trang releases/latest, không tính vào hạn mức này
            return viaRedirect(assetName) ?: throw e
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "DichGame-updater")
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP " + c.responseCode)
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun viaRedirect(assetName: String): Info? {
        val c = URL("https://github.com/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.requestMethod = "HEAD"
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "DichGame-updater")
            val loc = c.getHeaderField("Location") ?: return null
            val n = TAG.find(loc.substringAfterLast("/tag/"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return Info(n, "https://github.com/$REPO/releases/tag/build-$n",
                "https://github.com/$REPO/releases/download/build-$n/$assetName", -1L, "")
        } finally {
            c.disconnect()
        }
    }
}
