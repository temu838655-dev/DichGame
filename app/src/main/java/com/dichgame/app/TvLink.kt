package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL

/** Nói chuyện với app Dịch game TV đã ghép (cổng 8765). */
object TvLink {
    /**
     * Gửi âm thanh PCM 16 bit, một kênh sang TV để phát bằng loa TV.
     * Trả về null nếu TV nhận được, hoặc một câu mô tả lỗi.
     */
    fun postAudio(ip: String, token: String, pcm: Pcm): String? {
        if (pcm.data.isEmpty()) return "không có âm thanh"
        return try {
            val c = URL("http://$ip:8765/audio").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 2000
            c.readTimeout = 6000
            c.doOutput = true
            c.setFixedLengthStreamingMode(pcm.data.size)
            c.setRequestProperty("X-Token", token)
            c.setRequestProperty("X-Rate", pcm.rate.toString())
            c.outputStream.use { it.write(pcm.data) }
            val code = c.responseCode
            c.disconnect()
            if (code == 200) null else "TV trả mã $code"
        } catch (e: Exception) {
            e.message ?: "lỗi mạng"
        }
    }
}
