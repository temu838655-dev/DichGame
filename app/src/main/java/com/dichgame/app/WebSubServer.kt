package com.dichgame.app

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import org.json.JSONObject

/**
 * Trang web phụ đề phát từ điện thoại: mở địa chỉ http://IP-điện-thoại:cổng bằng trình duyệt của bất kỳ TV, máy tính bảng,
 * máy tính hay iPhone trong cùng Wi-Fi là thấy phụ đề dịch hiện chữ lớn, cập nhật theo thời gian thực (long-poll, chạy được cả
 * trình duyệt TV đời cũ). Chỉ chạy khi người dùng bật, chỉ trong mạng nội bộ, không lưu và không gửi nội dung đi đâu.
 */
class WebSubServer(private val port: Int = 8780, private val clock: () -> Long = { System.nanoTime() / 1000000L }) {
    internal var pairing = WebReceiverPairing(now = clock)
    @Volatile var legacyEnabled = false
    fun approveReceiver(code: String): Boolean = pairing.approve(code)
    fun revokeReceivers() { pairing.revokeAll(); synchronized(lock) { seq++; lock.notifyAll() } }
    private var server: ServerSocket? = null
    private val lock = Object()
    private var seq = 0L
    private var text = ""
    private var region = ""
    private var style = WebTvStyle()
    private var speechSeq = 0L
    private var speech = ""
    private var textUntil = 0L
    private var regionUntil = 0L
    private val clients = java.util.concurrent.Semaphore(16)
    @Volatile private var stopped = false

    val running: Boolean get() = server?.isClosed == false

