package com.dichgame.app

import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/** Resolve exactly once and return a private/local address; callers must connect to this address,
 * not re-resolve the original hostname (which would allow DNS-rebinding between validation and use). */
internal fun resolveLocalTvAddress(host: String): InetAddress? {
    val clean = host.trim().removePrefix("[").removeSuffix("]")
    if (clean.isEmpty() || clean.any { it.isWhitespace() || it.isISOControl() || it == '/' || it == '@' }) return null
    return try {
        InetAddress.getAllByName(clean).firstOrNull { address ->
            address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
                (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)
        }
    } catch (_: Exception) {
        null
    }
}

internal fun isLocalTvHost(host: String): Boolean = resolveLocalTvAddress(host) != null

/** Nói chuyện với app Dịch game TV đã ghép (cổng 8765). */
object TvLink {
    /**
     * Gửi âm thanh PCM 16 bit, một kênh sang TV để phát bằng loa TV. [speed] là tốc độ phát (1 = bình thường).
     * Trả về null nếu TV nhận được, hoặc một câu mô tả lỗi.
     */
    fun postAudio(ip: String, token: String, pcm: Pcm, speed: Float = 1f): String? {
        if (pcm.data.isEmpty()) return "không có âm thanh"
        if (pcm.channels != 1) return "âm thanh không phải một kênh"
        val address = resolveLocalTvAddress(ip) ?: return "Chỉ gửi âm thanh tới TV trong mạng nội bộ."
        return try {
            // Use the already-validated numeric address. Reusing the original hostname here
            // would perform a second DNS lookup and permit DNS-rebinding to a public endpoint.
            val numericHost = if (address is Inet6Address) {
                "[${address.hostAddress.replace("%", "%25")}]"
            } else address.hostAddress
            val c = URL("http://$numericHost:8765/audio").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 2000
                c.readTimeout = 6000
                c.doOutput = true
                c.setFixedLengthStreamingMode(pcm.data.size)
                c.setRequestProperty("X-Token", token)
                c.setRequestProperty("X-Rate", pcm.rate.toString())
                c.setRequestProperty("X-Speed", speed.toString())
                c.outputStream.use { it.write(pcm.data) }
                val code = c.responseCode
                if (code == 200) null else "TV trả mã $code"
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            e.message ?: "lỗi mạng"
        }
    }
}
