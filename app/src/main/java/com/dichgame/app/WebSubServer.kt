package com.dichgame.app

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import org.json.JSONObject

/**
 * Trang web phụ đề phát từ điện thoại: mở địa chỉ http://IP-điện-thoại:cổng bằng trình duyệt của bất kỳ TV, máy tính bảng,
 * máy tính hay iPhone trong cùng Wi-Fi là thấy phụ đề dịch hiện chữ lớn, cập nhật theo thời gian thực (long-poll, chạy được cả
 * trình duyệt TV đời cũ). Chỉ chạy khi người dùng bật, chỉ trong mạng nội bộ, không lưu và không gửi nội dung đi đâu.
 */
class WebSubServer(private val port: Int = 8780) {
    private var server: ServerSocket? = null
    private val lock = Object()
    private var seq = 0L
    private var text = ""
    // Đối với app Tizen/webOS: mã ghép và token chỉ sống trong phiên web server này.
    // Trang phụ đề trình duyệt cũ vẫn dùng /poll theo yêu cầu người dùng.
    val tvPairCode: String = String.format(java.util.Locale.ROOT, "%08d", SecureRandom().nextInt(100_000_000))
    private val tvTokens = java.util.Collections.synchronizedSet(HashSet<String>())
    private val attempts = HashMap<String, Pair<Long, Int>>()
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
                Thread({ handle(c) }, "dg-websub-conn").apply { isDaemon = true; start() }
            }
        }, "dg-websub").apply { isDaemon = true; start() }
        return s.localPort
    }

    fun stop() {
        stopped = true
        try { server?.close() } catch (_: Exception) {}
        server = null
        synchronized(lock) { lock.notifyAll(); tvTokens.clear(); attempts.clear() }
    }

    /** Đặt phụ đề hiện tại (chuỗi rỗng = xóa chữ). */
    fun push(t: String) {
        synchronized(lock) {
            if (t == text) return
            text = t
            seq++
            lock.notifyAll()
        }
    }

    private fun handle(c: Socket) {
        try {
            c.soTimeout = 30_000
            val rd = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.ISO_8859_1))
            val line = rd.readLine() ?: return
            while (true) { val h = rd.readLine() ?: break; if (h.isEmpty()) break }
            val parts = line.split(' ')
            val target = parts.getOrElse(1) { "/" }
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
            fun arg(name: String): String? = query.split('&').firstOrNull {
                it.substringBefore('=') == name
            }?.substringAfter('=', "")?.let {
                try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { null }
            }
            when (path) {
                "/tv-pair" -> {
                    val ip = c.inetAddress?.hostAddress ?: "unknown"
                    val accepted = synchronized(lock) {
                        val now = System.currentTimeMillis()
                        val old = attempts[ip]
                        val n = if (old == null || now - old.first > 60_000L) 1 else old.second + 1
                        // Tối đa 8 lần thử/60 giây/mỗi địa chỉ.
                        if (attempts.size >= 128) attempts.entries.removeAll { now - it.value.first > 60_000L }
                        if (attempts.size < 128 || attempts.containsKey(ip)) attempts[ip] = (if (n == 1) now else old!!.first) to n
                        n <= 8 && attempts.containsKey(ip) && arg("code") == tvPairCode
                    }
                    if (!accepted) {
                        reply(c, 403, "application/json; charset=utf-8", """{"error":"Sai mã hoặc ghép quá nhiều lần"}""")
                    } else {
                        val token = ByteArray(24).also { SecureRandom().nextBytes(it) }
                            .joinToString("") { "%02x".format(it) }
                        tvTokens.add(token)
                        reply(c, 200, "application/json; charset=utf-8", JSONObject().put("token", token).toString())
                    }
                }
                "/tv-poll" -> {
                    if (!tvTokens.contains(arg("token").orEmpty())) {
                        reply(c, 403, "application/json; charset=utf-8", """{"error":"Cần ghép lại TV"}""")
                    } else {
                        pollReply(c, arg("since")?.toLongOrNull() ?: -1L)
                    }
                }
                "/poll" -> pollReply(c, arg("since")?.toLongOrNull() ?: -1L)
                "/", "/index.html" -> reply(c, 200, "text/html; charset=utf-8", PAGE)
                else -> reply(c, 404, "text/plain; charset=utf-8", "404")
            }
        } catch (_: Exception) {
        } finally {
            try { c.close() } catch (_: Exception) {}
        }
    }

    private fun pollReply(c: Socket, since: Long) {
        val body: String
        synchronized(lock) {
            val end = System.currentTimeMillis() + 20_000
            while (seq <= since && !stopped) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                lock.wait(left)
            }
            body = JSONObject().put("seq", seq).put("text", text).toString()
        }
        reply(c, 200, "application/json; charset=utf-8", body)
    }

    private fun reply(c: Socket, code: Int, type: String, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else if (code == 403) "Forbidden" else "Not Found"}\r\nContent-Type: $type\r\nContent-Length: ${b.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
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
    try{var j=JSON.parse(x.responseText);seq=j.seq;sub.textContent=j.text;hint.style.display=j.text?'none':'block';}catch(e){}
    setTimeout(poll,50);
  };
  x.onerror=x.ontimeout=function(){hint.style.display='block';hint.textContent='Mất kết nối, đang thử lại...';setTimeout(poll,1500);};
  x.send();
}
poll();
</script></body></html>"""
    }
}
