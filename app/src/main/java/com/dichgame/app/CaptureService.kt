package com.dichgame.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Chụp màn hình -> đọc chữ (OCR) -> dịch Anh sang Việt -> hiện ở đầu màn hình. Tất cả chạy trên máy. */
class CaptureService : Service() {
    companion object {
        const val STOP = "stop"
        const val TOGGLE = "toggle"
        const val MENU = "menu"
        const val REGION = "region"
        const val AUDIO_TEXT = "audio_text"
        @Volatile var active = false

        /** Deliver decoded game dialogue to the active subtitle/translation service. */
        fun submitAudioTranscript(context: android.content.Context, text: String) {
            if (!active || text.isBlank()) return
            val intent = Intent(context, CaptureService::class.java)
                .setAction(AUDIO_TEXT)
                .putExtra("audio_text", text)
            runCatching { context.applicationContext.startService(intent) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ocr: Ocr? = null
    private var lastSig: IntArray? = null
    private var lastErrAt = 0L
    private var frameErrAt = 0L
    private var lang = "en"
    private var srcName = "tiếng Anh"
    private var sep = " "
    private var minLen = 3
    private var longLen = 25
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: TextView? = null
    private var tt: TextTranslator? = null
    private var speaker: Speaker? = null
    private var speak = false
    private var ready = false
    private var busy = false
    private val pendingAudioTranscripts = java.util.ArrayDeque<String>()
    private val maxPendingAudioTranscripts = 6
    private var running = false
    private var last = ""
    private var cw = 0
    private var ch = 0
    private var editing = true
    private var narrate = false
    private var needStable = 1
    private var cand = ""
    private var stable = 0
    private var prevVi = ""
    private var menuUntil = 0L
    private val tvExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var tvLast = ""
    private var autoMenu = false
    private var autoShown = false
    private var autoCand = ""
    private var autoStable = 0
    private var autoDone = ""
    private var prevOrig = ""
    private var aiWarned = false
    private var frame: FrameLayout? = null
    private var handle: View? = null
    private var region: FrameLayout? = null
    private lateinit var rlp: WindowManager.LayoutParams
    private lateinit var flp: WindowManager.LayoutParams
    private lateinit var tlp: WindowManager.LayoutParams
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val border by lazy {
        GradientDrawable().apply {
            setStroke((3 * resources.displayMetrics.density).toInt(), 0xFF00E5FF.toInt())
            setColor(0x2200E5FF)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                capture()
            } catch (_: Exception) {
                reportFrameError()
            } finally {
                if (running) handler.postDelayed(this, Prefs(this@CaptureService).scanMs.toLong())
            }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent.action == TOGGLE) { if (running) setEditing(!editing); return START_NOT_STICKY }
        if (intent.action == MENU) { if (running) menuOnce(); return START_NOT_STICKY }
        if (intent.action == REGION) { if (running) toggleRegion(); return START_NOT_STICKY }
        if (intent.action == AUDIO_TEXT) {
            val text = intent.getStringExtra("audio_text").orEmpty()
            if (running) handler.post { acceptAudioTranscript(text) }
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        running = true
        active = true
        narrate = intent.getBooleanExtra("narrate", false)
        speak = intent.getBooleanExtra("speak", false) || narrate
        needStable = if (intent.getBooleanExtra("slow", false)) 2 else 1
        autoMenu = intent.getBooleanExtra("automenu", false) && !narrate
        lang = if (narrate) "en" else intent.getStringExtra("lang") ?: "en"
        srcName = Prefs.srcNameOf(lang)
        val cjk = Prefs.isCjk(lang)
        sep = if (cjk) "" else " "
        minLen = if (cjk) 2 else 3
        longLen = if (cjk) 12 else 25
        val o = Ocr(Prefs(this), lang)
        ocr = o
        // Đọc chữ bằng Gemini thì không tự quét liên tục (tốn lượt dùng); vẫn dùng được "Dịch menu" và "Vùng dịch".
        if (o.isCloud) autoMenu = false
        startAsForeground()

        val data = intent.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(intent.getIntExtra("code", 0), data).also {
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
        }
        addOverlay()
        if (speak) {
            val sp = Speaker(this, Prefs(this))
            sp.onAiError = { m -> android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show() }
            speaker = sp
        }

        val tr = TextTranslator(this, Prefs(this), lang)
        tt = tr
        if (narrate) ready = true else show("Đang tải mô hình dịch (lần đầu, cần mạng)...")
        if (!narrate) tr.prepare { ok ->
            if (ok) { ready = true; if (!editing) show(""); drainPendingAudioTranscript() }
            else show("Không tải được mô hình dịch. Kiểm tra mạng rồi bấm Dừng và chạy lại.")
        }

        ensureSize()
        addFrame()
        handler.postDelayed(tick, Prefs(this).scanMs.toLong())
        if (autoMenu) handler.postDelayed(autoRun, 2500)
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("dg", "Dịch game", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, CaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val togglePi = PendingIntent.getService(
            this, 1, Intent(this, CaptureService::class.java).setAction(TOGGLE), PendingIntent.FLAG_IMMUTABLE
        )
        val menuPi = PendingIntent.getService(
            this, 2, Intent(this, CaptureService::class.java).setAction(MENU), PendingIntent.FLAG_IMMUTABLE
        )
        val regionPi = PendingIntent.getService(
            this, 3, Intent(this, CaptureService::class.java).setAction(REGION), PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, "dg")
            .setContentTitle("Dịch game đang chạy")
            .setContentText("Chạm để dừng")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(0, "Chỉnh/Khóa khung", togglePi)
            .addAction(0, "Dịch menu", menuPi)
            .addAction(0, "Vùng dịch", regionPi)
            .setContentIntent(stopPi).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)
    }

    private fun addOverlay() {
        val tv = TextView(this).apply {
            setTextColor(styleFg())
            setBackgroundColor(styleBg())
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            textSize = Prefs(this@CaptureService).let { it.fontSize * it.subScale / 100f }
            setPadding(32, 16, 32, 16)
            visibility = View.GONE
        }
        tlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        wm.addView(tv, tlp)
        overlay = tv
    }

    private val autoRun = object : Runnable {
        override fun run() {
            try {
                autoScan()
            } catch (_: Exception) {
                reportFrameError()
            } finally {
                if (running && autoMenu) handler.postDelayed(this, 2000)
            }
        }
    }

    /** Lỗi lấy khung hình chỉ bỏ qua lượt quét hiện tại, không làm chết vòng lặp OCR. */
    private fun reportFrameError() {
        finishBusy()
        val now = System.currentTimeMillis()
        if (now - frameErrAt > 8000) {
            frameErrAt = now
            show("Không lấy được khung hình để đọc chữ. Đang tự thử lại…")
        }
    }

    /**
     * Tạo ảnh cắt độc lập rồi giải phóng bitmap toàn màn hình tạm.
     * Các luồng OCR chỉ cần giữ ảnh crop; giữ cả ảnh gốc ở mỗi lần quét
     * sẽ làm tăng áp lực bộ nhớ khi game chạy liên tục.
     */
    private fun cropAndRecycle(full: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return try {
            Bitmap.createBitmap(full, x, y, width, height)
        } finally {
            if (!full.isRecycled) runCatching { full.recycle() }
        }
    }

    private fun rectOf(v: View?): android.graphics.Rect? {
        if (v == null || v.visibility != View.VISIBLE) return null
        val l = IntArray(2)
        v.getLocationOnScreen(l)
        return android.graphics.Rect(l[0], l[1], l[0] + v.width, l[1] + v.height)
    }

    /** Dịch menu tự động: màn hình đứng yên với từ 2 đoạn chữ dài trở lên (ngoài khung phụ đề) thì tự dịch. */
    private fun autoScan() {
        if (busy || !ready || editing || narrate || region != null || System.currentTimeMillis() < menuUntil) return
        ensureSize()
        val img = reader?.acquireLatestImage() ?: return
        val bmp = try {
            val p = img.planes[0]
            val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            try {
                full.copyPixelsFromBuffer(p.buffer)
                cropAndRecycle(full, 0, 0, img.width, img.height)
            } finally {
                // Đảm bảo giải phóng ảnh toàn màn hình nếu copy pixel hoặc crop gặp lỗi.
                if (!full.isRecycled) runCatching { full.recycle() }
            }
        } finally { img.close() }
        busy = true
        val skip = listOfNotNull(rectOf(frame), rectOf(overlay))
        ocr!!.read(bmp, { m -> ocrFail(m) }) { r ->
                val blocks = r.filter { b ->
                    val bb = b.box
                    b.text.length >= longLen && (bb == null || skip.none { it.contains(bb.centerX(), bb.centerY()) })
                }.map { it.text.replace("\n", sep).trim() }.take(4)
                if (blocks.size < 2) {
                    autoCand = ""; autoStable = 0; autoDone = ""
                    if (autoShown) { autoShown = false; show("") }
                    finishBusy(); return@read
                }
                val sig = blocks.joinToString("|")
                if (sig != autoCand) { autoCand = sig; autoStable = 1 } else autoStable++
                if (autoStable < 2 || sig == autoDone) { finishBusy(); return@read }
                autoDone = sig
                val parts = arrayOfNulls<String>(blocks.size)
                var left = blocks.size
                blocks.forEachIndexed { i, b ->
                    translateText(b) { vi ->
                        parts[i] = vi
                        if (--left == 0) {
                            val j = parts.joinToString("\n\n")
                            overlay?.apply { text = j; visibility = View.VISIBLE }
                            sendTv("region", j)
                            autoShown = true
                            finishBusy()
                        }
                    }
                }
            }
    }

    /**
     * ASR supplements OCR. Keep a small FIFO while OCR/translation owns the
     * overlay so short spoken lines are not discarded during a busy frame.
     */
    private fun normalizeTranscript(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun acceptAudioTranscript(raw: String) {
        if (!running || editing || narrate) return
        val text = raw.replace(Regex("\\s+"), " ").trim()
        val key = normalizeTranscript(text)
        if (key.isEmpty() || key == normalizeTranscript(last) || key == normalizeTranscript(cand) ||
            pendingAudioTranscripts.any { normalizeTranscript(it) == key }
        ) return

        if (!ready || busy) {
            if (pendingAudioTranscripts.size >= maxPendingAudioTranscripts) {
                pendingAudioTranscripts.removeFirst()
            }
            pendingAudioTranscripts.addLast(text)
            return
        }
        translateAudioTranscript(text)
    }

    private fun translateAudioTranscript(text: String) {
        if (!running || !ready || editing || narrate || busy) return
        last = text
        cand = text
        stable = needStable
        busy = true
        translateTextEx(text) { vi, delivery ->
            if (running) showSub(text, vi, delivery)
            finishBusy()
        }
    }

    /** Release OCR/translation ownership, then process the next queued ASR line. */
    private fun finishBusy() {
        busy = false
        if (running && ready && !editing && !narrate && pendingAudioTranscripts.isNotEmpty()) {
            handler.post { drainPendingAudioTranscript() }
        }
    }

    private fun drainPendingAudioTranscript() {
        if (!running || !ready || editing || narrate || busy) return
        while (pendingAudioTranscripts.isNotEmpty()) {
            val next = pendingAudioTranscripts.removeFirst()
            val key = normalizeTranscript(next)
            if (key.isNotEmpty() && key != normalizeTranscript(last) && key != normalizeTranscript(cand)) {
                translateAudioTranscript(next)
                return
            }
        }
    }

    /** Lỗi đọc chữ: mở khóa để quét lại, chỉ hiện thông báo lỗi của Gemini (không báo lỗi đọc trên máy), tối đa 8 giây một lần. */
    private fun ocrFail(m: String) {
        finishBusy()
        lastSig = null
        overlay?.let { if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE }
        if (m.isNotEmpty() && System.currentTimeMillis() - lastErrAt > 8000) { lastErrAt = System.currentTimeMillis(); show(m) }
    }

    /** Vùng dịch: khung cam, thả tay là dịch ngay vùng đó (nhiệm vụ, thư, mô tả vật phẩm). Bấm lần nữa để đóng. */
    private fun toggleRegion() {
        if (frame == null) return
        val old = region
        if (old != null) {
            try { wm.removeView(old) } catch (_: Exception) {}
            region = null; menuUntil = 0L; placeTranslation(); show("")
            return
        }
        val d = resources.displayMetrics.density
        rlp = WindowManager.LayoutParams(
            (cw * 0.7).toInt(), (ch * 0.2).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (cw * 0.15).toInt(); y = (ch * 0.3).toInt() }
        val box = FrameLayout(this)
        box.background = GradientDrawable().apply {
            setStroke((3 * d).toInt(), 0xFFFF9800.toInt()); setColor(0x22FF9800)
        }
        val grip = View(this).apply { setBackgroundColor(0xFFFF9800.toInt()) }
        box.addView(grip, FrameLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt(), Gravity.BOTTOM or Gravity.END))
        var lx = 0f; var ly = 0f; var resize = false
        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lx = e.rawX; ly = e.rawY
                    resize = e.x > box.width - 48 * d && e.y > box.height - 48 * d
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - lx).toInt(); val my = (e.rawY - ly).toInt()
                    lx = e.rawX; ly = e.rawY
                    if (resize) {
                        rlp.width = (rlp.width + mx).coerceAtLeast(150).coerceAtMost(cw - rlp.x)
                        rlp.height = (rlp.height + my).coerceAtLeast(100).coerceAtMost(ch - rlp.y)
                    } else {
                        rlp.x = (rlp.x + mx).coerceIn(0, cw - rlp.width)
                        rlp.y = (rlp.y + my).coerceIn(0, ch - rlp.height)
                    }
                    wm.updateViewLayout(box, rlp)
                }
                MotionEvent.ACTION_UP -> { placeTranslation(); translateRegion() }
            }
            true
        }
        wm.addView(box, rlp)
        region = box
        menuUntil = Long.MAX_VALUE
        placeTranslation()
        overlay?.apply {
            text = "Kéo khung cam phủ lên chữ cần dịch rồi thả tay. Bấm \"Vùng dịch\" lần nữa để đóng."
            visibility = View.VISIBLE
        }
    }

    private fun translateRegion() {
        val box = region ?: return
        if (busy || !ready) return
        busy = true
        overlay?.visibility = View.INVISIBLE
        handler.postDelayed({ regionGrab(box) }, 300)
    }

    private fun regionGrab(box: FrameLayout) {
        val img = try {
            ensureSize()
            reader?.acquireLatestImage()
        } catch (_: Exception) {
            reportFrameError()
            finishBusy()
            overlay?.visibility = View.VISIBLE
            return
        }
        if (img == null || region == null) {
            img?.close()
            finishBusy()
            overlay?.visibility = View.VISIBLE
            return
        }
        val crop = try {
            val p = img.planes[0]
            val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            try {
                full.copyPixelsFromBuffer(p.buffer)
                val loc = IntArray(2)
                box.getLocationOnScreen(loc)
                val x = loc[0].coerceIn(0, img.width - 1)
                val y = loc[1].coerceIn(0, img.height - 1)
                cropAndRecycle(full, x, y, box.width.coerceIn(1, img.width - x), box.height.coerceIn(1, img.height - y))
            } finally {
                // Kể cả copy pixel hoặc lấy tọa độ lỗi, không giữ bitmap toàn màn hình.
                if (!full.isRecycled) runCatching { full.recycle() }
            }
        } catch (_: Exception) {
            reportFrameError()
            finishBusy()
            overlay?.visibility = View.VISIBLE
            return
        } finally {
            img.close()
        }
        val reader = ocr
        if (reader == null) {
            if (!crop.isRecycled) runCatching { crop.recycle() }
            finishBusy()
            overlay?.visibility = View.VISIBLE
            return
        }
        reader.read(crop, { m -> ocrFail(m) }) { r ->
            val text = r.joinToString("\n") { it.text }.replace("\n", sep).trim()
            if (text.length < minLen) { show("Không thấy chữ trong khung."); finishBusy(); return@read }
            translateText(text) { vi ->
                show(vi)
                sendTv("region", vi, speak && tvSpeakOn() && !tvVoiceOn())
                if (speak && tvVoiceOn()) tvVoice(vi)
                finishBusy()
            }
        }
    }

    /** Dịch bằng AI nếu đã bật (CloudAPI / Gemini, có nhớ vài câu trước), lỗi thì quay về dịch trên máy. */
    private fun translateText(text: String, done: (String) -> Unit) {
        val t = tt
        if (t == null) done(text) else t.translate(text, done)
    }

    private fun translateTextEx(text: String, done: (String, Delivery?) -> Unit) {
        val t = tt
        if (t == null) done(text, null) else t.translateEx(text, done)
    }

    private fun tvSpeakOn(): Boolean {
        val p = getSharedPreferences("dg", MODE_PRIVATE)
        return p.getBoolean("tvspeak", false) && p.getString("playon", "phone") == "tv" && !p.getString("tvip", "").isNullOrEmpty() && !p.getString("tvtoken", "").isNullOrEmpty()
    }

    /** Giọng offline hoặc giọng AI đang chọn trong khi tiếng được phát ở loa TV: điện thoại tạo âm thanh rồi gửi sang TV. */
    private fun tvVoiceOn(): Boolean {
        val pr = Prefs(this)
        return tvSpeakOn() && ((pr.voiceEngine == "offline" && Voices.installed(this, pr.offlineVoice)) || pr.aiVoiceOn)
    }

    private var tvVoiceWarned = false

    private fun tvVoice(text: String, d: Delivery? = null) {
        val sp = speaker ?: return
        sp.synthForTv(text, d) { pcm, speed ->
            val pf = getSharedPreferences("dg", MODE_PRIVATE)
            val err = if (pcm == null) "giọng đọc lỗi" else TvLink.postAudio(pf.getString("tvip", "").orEmpty(), pf.getString("tvtoken", "").orEmpty(), pcm, speed)
            if (err != null) {
                // lùi về giọng của chính TV để vẫn nghe được
                sendTv("sub", text, true, force = true)
                if (!tvVoiceWarned) {
                    tvVoiceWarned = true
                    handler.post { android.widget.Toast.makeText(this, "Không gửi được tiếng sang TV ($err). TV đọc bằng giọng của TV; nếu TV chưa cập nhật hãy cài bản TV mới.", android.widget.Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    /** Gửi phụ đề lên app Dịch game TV (nếu đã ghép). kind: sub | region | clear. */
    private fun sendTv(kind: String, text: String, speakIt: Boolean = false, force: Boolean = false) {
        val p = getSharedPreferences("dg", MODE_PRIVATE)
        val ip = p.getString("tvip", "").orEmpty()
        val tok = p.getString("tvtoken", "").orEmpty()
        if (p.getString("playon", "phone") != "tv" || ip.isEmpty() || tok.isEmpty()) return
        val key = kind + ":" + text
        if (!force && key == tvLast) return
        tvLast = key
        val size = p.getInt("fs", 18) * p.getInt("subscale", 100) / 100 + 12
        tvExec.execute {
            try {
                val c = URL("http://$ip:8765/sub").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 1500; c.readTimeout = 1500; c.doOutput = true
                c.setRequestProperty("X-Token", tok)
                val body = JSONObject().put("kind", kind).put("text", text).put("size", size).put("speak", speakIt).put("fg", styleFg()).put("bg", styleBg())
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                c.responseCode
                c.disconnect()
            } catch (_: Exception) {}
        }
    }

    /** Hộp dịch 3 tầng: câu trước (nhỏ), câu gốc, bản dịch (to). */
    private fun showSub(orig: String, vi: String, d: Delivery? = null) {
        val sb = SpannableStringBuilder()
        fun add(t: String, size: Float, color: Int) {
            if (t.isEmpty()) return
            val a = sb.length
            sb.append(t).append("\n")
            sb.setSpan(RelativeSizeSpan(size), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(color), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val fg = styleFg()
        val pr = Prefs(this)
        if (pr.showPrev) add(prevVi, 0.7f, (fg and 0x00FFFFFF) or 0x99000000.toInt())
        if (pr.showOrig) add(orig, 0.75f, (fg and 0x00FFFFFF) or 0xCC000000.toInt())
        if (pr.emoTr && pr.emoLabel && d != null && d.label.isNotEmpty()) add("● " + d.label, 0.62f, d.mood.color)
        add(vi, 1.15f, fg)
        if (sb.isNotEmpty()) sb.delete(sb.length - 1, sb.length)
        overlay?.apply { text = sb; visibility = View.VISIBLE }
        sendTv("sub", vi, speak && tvSpeakOn() && !tvVoiceOn())
        if (speak && tvVoiceOn()) tvVoice(vi, d)
        if (speak && ready && !editing && !tvSpeakOn()) speaker?.speak(vi, false, d)
        prevVi = vi; prevOrig = orig; autoShown = false
    }

    /** Dịch menu: một lần, đọc cả màn hình, dịch tối đa 4 đoạn chữ dài. */
    private fun menuOnce() {
        if (busy || !ready || editing || narrate) return
        busy = true
        overlay?.visibility = View.INVISIBLE
        handler.postDelayed({ menuGrab() }, 400)
    }

    private fun menuGrab() {
        val img = try {
            ensureSize()
            reader?.acquireLatestImage()
        } catch (_: Exception) {
            reportFrameError()
            overlay?.visibility = View.VISIBLE
            return
        }
        if (img == null) {
            finishBusy()
            overlay?.visibility = View.VISIBLE
            return
        }
        val bmp = try {
            val p = img.planes[0]
            val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            try {
                full.copyPixelsFromBuffer(p.buffer)
                cropAndRecycle(full, 0, 0, img.width, img.height)
            } finally {
                if (!full.isRecycled) runCatching { full.recycle() }
            }
        } catch (_: Exception) {
            reportFrameError()
            overlay?.visibility = View.VISIBLE
            return
        } finally {
            img.close()
        }
        val reader = ocr
        if (reader == null) {
            if (!bmp.isRecycled) runCatching { bmp.recycle() }
            finishBusy()
            overlay?.visibility = View.VISIBLE
            return
        }
        reader.read(bmp, { m -> ocrFail(m) }) { r ->
            val blocks = r.map { it.text.replace("\n", sep).trim() }.filter { it.length >= longLen }.take(4)
            if (blocks.isEmpty()) {
                menuUntil = System.currentTimeMillis() + 3000
                show("Không thấy đoạn chữ dài nào để dịch.")
                handler.postDelayed({ if (!editing) show("") }, 3000)
                finishBusy()
                return@read
            }
            val parts = arrayOfNulls<String>(blocks.size)
            var left = blocks.size
            blocks.forEachIndexed { i, b ->
                translateText(b) { vi ->
                    parts[i] = vi
                    if (--left == 0) {
                        menuUntil = System.currentTimeMillis() + 15000
                        val translated = parts.joinToString("\n\n")
                        show(translated)
                        sendTv("region", translated)
                        finishBusy()
                        handler.postDelayed({
                            if (!editing && System.currentTimeMillis() >= menuUntil) show("")
                        }, 15000)
                    }
                }
            }
        }
    }

    /** Mỗi hướng màn hình (dọc/ngang) nhớ vị trí khung riêng. */
    private fun pk(k: String): String {
        val g = Prefs(this).profile.trim().lowercase()
        return "g" + g.hashCode() + (if (cw > ch) "l" else "p") + k
    }

    private fun styleFg(): Int = when (getSharedPreferences("dg", MODE_PRIVATE).getInt("style", 0)) {
        1 -> 0xFFFFEB3B.toInt(); 2 -> Color.BLACK; else -> Color.WHITE
    }

    private fun styleBg(): Int = when (getSharedPreferences("dg", MODE_PRIVATE).getInt("style", 0)) {
        2 -> 0xE6FFFFFF.toInt(); 3 -> 0; else -> 0xB3000000.toInt()
    }

    /** Màn hình xoay (game thường chạy ngang): nạp lại khung theo hướng mới và đóng khung cam. */
    private fun relayoutFrame() {
        val f = frame ?: return
        val p = getSharedPreferences("dg", MODE_PRIVATE)
        val w = p.getInt(pk("fw"), (cw * 0.8).toInt()).coerceIn(150, cw)
        val h = p.getInt(pk("fh"), (ch * 0.28).toInt()).coerceIn(100, ch)
        flp.width = w; flp.height = h
        flp.x = p.getInt(pk("fx"), (cw * 0.1).toInt()).coerceIn(0, cw - w)
        flp.y = p.getInt(pk("fy"), (ch * 0.68).toInt()).coerceIn(0, ch - h)
        wm.updateViewLayout(f, flp)
        if (region != null) toggleRegion()
        placeTranslation()
    }

    /** Khung chọn vùng chữ: kéo để di chuyển, kéo ô vuông góc dưới phải để đổi cỡ. */
    private fun addFrame() {
        val d = resources.displayMetrics.density
        val p = getSharedPreferences("dg", MODE_PRIVATE)
        val w = p.getInt(pk("fw"), (cw * 0.8).toInt()).coerceIn(150, cw)
        val h = p.getInt(pk("fh"), (ch * 0.28).toInt()).coerceIn(100, ch)
        flp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = p.getInt(pk("fx"), (cw * 0.1).toInt()).coerceIn(0, cw - w)
            y = p.getInt(pk("fy"), (ch * 0.68).toInt()).coerceIn(0, ch - h)
        }
        val box = FrameLayout(this)
        val grip = View(this).apply { setBackgroundColor(0xFF00E5FF.toInt()) }
        box.addView(grip, FrameLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt(), Gravity.BOTTOM or Gravity.END))
        var lx = 0f; var ly = 0f; var resize = false
        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lx = e.rawX; ly = e.rawY
                    resize = e.x > box.width - 48 * d && e.y > box.height - 48 * d
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - lx).toInt(); val my = (e.rawY - ly).toInt()
                    lx = e.rawX; ly = e.rawY
                    if (resize) {
                        flp.width = (flp.width + mx).coerceAtLeast(150).coerceAtMost(cw - flp.x)
                        flp.height = (flp.height + my).coerceAtLeast(100).coerceAtMost(ch - flp.y)
                    } else {
                        flp.x = (flp.x + mx).coerceIn(0, cw - flp.width)
                        flp.y = (flp.y + my).coerceIn(0, ch - flp.height)
                    }
                    wm.updateViewLayout(box, flp)
                    placeTranslation()
                }
                MotionEvent.ACTION_UP -> getSharedPreferences("dg", MODE_PRIVATE).edit()
                    .putInt(pk("fx"), flp.x).putInt(pk("fy"), flp.y).putInt(pk("fw"), flp.width).putInt(pk("fh"), flp.height).apply()
            }
            true
        }
        wm.addView(box, flp)
        frame = box; handle = grip
        placeTranslation()
        setEditing(true)
    }

    private fun setEditing(on: Boolean) {
        editing = on
        val f = frame ?: return
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        flp.flags = if (on) base else base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        f.background = if (on) border else null
        handle?.visibility = if (on) View.VISIBLE else View.INVISIBLE
        wm.updateViewLayout(f, flp)
        last = ""
        show(if (on) "Kéo khung xanh tới dòng phụ đề, kéo ô vuông góc dưới phải để đổi cỡ. Xong thì kéo thông báo xuống, bấm \"Chỉnh/Khóa khung\"." else "")
        if (!on) drainPendingAudioTranscript()
    }

    /** Bản dịch hiện ở phía đối diện khung để không bị đọc lại chính nó. */
    private fun placeTranslation() {
        val o = overlay ?: return
        val l = if (region != null) rlp else flp
        val g = if (l.y + l.height / 2 < ch / 2) Gravity.BOTTOM else Gravity.TOP
        if (tlp.gravity != g) { tlp.gravity = g; wm.updateViewLayout(o, tlp) }
    }

    private fun show(t: String) {
        if (t.isEmpty()) sendTv("clear", "")
        overlay?.apply { text = t; visibility = if (t.isEmpty()) View.GONE else View.VISIBLE }
        if (t.isNotEmpty() && speak && ready && !editing && !tvSpeakOn()) speaker?.speak(t)
    }

    /** Theo kích thước thật của màn hình (xoay ngang/dọc), tạo lại bộ đệm ảnh khi đổi. */
    @Suppress("DEPRECATION")
    private fun ensureSize() {
        val dm = DisplayMetrics()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(0).getRealMetrics(dm)
        if (dm.widthPixels == cw && dm.heightPixels == ch) return
        cw = dm.widthPixels; ch = dm.heightPixels
        val newReader = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2)
        if (display == null) {
            display = projection!!.createVirtualDisplay(
                "dg", cw, ch, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                newReader.surface, null, handler
            )
        } else {
            display!!.resize(cw, ch, dm.densityDpi)
            display!!.surface = newReader.surface
        }
        reader?.close()
        reader = newReader
        relayoutFrame()
    }

    private fun capture() {
        if (busy || !ready || editing || System.currentTimeMillis() < menuUntil) return
        ensureSize()
        val img = reader?.acquireLatestImage() ?: return
        val crop = try {
            val p = img.planes[0]
            val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            try {
                full.copyPixelsFromBuffer(p.buffer)
                val loc = IntArray(2)
                frame!!.getLocationOnScreen(loc)
                val x = loc[0].coerceIn(0, img.width - 1)
                val y = loc[1].coerceIn(0, img.height - 1)
                val w = frame!!.width.coerceIn(1, img.width - x)
                val h = frame!!.height.coerceIn(1, img.height - y)
                cropAndRecycle(full, x, y, w, h)
            } finally {
                // cropAndRecycle đã giải phóng ảnh gốc khi thành công; nhánh lỗi
                // (copy pixel / lấy tọa độ / tạo crop) cũng phải giải phóng ảnh.
                if (!full.isRecycled) runCatching { full.recycle() }
            }
        } finally { img.close() }

        val o = ocr
        if (o == null) {
            if (!crop.isRecycled) runCatching { crop.recycle() }
            return
        }
        if (o.isCloud) {
            // Gemini: chỉ gửi khi hình trong khung thật sự đổi, để khỏi tốn lượt dùng.
            val sig = Ocr.thumb(crop)
            if (Ocr.same(sig, lastSig)) {
                // Không gửi ảnh trùng lên Gemini thì cũng phải giải phóng crop,
                // vì nhánh này không chuyển quyền sở hữu cho Ocr.read().
                if (!crop.isRecycled) runCatching { crop.recycle() }
                return
            }
            lastSig = sig
        }
        busy = true
        o.read(crop, { m -> ocrFail(m) }) { r ->
                val text = r.joinToString("\n") { it.text }.replace("\n", sep).trim()
                if (text.isEmpty()) { last = ""; cand = ""; stable = 0; if (!autoShown) show(""); finishBusy(); return@read }
                if (text == last) { finishBusy(); return@read }
                if (text != cand) { cand = text; stable = 1 } else stable++
                // Câu rất ngắn như "No", "Go", "OK" dễ bị bộ lọc độ dài bỏ qua.
                // Cho phép 1–2 ký tự nhưng yêu cầu lặp lại qua nhiều khung hình để giảm OCR rác.
                val requiredStable = when (text.length) {
                    1 -> maxOf(needStable, 3)
                    2 -> maxOf(needStable, 2)
                    else -> needStable
                }
                if (stable < requiredStable) { finishBusy(); return@read }
                last = text
                if (narrate) { showSub("", text); finishBusy(); return@read }
                translateTextEx(text) { vi, d -> showSub(text, vi, d); finishBusy() }
            }
    }

    override fun onDestroy() {
        running = false
        pendingAudioTranscripts.clear()
        active = false
        handler.removeCallbacksAndMessages(null)
        try { overlay?.let { wm.removeView(it) }; frame?.let { wm.removeView(it) }; region?.let { wm.removeView(it) } } catch (_: Exception) {}
        display?.release(); reader?.close()
        projection?.stop()
        tt?.close(); ocr?.close()
        speaker?.shutdown(); tvExec.shutdown()
        super.onDestroy()
    }
}