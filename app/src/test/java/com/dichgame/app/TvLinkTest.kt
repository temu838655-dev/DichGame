package com.dichgame.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TV LG giả trên máy: bắt tay WebSocket thật, trả lời ghép nối và thông báo như mô tả giao thức SSAP. */
private class FakeLg(
    private val toastError: String? = null,
    private val closeAfterRegister: Boolean = false,
) {
    val ss = ServerSocket(0)
    val port: Int get() = ss.localPort
    val toasts = CopyOnWriteArrayList<String>()
    @Volatile var register: JSONObject? = null

    init {
        Thread {
            try {
                val c = ss.accept()
                val ins = c.getInputStream()
                val out = c.getOutputStream()
                val sb = StringBuilder()
                while (!sb.endsWith("\r\n\r\n")) {
                    val x = ins.read()
                    if (x < 0) return@Thread
                    sb.append(x.toChar())
                }
                val key = sb.lines().first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: ${MiniWs.accept(key)}\r\n\r\n").toByteArray())
                out.flush()
                while (true) {
                    val msg = readClient(ins) ?: break
                    val j = JSONObject(msg)
                    when (j.getString("type")) {
                        "register" -> {
                            register = j
                            if (closeAfterRegister) { c.close(); return@Thread }
                            writeServer(out, """{"type":"response","id":"register_0","payload":{"pairingType":"PROMPT","returnValue":true}}""")
                            Thread.sleep(150)
                            writeServer(out, """{"type":"registered","id":"register_0","payload":{"client-key":"KEY123"}}""")
                        }
                        "request" -> {
                            val id = j.getString("id")
                            if (toastError != null) {
                                writeServer(out, """{"type":"error","id":"$id","error":"$toastError","payload":{}}""")
                            } else {
                                toasts.add(j.getJSONObject("payload").getString("message"))
                                writeServer(out, """{"type":"response","id":"$id","payload":{"returnValue":true}}""")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }
    }

    fun close() = try { ss.close() } catch (_: Exception) {}
}

private fun readClient(ins: InputStream): String? {
    fun rd(n: Int): ByteArray {
        val b = ByteArray(n)
        var o = 0
        while (o < n) { val r = ins.read(b, o, n - o); if (r < 0) throw java.io.EOFException(); o += r }
        return b
    }
    val h = rd(2)
    if ((h[0].toInt() and 0x0F) == 0x8) return null
    var len = (h[1].toInt() and 0x7F).toLong()
    if (len == 126L) { val e = rd(2); len = ((e[0].toInt() and 255) shl 8 or (e[1].toInt() and 255)).toLong() }
    else if (len == 127L) { val e = rd(8); len = 0; for (x in e) len = (len shl 8) or (x.toLong() and 255) }
    val mask = rd(4)
    val d = rd(len.toInt())
    for (i in d.indices) d[i] = (d[i].toInt() xor mask[i % 4].toInt()).toByte()
    return String(d, Charsets.UTF_8)
}

private fun writeServer(out: OutputStream, text: String) {
    val p = text.toByteArray(Charsets.UTF_8)
    val h = ByteArrayOutputStream()
    h.write(0x81)
    when {
        p.size < 126 -> h.write(p.size)
        p.size < 65536 -> { h.write(126); h.write(p.size ushr 8); h.write(p.size and 255) }
        else -> { h.write(127); for (i in 7 downTo 0) h.write(((p.size.toLong() ushr (8 * i)) and 255L).toInt()) }
    }
    out.write(h.toByteArray())
    out.write(p)
    out.flush()
}

class TvLinkTest {
    @Test fun pairsAndSendsToast() {
        val tv = FakeLg()
        try {
            val lg = LgTv("127.0.0.1", "", tlsPorts = emptyList(), plainPorts = listOf(tv.port))
            var prompted = false
            assertNull(lg.connect(5000) { prompted = true })
            assertTrue("TV phải báo hộp hỏi cho phép", prompted)
            assertEquals("KEY123", lg.key)
            assertTrue(lg.connected)
            val perms = tv.register!!.getJSONObject("payload").getJSONObject("manifest").getJSONArray("permissions").toString()
            assertTrue(perms.contains("WRITE_NOTIFICATION_TOAST"))
            assertEquals("PROMPT", tv.register!!.getJSONObject("payload").getString("pairingType"))
            assertNull(lg.toast("Xin chào, đây là phụ đề"))
            assertEquals(listOf("Xin chào, đây là phụ đề"), tv.toasts.toList())
            lg.close()
            assertFalse(lg.connected)
        } finally { tv.close() }
    }

    @Test fun reusesClientKeyInRegister() {
        val tv = FakeLg()
        try {
            val lg = LgTv("127.0.0.1", "OLDKEY", tlsPorts = emptyList(), plainPorts = listOf(tv.port))
            assertNull(lg.connect(5000))
            assertEquals("OLDKEY", tv.register!!.getJSONObject("payload").getString("client-key"))
            lg.close()
        } finally { tv.close() }
    }

    @Test fun toastErrorIsReportedVerbatim() {
        val tv = FakeLg(toastError = "401 insufficient permissions")
        try {
            val lg = LgTv("127.0.0.1", "", tlsPorts = emptyList(), plainPorts = listOf(tv.port))
            assertNull(lg.connect(5000))
            val e = lg.toast("hi")
            assertNotNull(e)
            assertTrue(e!!, e.contains("401 insufficient permissions"))
            lg.close()
        } finally { tv.close() }
    }

    @Test fun refusedPortGivesReadableError() {
        val lg = LgTv("127.0.0.1", "", tlsPorts = emptyList(), plainPorts = listOf(1))
        val e = lg.connect(1000)
        assertNotNull(e)
        assertTrue(e!!, e.contains("cổng 1"))
        assertNotNull(lg.toast("x"))
    }

    @Test fun tvClosingDuringPairingIsReported() {
        val tv = FakeLg(closeAfterRegister = true)
        try {
            val lg = LgTv("127.0.0.1", "", tlsPorts = emptyList(), plainPorts = listOf(tv.port))
            val e = lg.connect(3000)
            assertNotNull(e)
        } finally { tv.close() }
    }

    @Test fun acceptKeyMatchesRfcExample() {
        // ví dụ trong RFC 6455 mục 1.3
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", MiniWs.accept("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test fun largeMessageRoundTripsBothLengthForms() {
        // máy chủ phản hồi lại đúng tin nhận được: kiểm khung dài 16 bit và 64 bit cả hai chiều
        val ss = ServerSocket(0)
        Thread {
            try {
                val c = ss.accept()
                val ins = c.getInputStream()
                val out = c.getOutputStream()
                val sb = StringBuilder()
                while (!sb.endsWith("\r\n\r\n")) sb.append(ins.read().toChar())
                val key = sb.lines().first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${MiniWs.accept(key)}\r\n\r\n").toByteArray())
                out.flush()
                while (true) { val m = readClient(ins) ?: break; writeServer(out, m) }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
        try {
            val ws = MiniWs.connect("127.0.0.1", ss.localPort, false, 3000)
            ws.setTimeout(5000)
            for (n in listOf(5, 125, 126, 1000, 65535, 70000)) {
                val s = "é".repeat(n / 2 + 1).take(n) + "ư".repeat(3)
                ws.send(s)
                assertEquals(s, ws.receive())
            }
            ws.close()
        } finally { ss.close() }
    }

    @Test fun lgSsdpReplyDetection() {
        val ok = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nST: urn:lge-com:service:webos-second-screen:1\r\nSERVER: WebOS/4.1.0 UPnP/1.0\r\n\r\n"
        assertTrue(LgDiscover.isLg(ok))
        assertFalse(LgDiscover.isLg("HTTP/1.1 200 OK\r\nSERVER: Linux UPnP/1.0 SomeRouter\r\n\r\n"))
        assertFalse(LgDiscover.isLg("NOTIFY * HTTP/1.1\r\nserver: webos\r\n\r\n"))
        val q = String(LgDiscover.packet())
        assertTrue(q.startsWith("M-SEARCH * HTTP/1.1"))
        assertTrue(q.contains("ssdp:discover"))
        assertTrue(q.endsWith("\r\n\r\n"))
    }

    private fun get(port: Int, path: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 6000
        val code = c.responseCode
        val body = (if (code == 200) c.inputStream else c.errorStream).bufferedReader().readText()
        c.disconnect()
        return code to body
    }

    @Test fun webSubServesPageAndLongPollsNewText() {
        val s = WebSubServer(0)
        s.legacyEnabled = true
        val port = s.start()
        try {
            val (c1, page) = get(port, "/")
            assertEquals(200, c1)
            assertTrue(page.contains("/poll"))
            assertEquals(404, get(port, "/khong-co").first)

            s.push("Xin chào")
            val (c2, b2) = get(port, "/poll?since=-1")
            assertEquals(200, c2)
            val j2 = JSONObject(b2)
            assertEquals("Xin chào", j2.getString("text"))
            val seq = j2.getLong("seq")

            // chưa có câu mới thì chờ; đẩy câu mới sau 200 ms thì trả về ngay câu đó
            Thread { Thread.sleep(200); s.push("Câu hai") }.start()
            val t0 = System.currentTimeMillis()
            val j3 = JSONObject(get(port, "/poll?since=$seq").second)
            assertEquals("Câu hai", j3.getString("text"))
            assertTrue(j3.getLong("seq") > seq)
            assertTrue("phải trả về nhanh, không chờ hết 20 giây", System.currentTimeMillis() - t0 < 5000)

            // cùng nội dung thì không tăng số thứ tự
            val before = j3.getLong("seq")
            s.push("Câu hai")
            assertEquals(before, JSONObject(get(port, "/poll?since=-1").second).getLong("seq"))
        } finally { s.stop() }
        assertFalse(s.running)
    }

    @Test fun receiverRequiresApprovalAndRevocation() {
        val s = WebSubServer(0)
        val port = s.start()
        try {
            assertEquals(403, get(port, "/").first)
            assertEquals(403, get(port, "/poll?since=-1").first)
            assertEquals(403, get(port, "/receiver/poll?since=-1").first)
            val register = JSONObject(get(port, "/receiver/register").second)
            val id = register.getString("id")
            val code = register.getString("code")
            assertEquals(8, code.length)
            assertEquals("", JSONObject(get(port, "/receiver/wait?id=$id").second).getString("token"))
            assertTrue(s.approveReceiver(code))
            val token = JSONObject(get(port, "/receiver/wait?id=$id").second).getString("token")
            assertEquals(64, token.length)
            s.push("Xin chào")
            val (status, frame) = get(port, "/receiver/poll?since=-1&token=$token")
            assertEquals(200, status)
            assertEquals("Xin chào", JSONObject(frame).getString("text"))
            s.revokeReceivers()
            assertEquals(403, get(port, "/receiver/poll?since=-1&token=$token").first)
        } finally { s.stop() }
    }

    @Test fun staleSubtitleAndMenuExpireSeparately() {
        var now = 1000L
        val s = WebSubServer(0) { now }
        s.legacyEnabled = true
        val port = s.start()
        try {
            s.push("Lời thoại")
            s.push("Menu", "region")
            now += 12001
            var frame = JSONObject(get(port, "/poll?since=-1").second)
            assertEquals("", frame.getString("text"))
            assertEquals("Menu", frame.getString("region"))
            now += 3000
            frame = JSONObject(get(port, "/poll?since=-1").second)
            assertEquals("", frame.getString("region"))
        } finally { s.stop() }
    }
}