    /** Mở cổng, trả về cổng thật (khi truyền 0 thì hệ điều hành chọn). Ném lỗi nếu cổng bị chiếm. */
    fun start(): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        stopped = false
        val s = ServerSocket(port, 20, null)
        server = s
        Thread({
            while (!stopped) {
                val c = try { s.accept() } catch (_: SocketException) { break } catch (_: Exception) { continue }
                if (!c.inetAddress.isSiteLocalAddress && !c.inetAddress.isLoopbackAddress) { c.close(); continue }
                if (!clients.tryAcquire()) { c.close(); continue }
                Thread({ try { handle(c) } finally { clients.release() } }, "dg-websub-conn").apply { isDaemon = true; start() }
            }
        }, "dg-websub").apply { isDaemon = true; start() }
        return s.localPort
    }

    fun stop() {
        stopped = true
        try { server?.close() } catch (_: Exception) {}
        server = null
        synchronized(lock) { lock.notifyAll() }
    }

    /** Đặt phụ đề hiện tại (chuỗi rỗng = xóa chữ). */
    fun push(t: String, kind: String = "sub", speak: Boolean = false) {
        synchronized(lock) {
            val oldText = text
            val oldRegion = region
            val now = clock()
            when (kind) {
                "region" -> { region = t; regionUntil = now + 15000 }
                "clear" -> { text = ""; region = ""; textUntil = 0; regionUntil = 0 }
                else -> { text = t; textUntil = now + 12000 }
            }
            val voiced = speak && t.isNotBlank() && style.speak
            if (voiced && (speech != t || oldText != text || oldRegion != region)) {
                speech = t
                speechSeq++
            } else if (!voiced && oldText == text && oldRegion == region) return
            seq++
            lock.notifyAll()
        }
    }

    internal fun setStyle(next: WebTvStyle) {
        synchronized(lock) {
            if (style == next) return
            style = next
            seq++
            lock.notifyAll()
        }
    }

    fun clearTestSubtitle(expected: String) {
        synchronized(lock) { if (text == expected) push("") }
    }

    private fun expireLocked() {
        val now = clock()
        var changed = false
        if (text.isNotEmpty() && now >= textUntil) { text = ""; changed = true }
        if (region.isNotEmpty() && now >= regionUntil) { region = ""; changed = true }
        if (changed) seq++
    }

    private fun boundedLine(rd: BufferedReader): String? {
        val b = StringBuilder()
        while (b.length < 4096) {
            val ch = rd.read()
            if (ch < 0) return null
            if (ch == 10) return b.toString().trimEnd(13.toChar())
            b.append(ch.toChar())
        }
        return null
    }

    private fun handle(c: Socket) {
        try {
            c.soTimeout = 30_000
            val rd = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.ISO_8859_1))
            val line = boundedLine(rd) ?: return
            var headers = 0
            while (true) { val h = boundedLine(rd) ?: return; if (h.isEmpty()) break; if (++headers > 64) return }
            val parts = line.split(' ')
            if (parts.firstOrNull() != "GET") { reply(c, 404, "text/plain; charset=utf-8", "404"); return }
            val target = parts.getOrElse(1) { "/" }
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
            fun param(name: String): String = query.split('&').firstOrNull { it.startsWith("$name=") }?.substringAfter('=').orEmpty()
            when (path) {
                "/receiver/register" -> {
                    val request = pairing.register()
                    if (request == null) reply(c, 429, "text/plain", "Busy", true)
                    else reply(c, 200, "application/json", JSONObject().put("id", request.id).put("code", request.code).toString(), true)
                }
                "/receiver/wait" -> {
                    val request = pairing.request(param("id"))
                    if (request == null) reply(c, 404, "text/plain", "Expired", true)
                    else reply(c, 200, "application/json", JSONObject().put("token", request.token ?: "").toString(), true)
                }
                "/receiver/poll", "/poll" -> {
                    val receiver = path == "/receiver/poll"
                    if ((receiver && !pairing.valid(param("token"))) || (!receiver && !legacyEnabled)) {
                        reply(c, 403, "text/plain", "Pair receiver first", receiver)
                        return
                    }
                    val since = query.split('&').firstOrNull { it.startsWith("since=") }?.substringAfter('=')?.let {
                        try { URLDecoder.decode(it, "UTF-8").toLongOrNull() } catch (_: Exception) { null }
                    } ?: -1L
                    val body: String
                    synchronized(lock) {
                        val end = System.nanoTime() / 1000000L + 20_000
                        expireLocked()
                        while (seq == since && !stopped && (if (receiver) pairing.valid(param("token")) else legacyEnabled)) {
                            val left = end - System.nanoTime() / 1000000L
                            if (left <= 0) break
                            lock.wait(minOf(left, 500L))
                            expireLocked()
                        }
                        body = JSONObject().put("seq", seq).put("text", text).put("region", region)
                            .put("style", style.json()).put("speechSeq", speechSeq).put("speech", speech).toString()
                    }
                    if (stopped || (receiver && !pairing.valid(param("token"))) || (!receiver && !legacyEnabled)) reply(c, 403, "text/plain", "Pair receiver first", receiver)
                    else reply(c, 200, "application/json; charset=utf-8", body, cors = receiver)
                }
                "/", "/index.html" -> if (legacyEnabled) reply(c, 200, "text/html; charset=utf-8", PAGE) else reply(c, 403, "text/plain", "Enable browser mode on phone")
                else -> reply(c, 404, "text/plain; charset=utf-8", "404")
            }
        } catch (_: Exception) {
        } finally {
            try { c.close() } catch (_: Exception) {}
        }
    }

    private fun reply(c: Socket, code: Int, type: String, body: String, cors: Boolean = false) {
        val b = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${when (code) { 200 -> "OK"; 403 -> "Forbidden"; 429 -> "Too Many Requests"; else -> "Not Found" }}\r\nContent-Type: $type\r\nContent-Length: ${b.size}\r\n" +
            "Cache-Control: no-store\r\n" + (if (cors) "Access-Control-Allow-Origin: *\r\n" else "") + "Connection: close\r\n\r\n"
        val o = c.getOutputStream()
        o.write(head.toByteArray(Charsets.ISO_8859_1))
        o.write(b)
        o.flush()
    }

    companion object {
        /** Trang cho trình duyệt: ES5 + XMLHttpRequest để chạy được trên trình duyệt TV đời cũ. */
        const val PAGE = """<!doctype html>
<html lang="vi"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Dịch game - Phụ đề</title>
<style>
html,body{margin:0;height:100%;background:#000;color:#fff;font-family:Arial,Helvetica,sans-serif}
#wrap{position:absolute;left:0;right:0;bottom:0;top:0;display:table;width:100%}
#cell{display:table-cell;vertical-align:bottom;text-align:center;padding:0 4% 6%}
#sub{display:inline-block;max-width:92%;font-size:5.2vw;line-height:1.25;font-weight:bold;background:rgba(0,0,0,.72);padding:.25em .6em;border-radius:.3em;text-shadow:0 2px 4px #000}
#hint{position:fixed;top:2%;left:0;right:0;text-align:center;color:#8a8f98;font-size:2.4vw}
#sub:empty{display:none}
</style></head><body>
<div id="hint">Dịch game: phụ đề sẽ hiện ở đây khi bạn bấm bắt đầu dịch</div>
<div id="wrap"><div id="cell"><div id="sub"></div></div></div>
<script>
var seq=-1,sub=document.getElementById('sub'),hint=document.getElementById('hint');
function poll(){
  var x=new XMLHttpRequest();
  x.open('GET','/poll?since='+seq+'&t='+(new Date().getTime()),true);
  x.timeout=30000;
  x.onload=function(){
    try{if(x.status!==200)throw Error();var j=JSON.parse(x.responseText);seq=j.seq;sub.textContent=j.text;hint.style.display=j.text?'none':'block';}catch(e){seq=-1;sub.textContent='';hint.style.display='block';hint.textContent='Bật lại chế độ trình duyệt trên điện thoại.';}
    setTimeout(poll,50);
  };
  x.onerror=x.ontimeout=function(){seq=-1;sub.textContent='';hint.style.display='block';hint.textContent='Mất kết nối, đang thử lại...';setTimeout(poll,750);};
  x.send();
}
poll();
</script></body></html>"""
    }
}
