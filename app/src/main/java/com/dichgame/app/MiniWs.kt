package com.dichgame.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Máy khách WebSocket tối giản (RFC 6455) chỉ để nói chuyện với TV trong mạng nhà, không cần thư viện ngoài.
 * Hỗ trợ khung văn bản, ping/pong, đóng kết nối và gói tin bị chia nhỏ. Không nén, không giao thức phụ.
 *
 * Với TLS ([connect] có tls = true) chấp nhận chứng chỉ tự ký, vì TV LG dùng chứng chỉ tự ký ở cổng 3001.
 * Chỉ dùng cho địa chỉ TV do chính người dùng chọn trong mạng nội bộ.
 */
class MiniWs private constructor(private val sock: Socket) {
    private val ins: InputStream = sock.getInputStream()
    private val outs: OutputStream = sock.getOutputStream()
    private val rnd = SecureRandom()

    @Volatile var closed = false
        private set

    /** Gửi một tin văn bản. */
    fun send(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        sendFrame(0x1, payload)
    }

    private fun sendFrame(opcode: Int, payload: ByteArray) {
        val head = ByteArrayOutputStream()
        head.write(0x80 or opcode)
        val n = payload.size
        when {
            n < 126 -> head.write(0x80 or n)
            n < 65536 -> { head.write(0x80 or 126); head.write(n ushr 8); head.write(n and 255) }
            else -> {
                head.write(0x80 or 127)
                for (i in 7 downTo 0) head.write(((n.toLong() ushr (8 * i)) and 255L).toInt())
            }
        }
        val mask = ByteArray(4).also { rnd.nextBytes(it) }
        head.write(mask)
        val body = ByteArray(n) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
        synchronized(outs) {
            outs.write(head.toByteArray())
            outs.write(body)
            outs.flush()
        }
    }

    private fun readFully(n: Int): ByteArray {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(b, off, n - off)
            if (r < 0) { closed = true; throw java.io.EOFException("kết nối đã đóng") }
            off += r
        }
        return b
    }

    /**
     * Đọc tin văn bản kế tiếp. Trả về null khi phía bên kia đóng kết nối.
     * Ném [SocketTimeoutException] nếu quá thời gian chờ (đặt bằng [setTimeout]); gọi lại được.
     */
    fun receive(): String? {
        var text: ByteArrayOutputStream? = null
        while (true) {
            val h = readFully(2)
            val fin = (h[0].toInt() and 0x80) != 0
            val op = h[0].toInt() and 0x0F
            val masked = (h[1].toInt() and 0x80) != 0
            var len = (h[1].toInt() and 0x7F).toLong()
            if (len == 126L) { val e = readFully(2); len = ((e[0].toInt() and 255) shl 8 or (e[1].toInt() and 255)).toLong() }
            else if (len == 127L) {
                val e = readFully(8)
                len = 0
                for (x in e) len = (len shl 8) or (x.toLong() and 255)
            }
            if (len < 0 || len > 4_000_000) { closed = true; throw java.io.IOException("khung tin quá lớn") }
            val mask = if (masked) readFully(4) else null
            val data = readFully(len.toInt())
            if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (op) {
                0x8 -> { closed = true; try { sendFrame(0x8, ByteArray(0)) } catch (_: Exception) {}; return null }
                0x9 -> { try { sendFrame(0xA, data) } catch (_: Exception) {} }
                0xA -> {}
                0x1, 0x0 -> {
                    if (op == 0x1) text = ByteArrayOutputStream()
                    text?.write(data)
                    if (fin) return String((text ?: ByteArrayOutputStream()).toByteArray(), Charsets.UTF_8)
                }
            }
        }
    }

    fun setTimeout(ms: Int) { sock.soTimeout = ms }

    fun close() {
        if (closed && sock.isClosed) return
        closed = true
        try { sendFrame(0x8, ByteArray(0)) } catch (_: Exception) {}
        try { sock.close() } catch (_: Exception) {}
    }

    companion object {
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        fun accept(key: String): String =
            java.util.Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray()))

        private val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        /** Kết nối và bắt tay WebSocket. Ném IOException nếu không được. */
        fun connect(host: String, port: Int, tls: Boolean, timeoutMs: Int = 4000, path: String = "/"): MiniWs {
            val raw = Socket()
            try {
                raw.connect(InetSocketAddress(host, port), timeoutMs)
                raw.soTimeout = timeoutMs
                raw.tcpNoDelay = true
                val s: Socket = if (tls) {
                    val ctx = SSLContext.getInstance("TLS")
                    ctx.init(null, arrayOf(trustAll), SecureRandom())
                    (ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket).also { it.startHandshake() }
                } else raw
                val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val key = java.util.Base64.getEncoder().encodeToString(keyBytes)
                val req = "GET $path HTTP/1.1\r\nHost: $host:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n"
                s.getOutputStream().write(req.toByteArray())
                s.getOutputStream().flush()
                // đọc phần đầu phản hồi HTTP đến dòng trống
                val ins = s.getInputStream()
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) {
                    val c = ins.read()
                    if (c < 0) throw java.io.IOException("TV đóng kết nối khi bắt tay")
                    head.append(c.toChar())
                    if (head.length > 8192) throw java.io.IOException("phản hồi bắt tay quá dài")
                }
                val lines = head.toString().split("\r\n")
                if (!lines[0].contains(" 101")) throw java.io.IOException("TV không nâng cấp lên WebSocket (${lines[0].trim()})")
                val got = lines.firstOrNull { it.startsWith("Sec-WebSocket-Accept:", true) }?.substringAfter(':')?.trim()
                if (got != accept(key)) throw java.io.IOException("TV trả sai khóa bắt tay WebSocket")
                return MiniWs(s)
            } catch (e: Exception) {
                try { raw.close() } catch (_: Exception) {}
                throw if (e is java.io.IOException) e else java.io.IOException(e.message ?: e.toString())
            }
        }
    }
}
