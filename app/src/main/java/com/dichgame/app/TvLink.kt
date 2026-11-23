package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL

/** Nói chuyện với app Dịch game TV đã ghép (cổng 8765). */
object TvLink {

    /** Dùng chung giao thức /sub với CaptureService để phụ đề video trên web hiện trên Android TV. */
    fun postSubtitle(ip: String, token: String, text: String, size: Int, speak: Boolean = false): String? {
        if (ip.isBlank() || token.isBlank()) return "TV chưa ghép nối"
        return try {
            val c = URL("http://$ip:8765/sub").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 1500
                c.readTimeout = 2000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setRequestProperty("X-Token", token)
                val body = org.json.JSONObject()
                    .put("kind", "sub").put("text", text.take(2500))
                    .put("size", size.coerceIn(14, 80))
                    .put("speak", speak)
                    .toString().toByteArray(Charsets.UTF_8)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
                if (c.responseCode == 200) null else "TV trả mã " + c.responseCode
            } finally { c.disconnect() }
        } catch (e: Exception) { e.message ?: "lỗi mạng TV" }
    }
    /**
     * Gửi âm thanh PCM 16 bit, một kênh sang TV để phát bằng loa TV. [speed] là tốc độ phát (1 = bình thường).
     * Trả về null nếu TV nhận được, hoặc một câu mô tả lỗi.
     */
    fun postAudio(ip: String, token: String, pcm: Pcm, speed: Float = 1f): String? {
        if (pcm.data.isEmpty()) return "không có âm thanh"
        if (pcm.channels != 1) return "âm thanh không phải một kênh"
        return try {
            val c = URL("http://$ip:8765/audio").openConnection() as HttpURLConnection
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
