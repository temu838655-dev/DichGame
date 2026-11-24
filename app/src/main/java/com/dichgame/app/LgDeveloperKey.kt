package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

/** Blocking developer-key retrieval; call only on a worker thread after user consent. */
internal object LgDeveloperKey {
    fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4 || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return false
        val n = parts.map { it.toInt() }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) ||
            (n[0] == 192 && n[1] == 168)
    }

    fun fetch(ip: String): ByteArray {
        require(isPrivateIpv4(ip)) { "IP TV phải thuộc mạng nội bộ" }
        val conn = URL("http://$ip:9991/webos_rsa").openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            check(conn.responseCode == 200) { "Không lấy được khóa Developer Mode" }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(2048)
                while (true) {
                    val count = input.read(buf)
                    if (count < 0) break
                    check(out.size() + count <= 32768) { "Khóa vượt giới hạn" }
                    out.write(buf, 0, count)
                }
            }
            val bytes = out.toByteArray()
            check(bytes.toString(Charsets.US_ASCII).startsWith("-----BEGIN ") &&
                bytes.toString(Charsets.US_ASCII).contains("PRIVATE KEY-----")) { "Khóa không hợp lệ" }
            return bytes
        } finally {
            conn.disconnect()
        }
    }
}
