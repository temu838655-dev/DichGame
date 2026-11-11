package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/** Kiểm tra bản mới trên trang Releases của dự án (bản dựng tự động có tên build-<số>). */
object Update {
    const val REPO = "temu838655-dev/DichGame"

    class Info(val no: Int, val page: String, val apk: String?)

    /** Chặn luồng, hãy gọi từ luồng nền. Trả về bản dựng có số lớn nhất, hoặc null nếu chưa có bản nào. */
    fun latest(): Info? {
        val c = URL("https://api.github.com/repos/$REPO/releases?per_page=20").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP " + c.responseCode)
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            val re = Regex("^build-(\\d+)$")
            var best: Info? = null
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val n = re.find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val cur = best
                if (cur != null && n <= cur.no) continue
                var apk: String? = null
                val assets = r.optJSONArray("assets")
                if (assets != null) {
                    for (k in 0 until assets.length()) {
                        val a = assets.getJSONObject(k)
                        if (a.optString("name") == "DichGame-phone-release.apk") apk = a.optString("browser_download_url")
                    }
                }
                best = Info(n, r.optString("html_url"), apk)
            }
            return best
        } finally {
            c.disconnect()
        }
    }
}
