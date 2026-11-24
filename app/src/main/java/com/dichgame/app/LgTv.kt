package com.dichgame.app

import java.net.SocketTimeoutException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Điều khiển TV LG webOS qua giao thức SSAP của "LG Connect Apps" (WebSocket cổng 3000, hoặc 3001 có TLS ở TV đời mới).
 * Dùng để ghép nối (TV hiện hộp hỏi "Cho phép kết nối?") và gửi thông báo chữ ngắn ([toast]) hiện đè lên mọi cổng vào, kể cả HDMI.
 *
 * THỬ NGHIỆM: viết theo mô tả giao thức công khai và kiểm thử với TV giả trên máy; CHƯA thử trên TV LG thật.
 * Mọi lỗi từ TV được trả về nguyên văn để người dùng thấy rõ nguyên nhân.
 */
class LgTv(
    private val ip: String,
    clientKey: String = "",
    private val tlsPorts: List<Int> = listOf(3001),
    private val plainPorts: List<Int> = listOf(3000),
) {
    /** Khóa ghép nối do TV cấp; lưu lại để lần sau không phải bấm cho phép nữa. */
    var key: String = clientKey
        private set

    private var ws: MiniWs? = null
    private var seq = 0

    val connected: Boolean get() = ws?.closed == false

    fun close() {
        try { ws?.close() } catch (_: Exception) {}
        ws = null
    }

    private fun manifest(): JSONObject = JSONObject()
        .put("manifestVersion", 1)
        .put("appVersion", "1.0")
        .put("permissions", JSONArray(listOf("LAUNCH", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO")))

    private fun registerMessage(): String = JSONObject()
        .put("type", "register")
        .put("id", "register_0")
        .put("payload", JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .apply { if (key.isNotEmpty()) put("client-key", key) }
            .put("manifest", manifest()))
        .toString()

    /**
     * Kết nối và ghép nối. Lần đầu TV hiện hộp hỏi cho phép: [onPrompt] được gọi khi TV đã hiện hộp, chờ tối đa [approveMs].
     * Trả về null nếu thành công (đã có [key]), hoặc câu mô tả lỗi.
     */
    fun connect(approveMs: Long = 60_000, onPrompt: () -> Unit = {}): String? {
        close()
        var lastErr = "không kết nối được cổng điều khiển của TV"
        val tries = tlsPorts.map { it to true } + plainPorts.map { it to false }
        for ((port, tls) in tries) {
            val sock = try { MiniWs.connect(ip, port, tls, 3500) } catch (e: Exception) {
                lastErr = "cổng $port: ${e.message ?: e.javaClass.simpleName}"
                continue
            }
            var reason: String? = null
            try {
                sock.send(registerMessage())
                sock.setTimeout(1000)
                val end = System.currentTimeMillis() + approveMs
                loop@ while (System.currentTimeMillis() < end) {
                    val msg: String? = try { sock.receive() } catch (_: SocketTimeoutException) { continue@loop }
                    if (msg == null) { reason = "TV đóng kết nối khi ghép nối"; break@loop }
                    val j = try { JSONObject(msg) } catch (_: Exception) { continue@loop }
                    when (j.optString("type")) {
                        "registered" -> {
                            val k = j.optJSONObject("payload")?.optString("client-key").orEmpty()
                            if (k.isEmpty()) { reason = "TV không cấp khóa ghép nối"; break@loop }
                            key = k
                            ws = sock
                            return null
                        }
                        "response" -> {
                            if (j.optJSONObject("payload")?.optString("pairingType") == "PROMPT") onPrompt()
                        }
                        "error" -> {
                            reason = "TV từ chối: " + j.optString("error", j.optString("payload"))
                            break@loop
                        }
                    }
                }
                if (reason == null) reason = "Hết ${approveMs / 1000} giây mà chưa bấm Cho phép trên TV"
            } catch (e: Exception) {
                reason = e.message ?: e.javaClass.simpleName
            }
            lastErr = reason ?: lastErr
            try { sock.close() } catch (_: Exception) {}
            // TV đã trả lời ghép nối (đã hiện hộp hỏi hoặc từ chối): không thử cổng khác nữa
            if (lastErr.startsWith("Hết") || lastErr.startsWith("TV từ chối")) return lastErr
        }
        return lastErr
    }

    /**
     * Gửi thông báo chữ lên TV (ssap://system.notifications/createToast). Trả về null nếu TV nhận, hoặc câu lỗi.
     * Phải gọi [connect] trước và nên gọi từ luồng nền.
     */
    fun launchReceiver(): String? {
        val s = ws ?: return "chưa kết nối TV"
        val id = "launch_${++seq}"
        return try {
            s.send(JSONObject().put("type", "request").put("id", id)
                .put("uri", "ssap://system.launcher/launch")
                .put("payload", JSONObject().put("id", "com.dichgame.subtitles")).toString())
            s.setTimeout(3000)
            val end = System.currentTimeMillis() + 5000
            var result: String? = "TV chưa xác nhận mở ứng dụng"
            while (System.currentTimeMillis() < end) {
                val raw = s.receive() ?: break
                val j = JSONObject(raw)
                if (j.optString("id") != id) continue
                result = if (j.optString("type") == "response" &&
                    j.optJSONObject("payload")?.optBoolean("returnValue", false) == true) null
                    else j.optString("error", j.optJSONObject("payload")?.optString("errorText") ?: "TV không mở được ứng dụng")
                break
            }
            result
        } catch (e: Exception) { e.message ?: "lỗi mở ứng dụng" }
    }

    fun toast(text: String): String? {
        val s = ws ?: return "chưa kết nối TV"
        val id = "toast_${++seq}"
        val msg = JSONObject().put("type", "request").put("id", id)
            .put("uri", "ssap://system.notifications/createToast")
            .put("payload", JSONObject().put("message", text.take(160))).toString()
        return try {
            s.send(msg)
            s.setTimeout(2500)
            val end = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < end) {
                val r = try { s.receive() } catch (_: SocketTimeoutException) { break } ?: return "TV đã đóng kết nối"
                val j = try { JSONObject(r) } catch (_: Exception) { continue }
                if (j.optString("id") != id) continue
                if (j.optString("type") == "error") return "TV từ chối: " + j.optString("error", j.optString("payload"))
                val pl = j.optJSONObject("payload")
                if (pl != null && pl.has("returnValue") && !pl.optBoolean("returnValue", true)) {
                    return "TV báo lỗi: " + pl.optString("errorText", pl.toString())
                }
                return null
            }
            null // TV không trả lời trong hạn: coi như đã gửi (một số đời TV không phản hồi toast)
        } catch (e: Exception) {
            close()
            e.message ?: e.javaClass.simpleName
        }
    }
}

/** Tìm TV LG webOS trong mạng bằng SSDP (M-SEARCH tới 239.255.255.250:1900). */
object LgDiscover {
    const val ST = "urn:lge-com:service:webos-second-screen:1"

    fun packet(): ByteArray =
        ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $ST\r\n\r\n").toByteArray()

    /** Phản hồi SSDP có đúng là của TV LG webOS không. */
    fun isLg(reply: String): Boolean {
        val first = reply.lineSequence().firstOrNull()?.trim().orEmpty()
        if (!first.startsWith("HTTP/1.1 200")) return false
        val low = reply.lowercase()
        return low.contains("webos-second-screen") || low.contains("server: webos") || low.contains("lge")
    }

    /** Gửi M-SEARCH qua từng mạng Wi-Fi/LAN và gom địa chỉ các TV LG trả lời trong [ms] mili giây. Đóng các ổ trong [legs] khi xong. */
    fun search(legs: List<PsLeg>, ms: Long = 3500): List<String> {
        val found = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
        val q = packet()
        val readers = legs.map { leg ->
            Thread {
                val buf = ByteArray(2048)
                val end = System.currentTimeMillis() + ms
                try { leg.socket.soTimeout = 300 } catch (_: Throwable) {}
                while (System.currentTimeMillis() < end) {
                    try {
                        val pk = java.net.DatagramPacket(buf, buf.size)
                        leg.socket.receive(pk)
                        if (isLg(String(pk.data, 0, pk.length))) pk.address?.hostAddress?.let { found.add(it) }
                    } catch (_: java.net.SocketTimeoutException) {
                    } catch (_: Throwable) { break }
                }
            }.apply { isDaemon = true; start() }
        }
        for (round in 0 until 3) {
            for (leg in legs) {
                try { leg.socket.send(java.net.DatagramPacket(q, q.size, java.net.InetAddress.getByName("239.255.255.250"), 1900)) } catch (_: Throwable) {}
            }
            try { Thread.sleep(ms / 4) } catch (_: InterruptedException) {}
        }
        for (r in readers) try { r.join(ms + 500) } catch (_: InterruptedException) {}
        for (l in legs) try { l.socket.close() } catch (_: Throwable) {}
        return found.toList()
    }
}
