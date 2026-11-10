package com.dichgame.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Locale

/** Trạng thái ghép nối dùng chung giữa màn hình ghép (TvActivity) và dịch vụ nhận phụ đề (TvService). */
object TvState {
    @Volatile var code = "--------"
    @Volatile var prev = ""
    @Volatile var fails = 0
    val tokens: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())
    fun rotate() { prev = code; code = String.format("%08d", SecureRandom().nextInt(100_000_000)); fails = 0 }
}

/** Nhận phụ đề từ điện thoại qua Wi-Fi (cổng 8765) rồi vẽ nổi lên hình TV, kể cả khi TV đang ở cổng HDMI. */
class TvService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var server: ServerSocket? = null
    private var disc: java.net.DatagramSocket? = null
    private var tts: TextToSpeech? = null
    private var subView: TextView? = null
    private var regView: TextView? = null
    private val hideSub = Runnable { subView?.visibility = View.GONE }
    private val hideReg = Runnable { regView?.visibility = View.GONE }
    private val rotate = object : Runnable {
        override fun run() { TvState.rotate(); handler.postDelayed(this, 60000) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("tv", "Dịch game TV", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "tv")
            .setContentTitle("Dịch game TV đang chờ phụ đề")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, 1 shl 30) else startForeground(1, n)
        getSharedPreferences("tv", MODE_PRIVATE).getStringSet("tokens", emptySet())?.let { TvState.tokens.addAll(it) }
        handler.post(rotate)
        tts = TextToSpeech(this) { s -> if (s == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("vi", "VN")) }
        Thread { listen() }.start()
        Thread { discover() }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "stop" -> stopSelf()
            "test" -> show("sub", "Đây là câu thử phụ đề trên TV.", 30, false)
        }
        return START_STICKY
    }

    private fun listen() {
        try {
            val ss = ServerSocket(8765)
            server = ss
            while (!ss.isClosed) {
                val s = ss.accept()
                Thread {
                    try { handle(s) } catch (_: Exception) {} finally { try { s.close() } catch (_: Exception) {} }
                }.start()
            }
        } catch (_: Exception) {}
    }

    /** Trả lời gói quảng bá "DICHGAME?" của điện thoại để nó tự tìm ra địa chỉ TV. */
    private fun discover() {
        try {
            val ds = java.net.DatagramSocket(8766)
            disc = ds
            val buf = ByteArray(64)
            while (!ds.isClosed) {
                val p = java.net.DatagramPacket(buf, buf.size)
                ds.receive(p)
                if (String(p.data, 0, p.length) == "DICHGAME?") {
                    val r = "DICHGAME:8765".toByteArray()
                    ds.send(java.net.DatagramPacket(r, r.size, p.address, p.port))
                }
            }
        } catch (_: Exception) {}
    }

    private fun line(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 4096) return null
        }
    }

    private fun save() {
        val copy = synchronized(TvState.tokens) { HashSet(TvState.tokens) }
        getSharedPreferences("tv", MODE_PRIVATE).edit().putStringSet("tokens", copy).apply()
    }

    private fun handle(s: Socket) {
        s.soTimeout = 3000
        val i = s.getInputStream().buffered()
        val req = line(i) ?: return
        var len = 0
        var token = ""
        while (true) {
            val h = line(i) ?: return
            if (h.isEmpty()) break
            val k = h.substringBefore(':').trim().lowercase()
            val v = h.substringAfter(':', "").trim()
            if (k == "content-length") len = v.toIntOrNull() ?: 0
            if (k == "x-token") token = v
        }
        val buf = ByteArray(len.coerceIn(0, 8192))
        var off = 0
        while (off < buf.size) {
            val r = i.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        val body = String(buf, 0, off, Charsets.UTF_8)
        val path = req.split(" ").getOrElse(1) { "" }
        var status = 404
        var out = ""
        if (path == "/pair") {
            if (TvState.fails >= 10) status = 429
            else if (body.length == 8 && (body == TvState.code || body == TvState.prev)) {
                val t = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
                TvState.tokens.add(t); save()
                status = 200; out = t
            } else { TvState.fails++; status = 403 }
        } else if (path == "/sub") {
            if (token.isEmpty() || !TvState.tokens.contains(token)) status = 403
            else {
                val j = JSONObject(body)
                val kind = j.optString("kind", "sub")
                val text = j.optString("text", "")
                val size = j.optInt("size", 30).coerceIn(14, 80)
                val sp = j.optBoolean("speak", false)
                val fg = j.optInt("fg", Color.WHITE)
                val bg = j.optInt("bg", 0xB3000000.toInt())
                handler.post { show(kind, text, size, sp, fg, bg) }
                status = 200; out = "ok"
            }
        }
        val bytes = out.toByteArray()
        s.getOutputStream().apply {
            write("HTTP/1.1 $status OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun mk(top: Boolean): TextView {
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            setPadding(80, 16, 80, 16)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = if (top) Gravity.TOP else Gravity.BOTTOM; y = if (top) 40 else 60 }
        wm.addView(tv, lp)
        return tv
    }

    private fun put(v: TextView, t: String, size: Int, hide: Runnable, ms: Long, fg: Int, bg: Int) {
        handler.removeCallbacks(hide)
        if (t.isEmpty()) { v.visibility = View.GONE; return }
        v.textSize = size.toFloat()
        v.setTextColor(fg)
        v.setBackgroundColor(bg)
        v.text = t
        v.visibility = View.VISIBLE
        handler.postDelayed(hide, ms)
    }

    private fun show(kind: String, text: String, size: Int, speakIt: Boolean, fg: Int = Color.WHITE, bg: Int = 0xB3000000.toInt()) {
        if (!Settings.canDrawOverlays(this)) return
        if (subView == null) { subView = mk(false); regView = mk(true) }
        when (kind) {
            "region" -> put(regView!!, text, size, hideReg, 15000, fg, bg)
            "clear" -> { put(subView!!, "", size, hideSub, 0, fg, bg); put(regView!!, "", size, hideReg, 0, fg, bg) }
            else -> put(subView!!, text, size, hideSub, 12000, fg, bg)
        }
        if (speakIt && text.isNotEmpty()) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tv")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { server?.close() } catch (_: Exception) {}
        try { disc?.close() } catch (_: Exception) {}
        try { subView?.let { wm.removeView(it) }; regView?.let { wm.removeView(it) } } catch (_: Exception) {}
        tts?.shutdown()
        super.onDestroy()
    }
}
