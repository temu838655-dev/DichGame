package com.dichgame.app

import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.security.SecureRandom

/** Browser-based login using Samsung SDK's loopback callback. No password capture or persistence. */
internal class SamsungAccountLogin : java.io.Closeable {
    private val state = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private val listener: ServerSocket = (4794..4813).shuffled().firstNotNullOfOrNull { port ->
        runCatching { ServerSocket(port, 4, InetAddress.getByName("127.0.0.1")) }.getOrNull()
    } ?: error("Không mở được cổng đăng nhập Samsung")
    private val redirect = "http://localhost:${listener.localPort}/signin/callback?dg_state=$state"
    val url = "https://account.samsung.com/mobile/account/check.do?serviceID=v285zxnl3h&actionID=StartOAuth2&accessToken=Y&redirect_uri=" +
        URLEncoder.encode(redirect,"UTF-8") + "&state=$state"

    private fun line(input: InputStream): String? {
        val out = java.io.ByteArrayOutputStream()
        repeat(65536) {
            val b = input.read()
            if (b < 0) return null
            if (b == 10) return out.toString("UTF-8").trimEnd('\r')
            out.write(b)
        }
        return null
    }

    fun await(): SamsungCertificateAuthority.Account {
        val deadline = System.nanoTime() + 300_000_000_000L
        listener.soTimeout = 1000
        while (!listener.isClosed && System.nanoTime() < deadline) {
            val peer = try { listener.accept() } catch (_: java.net.SocketTimeoutException) { continue }
            peer.use { c ->
                c.soTimeout = 5000
                val result = runCatching {
                    val input = c.getInputStream().buffered()
                    val request = line(input) ?: error("Invalid callback")
                    val headers = linkedMapOf<String,String>()
                    var ended = false
                    repeat(32) {
                        if (!ended) {
                            val h = line(input) ?: error("Incomplete callback")
                            if (h.isEmpty()) ended = true
                            else {
                                require(h.contains(':'))
                                val key = h.substringBefore(':').lowercase()
                                require(key !in headers || key !in listOf("host","origin","content-type","content-length","transfer-encoding"))
                                headers[key] = h.substringAfter(':').trim()
                            }
                        }
                    }
                    require(ended)
                    val length = headers["content-length"]?.let { it.toIntOrNull() ?: error("Invalid length") } ?: 0
                    require(length in 0..65536 && !headers.containsKey("transfer-encoding"))
                    val body = ByteArray(length)
                    java.io.DataInputStream(input).readFully(body)
                    SamsungOAuthCallback.parse(request,headers,body.toString(Charsets.UTF_8),listener.localPort,state)
                }.getOrNull()
                val html = if (result == null) "Invalid Samsung callback. Return to DichGame and retry." else "Samsung login received. Return to DichGame to confirm the account."
                val body = html.toByteArray(Charsets.UTF_8)
                runCatching {
                    c.getOutputStream().apply {
                        write(("HTTP/1.1 " + if (result == null) "400 Bad Request" else "200 OK").toByteArray())
                        write("\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush()
                    }
                }
                if (result != null) return result
            }
        }
        error("Đăng nhập Samsung hết hạn hoặc đã hủy")
    }
    override fun close() { runCatching { listener.close() } }
}
