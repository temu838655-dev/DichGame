package com.dichgame.app

import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import org.json.JSONObject

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

    private fun params(s: String): Map<String,String> = s.split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i < 0) null else URLDecoder.decode(it.substring(0,i),"UTF-8") to URLDecoder.decode(it.substring(i+1),"UTF-8")
    }.toMap()

    fun await(): SamsungCertificateAuthority.Account {
        val deadline = System.nanoTime() + 300_000_000_000L
        listener.soTimeout = 1000
        while (!listener.isClosed && System.nanoTime() < deadline) {
            val peer = try { listener.accept() } catch (_: java.net.SocketTimeoutException) { continue }
            peer.use { c ->
                c.soTimeout = 5000
                val result = runCatching {
                    val input = c.getInputStream().buffered()
                    val request = line(input)?.split(' ') ?: error("Invalid callback")
                    require(request.size == 3 && request[0] in listOf("GET","POST"))
                    require(request[1].substringBefore('?') == "/signin/callback")
                    val headers = linkedMapOf<String,String>()
                    var ended = false
                    repeat(32) {
                        if (!ended) {
                            val h = line(input) ?: error("Incomplete callback")
                            if (h.isEmpty()) ended = true
                            else { require(h.contains(':')); headers[h.substringBefore(':').lowercase()] = h.substringAfter(':').trim() }
                        }
                    }
                    require(ended)
                    require(headers["host"] in listOf("localhost:${listener.localPort}","127.0.0.1:${listener.localPort}"))
                    headers["origin"]?.let { origin ->
                        require(origin == "https://account.samsung.com" || origin == "null")
                    }
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    require(length in 0..65536 && !headers.containsKey("transfer-encoding"))
                    val body = ByteArray(length)
                    java.io.DataInputStream(input).readFully(body)
                    val values = params(request[1].substringAfter('?',"")) + params(body.toString(Charsets.UTF_8))
                    // A missing or wrong state is never accepted; fail closed if Samsung changes callback semantics.
                    require(values["dg_state"] == state || values["state"] == state)
                    val code = JSONObject(values["code"] ?: error("Missing response"))
                    val token = code.optString("access_token")
                    val uid = code.optString("userId")
                    val email = code.optString("inputEmailID")
                    require(token.isNotBlank() && token.length <= 32768 && uid.isNotBlank() && uid.length <= 512)
                    require(code.optLong("access_token_expires_in",0) > 0)
                    SamsungCertificateAuthority.Account(token,uid,email.take(320))
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
