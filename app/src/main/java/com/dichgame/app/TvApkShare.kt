package com.dichgame.app

import android.content.Context
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Semaphore

/** Explicit, temporary LAN-only sharing of the bundled Android TV APK. */
internal object TvApkShare {
    private const val ASSET = "tv/DichGame-TV.apk"
    private const val PORT = 8781
    private var active: ServerSocket? = null
    private val peers = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private val slots = Semaphore(4)
    @Synchronized fun stop() {
        runCatching { active?.close() }
        active = null
        peers.forEach { runCatching { it.close() } }
        peers.clear()
    }

    @Synchronized fun start(context: Context): List<String> {
        val app = context.applicationContext
        val ips = PsFinder.localIps(app).filter { LgDeveloperKey.isPrivateIpv4(it) }
        check(ips.isNotEmpty()) { "Điện thoại chưa có IP Wi-Fi nội bộ" }
        val apk = app.assets.open(ASSET).use { it.readBytes() }
        check(apk.size in 1024..(32 * 1024 * 1024)) { "Bộ cài TV không hợp lệ" }
        stop()
        val token = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        val server = ServerSocket(PORT, 8)
        active = server
        val expires = android.os.SystemClock.elapsedRealtime() + 600000
        Thread({
            try {
                server.soTimeout = 1000
                while (!server.isClosed && android.os.SystemClock.elapsedRealtime() < expires) {
                    val peer = try { server.accept() } catch (_: java.net.SocketTimeoutException) { continue } catch (_: Exception) { break }
                    if (!peer.inetAddress.isSiteLocalAddress || !slots.tryAcquire()) { peer.close(); continue }
                    peers.add(peer)
                    Thread({
                        try { serve(peer, token, apk, expires, server) }
                        finally { peers.remove(peer); runCatching { peer.close() }; slots.release() }
                    }, "dg-tv-apk-peer").apply { isDaemon = true; start() }
                }
            } finally { runCatching { server.close() } }
        }, "dg-tv-apk").apply { isDaemon = true; start() }
        return ips.map { "http://$it:$PORT/$token/DichGame-TV.apk" }
    }

    private fun line(input: java.io.InputStream): String? {
        val buffer = java.io.ByteArrayOutputStream()
        repeat(4096) {
            val b = input.read()
            if (b < 0) return null
            if (b == 10) return buffer.toString("US-ASCII").trimEnd('\r')
            buffer.write(b)
        }
        return null
    }

    private fun serve(peer: Socket, token: String, apk: ByteArray, expires: Long, server: ServerSocket) {
        try {
            peer.soTimeout = 5000
            val input = peer.getInputStream().buffered()
            val first = line(input)?.split(' ') ?: return
            if (first.size != 3) return
            var ended = false
            repeat(32) {
                if (!ended) { val h = line(input) ?: return; if (h.isEmpty()) ended = true }
            }
            if (!ended) return
            val output = peer.getOutputStream()
            val valid = !server.isClosed && android.os.SystemClock.elapsedRealtime() < expires &&
                first[0] in listOf("GET", "HEAD") && first[1] == "/$token/DichGame-TV.apk"
            if (!valid) {
                output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\n" +
                "Content-Disposition: attachment; filename=DichGame-TV.apk\r\nContent-Length: ${apk.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
            if (first[0] == "GET") {
                var pos = 0
                while (pos < apk.size && !server.isClosed && android.os.SystemClock.elapsedRealtime() < expires) {
                    val n = minOf(32768, apk.size - pos)
                    output.write(apk, pos, n); pos += n
                }
            }
            output.flush()
        } catch (_: Exception) {}
    }
}
