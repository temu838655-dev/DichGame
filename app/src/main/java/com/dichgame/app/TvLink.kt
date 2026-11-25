package com.dichgame.app

import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/** Only send live game audio to a TV on a local/private network, never an arbitrary public host. */
internal fun isLocalTvHost(host: String): Boolean {
    val clean = host.trim().removePrefix("[").removeSuffix("]")
    if (clean.isEmpty() || clean.any { it.isWhitespace() || it.isISOControl() || it == '/' || it == '@' }) return false
    return try {
        val addresses = InetAddress.getAllByName(clean)
        addresses.isNotEmpty() && addresses.all { address ->
            address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
                (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)
        }
    } catch (_: Exception) {
        false
    }
}

/** Nói chuyện với app Dịch game TV đã ghép (cổng 8765). */
object TvLink {
    /**
     * Gửi âm thanh PCM 16 bit, một kênh sang TV để phát bằng loa TV. [speed] là tốc độ phát (1 = bình thường).
     * Trả về null nếu TV nhận được, hoặc một câu mô tả lỗi.
     */
    fun postAudio(ip: String, token: String, pcm: Pcm, speed: Float = 1f): String? {
        if (pcm.data.isEmpty()) return "không có âm thanh"
        if (pcm.channels != 1) return "âm thanh không phải một kênh"
        if (!isLocalTvHost(ip)) return "Chỉ gửi âm thanh tới TV trong mạng nội bộ."
        return try {
            val host = if (ip.contains(':') && !ip.startsWith("[")) "[$ip]" else ip
            val c = URL("http://$host:8765/audio").openConnection() as HttpURLConnection
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
            c.disconnect()
            if (code == 200) null else "TV trả mã $code"
        } catch (e: Exception) {
            e.message ?: "lỗi mạng"
        }
    }
}
