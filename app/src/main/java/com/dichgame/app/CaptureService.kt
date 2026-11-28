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
import android.graphics.Typeface
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
import android.text.style.StyleSpan
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
        fun submitAudioTranscript(context: android.content.Context, text: String, speechMs: Long = 0L, rms: Float = 1f) {
            if (!active || text.isBlank()) return
            val intent = Intent(context, CaptureService::class.java)
                .setAction(AUDIO_TEXT)
                .putExtra("audio_text", text)
                .putExtra("speech_ms", speechMs)
                .putExtra("rms", rms)
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
    private val captureBuffer = CaptureBuffer<ImageReader>()
    private var overlay: TextView? = null
    private var menuOverlay: UiOverlay? = null
    private var menuGeneration = 0
    private var renderingMenu = false
    private var menuScene: MenuSceneGuard? = null
    private var lastMenuSceneCheck = 0L
    private var tt: TextTranslator? = null
    private var speaker: Speaker? = null
    private var speak = false
    private var ready = false
    private var busy = false
    /** Một câu cần dịch: lời của một người nói (hoặc câu không rõ người nói). */
    private class Line(val speaker: String?, val text: String, val nameLike: Boolean, val src: Src)
    /** Một việc dịch: một hoặc vài câu hiện cùng lúc (nhiều người nói trên cùng một khung hình). */
    private class Job(val lines: List<Line>, val src: Src, val speechMs: Long, val replace: Boolean, val seenAt: Long)
    private class Card(val speaker: String?, val orig: String, val tr: TextTranslator.Tr, val unsure: Boolean)
    private class Payload(val cards: List<Card>, val seenAt: Long)
    /** Các câu đã qua bộ hợp nhất, chờ dịch (mỗi lúc chỉ dịch một việc để giữ đúng thứ tự). */
    private val jobs = java.util.ArrayDeque<Job>()
    private val maxJobs = 6
    private var translating = false
    private var translatingSince = 0L
    private val translationEpoch = TranslationCallbackEpoch()
    private val fusion = SubtitleFusion()
    private val pacer = SubtitlePacer(mergePayload = { a, b ->
        val x = a as? Payload
        val y = b as? Payload
        if (x != null && y != null) Payload(x.cards + y.cards, minOf(x.seenAt, y.seenAt)) else y ?: x
    })
    private var audioQueue: GameAudioPcmQueue? = null
    private var audioWorker: GameAudioAsrWorker? = null
    private var phoneAudio: PhoneAudioCapture? = null
    private var speakers = SpeakerBook()
    private var activeGameContext = ""
    private var langTrack: LangSwitchTracker? = null
    private var pendingLang: String? = null
    private val retired = ArrayList<TextTranslator>()
    private val rate = SubtitlePacer.SpeechRate()
    private val stats = SessionStats.live
    private var lastFine: IntArray? = null
    private var skipRun = 0
    /** Trong khung phụ đề còn chữ (lần quét gần nhất có câu thoại): chưa ẩn phụ đề dù đã hết thời gian đọc. */
    private var textOnScreen = false
    /** Lúc lần đầu không thấy câu thoại nào (0 khi đang có chữ): chỉ ẩn phụ đề khi chữ đã biến mất đủ lâu, không ẩn vì một lần đọc hụt. */
    private var textGoneSince = 0L
    private var running = false
    private var last = ""
    private val cw get() = captureBuffer.width
    private val ch get() = captureBuffer.height
    private var editing = true
    private var narrate = false
    private val autoGameDetector = AutoGameDetector()
    private var needStable = 1
    private var cand = ""
    private var stable = 0
    private val ocrStability = OcrStabilityFilter()
    private var prevVi = ""
    private var menuUntil = 0L
    private val tvExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var tvLast = ""
    private var autoMenu = false
    private var autoShown = false
    private var autoCand = ""
    private var autoStable = 0
    private var autoDone = ""
    private var prevOrig = ""
    private var aiWarned = false
    private var frame: FrameLayout? = null
    private var handle: View? = null
    private var doneBtn: View? = null
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

    private fun gameContext(p: Prefs): String = listOf(p.profile, PackStore.gameName(p), p.game).joinToString("\u0000")

    /** Kho game vừa chuyển trò chơi: loại bỏ phụ đề/nhân vật của hồ sơ cũ
     * mà không phải cấp lại quyền MediaProjection. */
    private fun syncGameContext(): Boolean {
        if (!running) return false
        val p = Prefs(this)
        val context = gameContext(p)
        if (context == activeGameContext) return false
        activeGameContext = context
        speakers = SpeakerBook(PackStore.cast(p).map { it.name } + p.detectedSpeakers())
        translationEpoch.invalidate()
        translating = false
        jobs.clear()
        fusion.clear()
        pacer.cleared()
        RecentLines.clear()
        last = ""; cand = ""; stable = 0; ocrStability.reset(); prevVi = ""; prevOrig = ""
        lastFine = null; lastSig = null
        textOnScreen = false
        menuUntil = 0L; autoShown = false; autoCand = ""; autoDone = ""; autoStable = 0
        speaker?.stop()
        show("")
        return true
    }

    private val cloudRefresh by lazy { CloudRefresh(this) }

    private val tick = object : Runnable {
        override fun run() {
            try {
                syncGameContext()
                housekeeping()
                capture()
            } catch (_: Throwable) {
                reportFrameError()
            } finally {
                if (running) handler.postDelayed(this, scanDelay())
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
            val ms = intent.getLongExtra("speech_ms", 0L)
            val rms = intent.getFloatExtra("rms", 1f)
            if (running) handler.post { acceptAudioTranscript(text, ms, rms) }
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        CrashLog.mark("capture:bat-dau")
        // Android 14 trở lên bắt buộc gọi startForeground ngay khi dịch vụ khởi động, trước cả việc nạp bộ đọc chữ.
        val micReq = intent.getBooleanExtra("phoneaudio", false) && !intent.getBooleanExtra("narrate", false)
        val micFgs = try {
            startAsForeground(micReq)
        } catch (e: Throwable) {
            return failStart("Không bật được dịch vụ dịch màn hình: ${e.message ?: e.javaClass.simpleName}")
        }
        return try {
            startUp(intent, micFgs)
        } catch (e: Throwable) {
            failStart("Không bật được dịch màn hình: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Bắt đầu thất bại: ghi lại, báo cho người dùng và dừng gọn thay vì để app đóng đột ngột. */
    private fun failStart(msg: String): Int {
        CrashLog.mark("capture:loi $msg")
        try { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
        stopSelf()
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startUp(intent: Intent, micFgs: Boolean): Int {
        running = true
        cloudRefresh.start()
        active = true
        stats.reset(System.currentTimeMillis(), batteryPct())
        RecentLines.clear()
        narrate = intent.getBooleanExtra("narrate", false)
        speak = intent.getBooleanExtra("speak", false) || narrate || Prefs(this).listenOnly
        needStable = if (intent.getBooleanExtra("slow", false)) 2 else 1
        // chỉ khi bắt đầu từ màn hình chính (game chạy trên điện thoại); chơi PS trong app thì đã có âm thanh từ Remote Play
        val wantPhoneAudio = intent.getBooleanExtra("phoneaudio", false) && !narrate
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
        activeGameContext = gameContext(Prefs(this))
        speakers = SpeakerBook((PackStore.cast(Prefs(this)).map { it.name } + Prefs(this).detectedSpeakers()))
        // dựng sẵn bộ nhớ câu Việt hoá (có thể hàng chục nghìn câu nhập từ file) ở nền để câu đầu tiên không bị khựng
        val warmPrefs = Prefs(this)
        Thread { try { PackStore.memory(applicationContext, warmPrefs).warm() } catch (_: Throwable) {} }.start()
        langTrack = LangSwitchTracker(lang)
        if (narrate) ready = true else show("Đang tải mô hình dịch (lần đầu, cần mạng)...")
        if (!narrate) tr.prepareForPlayback { ok ->
            if (!running || tt !== tr) return@prepareForPlayback
            if (ok) { ready = true; if (!editing) show(""); pump() }
            else show("Không tải được mô hình dịch. Kiểm tra mạng rồi bấm Dừng và chạy lại.")
        }

        ensureSize()
        addFrame()
        if (wantPhoneAudio) startPhoneAudio(micFgs)
        handler.postDelayed(tick, Prefs(this).scanMs.toLong())
        if (autoMenu) handler.postDelayed(autoRun, 2500)
        return START_NOT_STICKY
    }

    /** Trả true nếu dịch vụ nền được phép dùng micro (cần khi game đang chạy phía trước và app ở nền). */
    private fun startAsForeground(wantMic: Boolean): Boolean {
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
        val fixPi = PendingIntent.getActivity(
            this, 4, Intent(this, CorrectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, "dg")
            .setContentTitle("Dịch game đang chạy")
            .setContentText("Chạm để dừng")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(0, "Chỉnh/Khóa khung", togglePi)
            .addAction(0, "Dịch menu", menuPi)
            .addAction(0, "Vùng dịch", regionPi)
            .addAction(0, "Sửa bản dịch", fixPi)
            .setContentIntent(stopPi).build()
        val micGranted = wantMic && Build.VERSION.SDK_INT >= 30 &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 29) {
            val projectionType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (micGranted) {
                try {
                    startForeground(1, n, projectionType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    return true
                } catch (_: SecurityException) {
                    // hệ điều hành không cho dùng micro trong dịch vụ nền: vẫn dịch chữ, nghe âm thanh qua đường khác nếu được
                }
            }
            startForeground(1, n, projectionType)
        } else startForeground(1, n)
        return false
    }

    /** Nghe âm thanh game trên điện thoại: thu PCM -> VAD + Whisper offline -> bộ hợp nhất. Lỗi gì cũng chỉ báo và vẫn dịch bằng chữ. */
    private fun startPhoneAudio(micFgs: Boolean) {
        val dir = java.io.File(filesDir, "whisper-tiny")
        if (!WhisperTinyModelInstaller(dir).isInstalled()) {
            android.widget.Toast.makeText(this, "Chưa tải mô hình nhận dạng giọng nói (Cài đặt › Nhận dạng lời thoại bằng âm thanh). Đang dịch chỉ bằng chữ trên màn hình.", android.widget.Toast.LENGTH_LONG).show()
            stats.inc("nguon_khong")
            return
        }
        try {
            val q = GameAudioPcmQueue()
            audioQueue = q
            val w = GameAudioAsrWorker(
                queue = q,
                modelDirectory = dir,
                language = lang,
                onTranscript = { t -> handler.post { acceptAudioTranscript(t, 0L, 1f) } },
                onError = { m -> handler.post { android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show() } },
                onTranscriptMeta = { t, ms, rms -> handler.post { acceptAudioTranscript(t, ms, rms) } },
            )
            audioWorker = w
            w.start()
            // micro chỉ dùng được ngầm khi dịch vụ nền khai báo loại microphone; nếu không thì coi như không cho dùng micro
            val allowMic = Prefs(this).phoneAudioMic && micFgs
            val cap = PhoneAudioCapture(this, projection, q, allowMic) { mode -> handler.post { announceAudioMode(mode) } }
            phoneAudio = cap
            cap.start()
        } catch (e: Throwable) {
            android.widget.Toast.makeText(this, "Không bật được nghe âm thanh game: ${e.message ?: e.javaClass.simpleName}. Vẫn dịch bằng chữ trên màn hình.", android.widget.Toast.LENGTH_LONG).show()
            stats.inc("nguon_khong")
        }
    }

    /** Nói rõ cho người dùng biết đang nghe âm thanh bằng nguồn nào (hoặc vì sao không nghe được). */
    private fun announceAudioMode(mode: String) {
        if (!running) return
        val msg = when (mode) {
            "system" -> { stats.inc("nguon_he_thong"); "Đang nghe âm thanh trong của game để bổ sung phụ đề. Game chặn thu âm thanh thì chỉ còn chữ trên màn hình." }
            "mic" -> { stats.inc("nguon_mic"); "Đang nghe qua micro (máy không thu được âm thanh trong): chất lượng kém hơn và có thể thu cả giọng bạn. Đeo tai nghe sẽ không nghe được game." }
            "mic-fallback" -> { stats.inc("nguon_mic"); "Game chặn thu âm thanh trong, đang nghe qua micro: chất lượng kém hơn và có thể thu cả giọng bạn." }
            "none:permission" -> { stats.inc("nguon_khong"); "Chưa cấp quyền ghi âm nên chưa nghe được âm thanh game. Vẫn dịch bằng chữ trên màn hình." }
            "none:system" -> { stats.inc("nguon_khong"); "Máy hoặc game không cho thu âm thanh trong và micro đang tắt (Cài đặt › Nhận dạng lời thoại). Vẫn dịch bằng chữ trên màn hình." }
            else -> { stats.inc("nguon_khong"); "Không mở được micro. Vẫn dịch bằng chữ trên màn hình." }
        }
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
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

    private fun clearMenuOverlay() {
        if (renderingMenu || menuScene != null || menuOverlay?.visibility == View.VISIBLE) menuUntil = 0L
        menuGeneration++
        menuScene = null
        if (renderingMenu) { renderingMenu = false; finishBusy() }
        menuOverlay?.clear()
        menuOverlay?.visibility = View.GONE
    }

    private fun ensureMenuOverlay(): UiOverlay {
        menuOverlay?.let { return it }
        val v = UiOverlay(this).apply { visibility = View.GONE }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            // Android 12+ prevents touches through a highly opaque application-overlay window.
            alpha = 0.78f
        }
        wm.addView(v, lp)
        menuOverlay = v
        return v
    }

    private data class MenuItem(val source: String, val rect: android.graphics.Rect, val colors: Pair<Int, Int>,
                                val corners: FloatArray?, val lines: Int)

    private fun scanScope() = listOf(gameContext(Prefs(this)), cw.toString(), ch.toString(), lang).joinToString("\u0000")

    private fun validScan(scope: String): Boolean = running && !editing && scope == scanScope()

    private fun menuItems(blocks: List<Blk>, image: Bitmap?, skip: List<android.graphics.Rect>): List<MenuItem> {
        if (image == null) return emptyList()
        return blocks.mapNotNull { b ->
            val box = b.box ?: return@mapNotNull null
            if (box.width() < 12 || box.height() < 8 || skip.any { it.contains(box.centerX(), box.centerY()) }) return@mapNotNull null
            val src = UiText.joinLines(b.text, Prefs.isCjk(lang))
            if (UiText.verdict(src, b.conf) != UiText.Verdict.TRANSLATE) return@mapNotNull null
            val colors = NativeTextStyle.sample(image, b) ?: return@mapNotNull null
            MenuItem(src, android.graphics.Rect(box), colors, b.corners, b.lineCount)
        }.take(80)
    }

    private fun renderMenuItems(items: List<MenuItem>, scene: IntArray) {
        if (!running || items.isEmpty()) { finishBusy(); return }
        val t = tt ?: run { finishBusy(); return }
        val generation = ++menuGeneration
        renderingMenu = true
        menuUntil = Long.MAX_VALUE
        val context = gameContext(Prefs(this))
        val screenW = cw; val screenH = ch
        menuScene = MenuSceneGuard.create(scene, screenW, screenH, items.map { intArrayOf(it.rect.left,it.rect.top,it.rect.right,it.rect.bottom) })
        lastMenuSceneCheck = android.os.SystemClock.uptimeMillis()
        val v = try { ensureMenuOverlay() } catch (_: Exception) {
            renderingMenu = false; menuScene = null; menuUntil = 0L; finishBusy(); show("Không tạo được lớp phủ chữ trên màn hình.", false); return
        }
        overlay?.visibility = View.GONE
        pacer.cleared()
        handler.removeCallbacks(hideCheck)
        speaker?.stop()
        v.visibility = View.VISIBLE
        val translated = arrayOfNulls<String>(items.size)
        var next = 0
        var pending = 0
        var finished = false
        fun current() = running && generation == menuGeneration && context == gameContext(Prefs(this)) && cw == screenW && ch == screenH
        fun draw() {
            if (!current()) return
            val loc = IntArray(2); v.getLocationOnScreen(loc)
            val draws = items.mapIndexedNotNull { i, item ->
                val text = translated[i] ?: return@mapIndexedNotNull null
                val b = item.rect
                val r = android.graphics.RectF((b.left-loc[0]).toFloat(),(b.top-loc[1]).toFloat(),
                    (b.right-loc[0]).toFloat(),(b.bottom-loc[1]).toFloat())
                val q = item.corners?.mapIndexed { j, x -> x - loc[j%2] }?.toFloatArray()
                UiOverlay.Draw(r,text,item.colors.first,item.colors.second,false,q,item.lines)
            }
            v.set(draws)
        }
        fun complete() {
            if (finished || generation != menuGeneration) return
            finished = true
            renderingMenu = false
            menuUntil = System.currentTimeMillis() + 12000
            finishBusy()
            val texts = translated.filterNotNull()
            if (texts.isEmpty()) {
                v.clear(); v.visibility = View.GONE
                show("Chưa dịch được chữ rõ ràng trên màn hình.", false)
            } else sendTv("region", texts.joinToString("\n"))
            handler.postDelayed({
                if (generation == menuGeneration) {
                    clearMenuOverlay(); menuUntil = 0L; autoShown = false; autoDone = ""; show("")
                }
            }, 12000)
        }
        fun pump() {
            if (finished || !current()) return
            while (pending < 3 && next < items.size) {
                val i = next++
                val item = items[i]
                val protected = UiText.protect(item.source)
                pending++
                t.translateFull(protected.text, null, true) { tr ->
                    if (finished || !current()) return@translateFull
                    pending--
                    val restored = UiText.restore(tr.text, protected)
                    if (restored != null && UiText.safeTranslation(item.source, restored, tr.uncertain, lang)) {
                        translated[i] = restored
                        draw()
                    }
                    if (next == items.size && pending == 0) complete() else pump()
                }
            }
        }
        // Prevent a lost callback or slow network from locking screen capture indefinitely.
        handler.postDelayed({ if (!finished && generation == menuGeneration) complete() }, 35000)
        v.post { if (current()) draw() }
        pump()
    }

    /** Watch the unmasked game pixels while translated text is visible. Never OCR the overlay itself. */
    private fun checkMenuScene() {
        val watch = menuScene ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastMenuSceneCheck < 1000L) return
        lastMenuSceneCheck = now
        val img = captureBuffer.reader?.acquireLatestImage() ?: return
        val thumb = try {
            val p = img.planes[0]
            val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            try {
                full.copyPixelsFromBuffer(p.buffer)
                val cropped = cropAndRecycle(full, 0, 0, img.width, img.height)
                try { Ocr.thumbFine(cropped) } finally { cropped.recycle() }
            } finally { if (!full.isRecycled) full.recycle() }
        } finally { img.close() }
        if (watch.changed(thumb)) {
            clearMenuOverlay()
            menuUntil = 0L
            autoCand = ""; autoDone = ""; autoStable = 0
            show("")
        }
    }

    private val autoRun = object : Runnable {
        override fun run() {
            try {
                autoScan()
            } catch (_: Throwable) {
                reportFrameError()
            } finally {
                if (running && autoMenu) handler.postDelayed(this, 1000)
            }
        }
    }

    /** Lỗi lấy khung hình chỉ bỏ qua lượt quét hiện tại, không làm chết vòng lặp OCR. */
    private fun reportFrameError() {
        finishBusy()
        val now = System.currentTimeMillis()
        if (now - frameErrAt > 8000) {
            frameErrAt = now
            show("Không lấy được khung hình để đọc chữ. Đang tự thử lại…", readAloud = false)
        }
    }

    /**
     * Tạo ảnh cắt độc lập rồi giải phóng bitmap toàn màn hình tạm.
     * Các luồng OCR chỉ cần giữ ảnh crop; giữ cả ảnh gốc ở mỗi lần quét
     * sẽ làm tăng áp lực bộ nhớ khi game chạy liên tục.
     */
    private fun cropAndRecycle(full: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        // createBitmap may return the original object for an unchanged full-screen crop.
        // Always return a separate bitmap before releasing the caller's original.
        return try {
            val cropped = Bitmap.createBitmap(full, x, y, width, height)
            if (cropped === full) checkNotNull(full.copy(Bitmap.Config.ARGB_8888, false)) else cropped
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
        val ocrNow = ocr ?: return
        ensureSize()
        val img = captureBuffer.reader?.acquireLatestImage() ?: return
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
        val scene = Ocr.thumbFine(bmp)
        val keep = runCatching { bmp.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        val scope = scanScope()
        ocrNow.read(bmp, { m -> keep?.recycle(); if (validScan(scope)) ocrFail(m) else finishBusy() }) { r ->
            if (!validScan(scope)) { keep?.recycle(); finishBusy(); return@read }
            val items = try { menuItems(r, keep, skip) } catch (_: Exception) { emptyList() } finally { keep?.recycle() }
            val long = items.filter { it.source.length >= longLen }
            if (long.size < 2) {
                autoCand = ""; autoStable = 0; autoDone = ""
                finishBusy(); return@read
            }
            val sig = items.joinToString("|") { it.source }
            if (sig != autoCand) { autoCand = sig; autoStable = 1 } else autoStable++
            if (autoStable < 2 || sig == autoDone) { finishBusy(); return@read }
            autoDone = sig
            renderMenuItems(items, scene)
        }
    }

    // ---------- lời nghe được từ âm thanh game ----------

    /** Câu nhận dạng từ âm thanh game: lọc ảo giác, rồi hợp nhất với chữ trên màn hình (không hiện hai lần). */
    private fun acceptAudioTranscript(raw: String, speechMs: Long, rms: Float) {
        if (!running || editing || narrate || syncGameContext()) return
        val now = System.currentTimeMillis()
        stats.inc("asr_nhan")
        val v = AsrGuard.check(raw, rms, lang)
        // câu ngắn hay bịa ("yeah", "thank you") mà có âm lượng thì vẫn không đủ căn cứ nếu màn hình không có chữ tương ứng: bỏ
        if (!v.ok || v.ambiguous) { stats.inc("asr_loc"); return }
        rate.add(v.text, speechMs)
        val fresh = ArrayList<Line>()
        applyOuts(fusion.onAsr(v.text, now), false, fresh, speechMs, now)
        enqueue(fresh, speechMs, now, false)
    }

    /** Chuyển quyết định của bộ hợp nhất thành các câu cần dịch. REPLACE (bản đầy đủ hơn của câu đã hiện) đi riêng, SHOW gom lại. */
    private fun applyOuts(outs: List<SubtitleFusion.Out>, nameLike: Boolean, fresh: MutableList<Line>, speechMs: Long, seenAt: Long) {
        for (o in outs) {
            when (o.kind) {
                SubtitleFusion.Kind.DROP -> if (o.src == Src.ASR) stats.inc("asr_trung")
                SubtitleFusion.Kind.HOLD -> {}
                SubtitleFusion.Kind.SHOW -> fresh.add(Line(o.speaker, o.text, nameLike, o.src))
                SubtitleFusion.Kind.REPLACE -> {
                    if (o.src == Src.MIXED) stats.inc("ghep")
                    enqueue(listOf(Line(o.speaker, o.text, nameLike, o.src)), speechMs, seenAt, true)
                }
            }
        }
    }

    private fun enqueue(lines: List<Line>, speechMs: Long, seenAt: Long, replace: Boolean) {
        if (lines.isEmpty()) return
        var rep = replace
        if (replace) {
            // bản cũ của câu này còn đang chờ dịch thì thay ngay trong hàng chờ, khỏi hiện cả hai
            val removed = jobs.removeAll { j -> j.lines.any { a -> lines.any { b -> fusion.relation(a.text, b.text) != SubtitleFusion.Rel.NONE } } }
            if (removed) rep = false
        }
        // nhân vật đã nói trong ASR thì biết thời gian nói thật; câu chỉ có chữ thì ước lượng theo tốc độ nói đã nghe được
        var ms = speechMs
        if (ms == 0L && rate.cps > 0f) ms = (lines.sumOf { l -> l.text.count { c -> !c.isWhitespace() } } * 1000f / rate.cps).toLong()
        while (jobs.size >= maxJobs) { jobs.removeFirst(); stats.inc("hang_day") }
        jobs.addLast(Job(lines, lines[0].src, ms, rep, seenAt))
        pump()
    }

    /** Mỗi lúc chỉ dịch một việc (giữ đúng thứ tự lời thoại), xong thì đưa cho bộ giữ nhịp quyết định khi nào hiện. */
    private fun pump() {
        if (!running || !ready || editing || narrate) return
        val now = System.currentTimeMillis()
        if (now < menuUntil) return
        pacer.poll(now)?.let { render(it) }
        if (translating) {
            if (now - translatingSince < 30_000L) return
            // Hủy quyền callback của request cũ trước khi chạy job tiếp theo.
            // Nếu AI trả lời muộn, kết quả cũ không được phép ghi đè phụ đề mới.
            translating = false
            translationEpoch.invalidate()
            stats.inc("dich_treo")
        }
        val w = jobs.pollFirst() ?: return
        translating = true
        translatingSince = now
        val token = translationEpoch.begin()
        translateLines(w) { cards ->
            if (!translationEpoch.isCurrent(token) || !running) return@translateLines
            translating = false
            deliver(w, cards)
            handler.post { pump() }
        }
    }

    private fun translateLines(w: Job, done: (List<Card>) -> Unit) {
        val t = tt
        val out = ArrayList<Card>()
        fun next(i: Int) {
            if (i >= w.lines.size) { done(out); return }
            val line = w.lines[i]
            if (t == null) {
                out.add(Card(line.speaker, line.text, TextTranslator.Tr(line.text, null, true, "may"), true))
                next(i + 1)
            } else {
                t.translateFull(line.text, line.speaker) { tr ->
                    // một cái tên đứng riêng được giữ nguyên là bình thường, không đánh dấu "chưa chắc"
                    out.add(Card(line.speaker, line.text, tr, tr.uncertain && !line.nameLike))
                    next(i + 1)
                }
            }
        }
        next(0)
    }

    private fun deliver(w: Job, cards: List<Card>) {
        if (cards.isEmpty() || editing || narrate) return
        val now = System.currentTimeMillis()
        // Nếu một câu đã chậm hơn 8 giây và câu mới đang chờ, bỏ kết quả cũ để không lệch cảnh.
        if (jobs.isNotEmpty() && now - w.seenAt > 8_000L) { stats.inc("hang_day"); return }
        val text = cards.joinToString(" ") { it.tr.text }
        val item = SubtitlePacer.Item(text, w.src, pacer.holdFor(text, w.speechMs), cards[0].speaker, Payload(cards, w.seenAt))
        val shown = pacer.submit(item, now, w.replace)
        if (shown != null) render(shown) else { stats.inc("hang_cho"); scheduleHide() }
    }

    private fun render(item: SubtitlePacer.Item) {
        val p = item.payload as? Payload ?: return
        showCards(p.cards, item.src)
        val at = System.currentTimeMillis()
        for (c in p.cards) RecentLines.add(c.orig, c.tr.text, c.speaker, c.unsure, at)
        // độ trễ: từ lúc chữ được đọc từ màn hình (hoặc lời nghe được nhận dạng xong) tới lúc bản dịch hiện; chưa gồm thời gian nhận dạng giọng nói
        stats.latency(System.currentTimeMillis() - p.seenAt)
        scheduleHide()
    }

    /** Hẹn giờ kiểm tra có thể ẩn dòng đang hiện chưa (hết thời gian đọc, màn hình không còn chữ). */
    private val hideCheck = Runnable {
        try {
            if (running && !editing) {
                val now = System.currentTimeMillis()
                pump()
                if (pacer.showing != null) {
                    if (pacer.mayHide(now)) { if (canHide(now)) softHide() else if (!textOnScreen) scheduleHide() } else scheduleHide()
                }
            }
        } catch (_: Throwable) {}
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideCheck)
        val at = pacer.hideAt()
        if (at == 0L) return
        handler.postDelayed(hideCheck, (at - System.currentTimeMillis()).coerceAtLeast(250L))
    }

    /**
     * Dòng đang hiện đã đủ thời gian đọc và không còn lý do giữ: lời nghe được (không có chữ nào trên màn hình để theo dõi)
     * ẩn ngay khi hết thời gian đọc; dòng đọc từ màn hình ẩn khi chữ đã biến mất đủ lâu (không phải một lần đọc hụt).
     */
    private fun canHide(now: Long): Boolean {
        val cur = pacer.showing ?: return false
        if (!pacer.mayHide(now)) return false
        return cur.src == Src.ASR || (!textOnScreen && now - textGoneSince >= 1_200L)
    }

    /** Ẩn phụ đề (đã đủ thời gian đọc và chữ trên màn hình đã biến mất); vẫn nhớ câu vừa hiện để làm dòng "câu trước". */
    private fun softHide() {
        pacer.cleared()
        handler.removeCallbacks(hideCheck)
        sendTv("clear", "")
        overlay?.apply { text = ""; visibility = View.GONE }
    }

    /** Mỗi nhịp quét: câu nghe được chờ OCR xác nhận mà hết hạn thì hiện, câu xếp hàng tới lượt thì hiện, dòng cũ hết hạn thì ẩn. */
    private fun housekeeping() {
        if (!running || editing || narrate || !ready) return
        val now = System.currentTimeMillis()
        val outs = fusion.poll(now)
        if (outs.isNotEmpty()) {
            val fresh = ArrayList<Line>()
            applyOuts(outs, false, fresh, 0L, now)
            enqueue(fresh, 0L, now, false)
        }
        pump()
        if (!textOnScreen) fusion.onScreenEmpty(now)
        if (canHide(now)) softHide()
    }

    private fun scanDelay(): Long {
        val pr = Prefs(this)
        val f = when (pr.perf) {
            0 -> 2.0
            2 -> if (ocr?.isCloud == true) 1.0 else 0.7
            else -> 1.0
        }
        return (pr.scanMs * f).toLong().coerceIn(100L, 4000L)
    }

    private fun batteryPct(): Int = try {
        val v = (getSystemService(BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (v in 0..100) v else -1
    } catch (_: Exception) { -1 }

    /** Game đổi sang ngôn ngữ Latin khác (Anh sang Pháp...): nạp bộ dịch mới ở nền, xong mới thay; trong lúc đó vẫn dịch bằng bộ cũ. */
    private fun switchLang(newLang: String) {
        val ok = newLang != lang && newLang != "vi" && newLang != pendingLang &&
            LangDetect.scriptOfLang(newLang) == 'L' && LangDetect.scriptOfLang(lang) == 'L'
        if (!ok) { langTrack?.current = lang; return }
        pendingLang = newLang
        val t2 = TextTranslator(this, Prefs(this), newLang)
        t2.prepare { done ->
            pendingLang = null
            if (!running || !done) {
                runCatching { t2.close() }
                langTrack?.current = lang
                if (running) stats.inc("doi_ngon_ngu_loi")
                return@prepare
            }
            // bộ cũ có thể còn đang dịch dở một câu: đóng muộn để câu đó vẫn ra kết quả
            tt?.let { old -> retired.add(old); handler.postDelayed({ runCatching { old.close() }; retired.remove(old) }, 30_000L) }
            tt = t2
            lang = newLang
            srcName = Prefs.srcNameOf(newLang)
            langTrack?.current = newLang
            stats.inc("doi_ngon_ngu")
            android.widget.Toast.makeText(this, "Chữ trong game đã đổi sang $srcName: đã chuyển bộ dịch.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Số lần đọc giống nhau liên tiếp trước khi tin một câu: câu cực ngắn dễ là chữ rác nên cần thấy lâu hơn. */
    private fun requiredStable(utts: List<Utterance>): Int {
        val minLetters = utts.minOf { OcrGuard.letterCount(it.text) }
        var need = when {
            minLetters <= 1 -> maxOf(needStable, 3)
            minLetters <= 2 -> maxOf(needStable, 2)
            else -> needStable
        }
        // chỉ có tên, chưa có lời thoại: thường là thẻ tên hiện trước khi chữ chạy ra, đợi thêm để khỏi nháy
        if (utts.all { it.nameLike }) need = maxOf(need, 3)
        // Thuyết minh đọc trực tiếp OCR, không có bước kiểm tra bản dịch: cần ít nhất hai khung hình ổn định.
        if (narrate) need = maxOf(need, 2)
        return need
    }

    private fun onOcrResult(r: List<Blk>, scanAt: Long) {
        if (!running) { finishBusy(); return }
        if (syncGameContext()) { finishBusy(); return }
        val now = System.currentTimeMillis()
        val cjk = Prefs.isCjk(lang)
        val lines = ArrayList<OcrLine>()
        for (b in r) {
            val t = OcrGuard.clean(b.text)
            if (t.isEmpty()) continue
            val v = OcrGuard.check(t, b.conf)
            if (!v.ok) { stats.inc("ocr_rac"); continue }
            val bx = b.box
            lines.add(OcrLine(v.text, bx?.let { Box(it.left, it.top, it.right, it.bottom) }, b.conf))
        }
        val prefs = Prefs(this)
        // Không dò tên game bằng lời thoại; chỉ nhận tựa game khớp toàn dòng OCR và lặp hai lượt.
        if (prefs.aiAuto) {
            val detected = autoGameDetector.observe(lines.map { it.text }, PackStore.gameName(prefs))
            if (detected != null && AutoGameSwitch.apply(prefs, detected)) {
                syncGameContext()
                show("✦ AI Tự động đã nhận diện: $detected")
                finishBusy()
                return
            }
        }
        val utts = DialogueSplitter.split(lines, cjk, speakers).map { u ->
            // Người dùng đã xóa tên OCR nhận nhầm: bỏ nhãn người nói, giữ nguyên nội dung thoại.
            if (prefs.isIgnoredSpeaker(u.speaker)) Utterance(null, u.text, u.nameLike) else u
        }
        if (utts.isEmpty()) {
            if (textOnScreen) { textOnScreen = false; textGoneSince = now }
            fusion.onScreenEmpty(now)
            last = ""; cand = ""; stable = 0; ocrStability.reset()
            if (narrate || pacer.showing == null) { if (!autoShown) show("") }
            else if (canHide(now)) softHide() else scheduleHide()
            finishBusy()
            return
        }
        textOnScreen = true
        val sig = DialogueSplitter.signature(utts)
        if (sig == last) { finishBusy(); return }
        val requiredFrames = requiredStable(utts)
        val minimumStableMs = when {
            requiredFrames >= 3 -> 320L
            requiredFrames >= 2 -> 220L
            else -> 160L
        }
        if (!ocrStability.observe(sig, requiredFrames, now, minimumStableMs)) {
            cand = sig
            stable = ocrStability.frameCount
            finishBusy()
            return
        }
        cand = sig
        stable = ocrStability.frameCount
        last = sig
        // Chỉ nhớ người nói khi OCR đã ổn định; không lưu tên chưa được xác nhận.
        for (u in utts) if (!u.nameLike && !u.speaker.isNullOrBlank()) prefs.rememberSpeaker(u.speaker!!)
        stats.inc("ocr_cau", utts.size.toLong())
        if (narrate) {
            // Không đọc nhãn tên đứng riêng khi đã có câu thoại thực sự trong khung.
            val spoken = utts.filter { !it.nameLike && VietsubGuard.isVietnamese(it.text) }
            if (spoken.isEmpty()) {
                // Không dịch, không gọi Gemini, không đọc phụ đề nước ngoài/không rõ ngôn ngữ.
                if (!autoShown) show("")
                finishBusy()
                return
            }
            val cards = spoken.map { u ->
                val phrase = u.text.replace("\n", sep)
                Card(u.speaker, "", TextTranslator.Tr(phrase, null, false, "doc"), false)
            }
            showCards(cards, Src.OCR)
            finishBusy()
            return
        }
        if (Prefs(this).autoLang) {
            for (u in utts) {
                if (!u.nameLike) langTrack?.observe(LangDetect.guess(u.text))?.let { switchLang(it) }
            }
        }
        val fresh = ArrayList<Line>()
        for (u in utts) applyOuts(fusion.onOcr(u.text, u.speaker, now), u.nameLike, fresh, 0L, scanAt)
        enqueue(fresh, 0L, scanAt, false)
        finishBusy()
    }

    /** Xong một lượt đọc chữ (hoặc dịch menu/vùng): mở khóa quét, và cho câu đang chờ có cơ hội chạy. */
    private fun finishBusy() {
        busy = false
        if (running && ready && !editing && !narrate && (jobs.isNotEmpty() || pacer.queued > 0)) handler.post { pump() }
    }

    /** Lỗi đọc chữ: mở khóa để quét lại, chỉ hiện thông báo lỗi của Gemini (không báo lỗi đọc trên máy), tối đa 8 giây một lần. */
    private fun ocrFail(m: String) {
        finishBusy()
        lastSig = null; lastFine = null
        overlay?.let { if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE }
        if (m.isNotEmpty() && System.currentTimeMillis() - lastErrAt > 8000) { lastErrAt = System.currentTimeMillis(); show(m, readAloud = false) }
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

    /** Dịch xong thì gỡ khung cam ngay (khung chỉ để chọn vùng, không để lù lù trên game); bản dịch vẫn hiện. Bấm "Vùng dịch" để chọn vùng khác. */
    private fun hideRegionFrame() {
        val old = region ?: return
        try { wm.removeView(old) } catch (_: Exception) {}
        region = null
        // giữ bản dịch vùng thêm một lúc rồi tự quay lại dịch phụ đề
        menuUntil = System.currentTimeMillis() + 15000
        handler.postDelayed({ if (running && region == null && !editing && System.currentTimeMillis() >= menuUntil) show("") }, 15200)
        placeTranslation()
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
            captureBuffer.reader?.acquireLatestImage()
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
        val scope = scanScope()
        reader.read(crop, { m -> if (validScan(scope)) ocrFail(m) else finishBusy() }) { r ->
            if (!validScan(scope)) { finishBusy(); return@read }
            val text = r.joinToString("\n") { it.text }.replace("\n", sep).trim()
            if (text.length < minLen) { show("Không thấy chữ trong khung."); finishBusy(); return@read }
            translateText(text) { vi ->
                if (!validScan(scope)) { finishBusy(); return@translateText }
                // Confidence is shown as a visual warning; a non-empty translation must still be narrated.
                val canNarrate = TranslationSpeechPolicy.isSpeakable(
                    TranslationSpeechPolicy.Candidate(vi.text, vi.uncertain)
                )
                show(vi.caption, readAloud = canNarrate)
                hideRegionFrame()
                sendTv("region", vi.caption, canNarrate && speak && tvSpeakOn() && !tvVoiceOn())
                if (canNarrate && speak && tvVoiceOn()) tvVoice(vi.text)
                finishBusy()
            }
        }
    }

    /** Dịch bằng AI nếu đã bật (CloudAPI / Gemini, có nhớ vài câu trước), lỗi thì quay về dịch trên máy. */
    private fun translateText(text: String, done: (TextTranslator.Tr) -> Unit) {
        val t = tt
        if (t == null) done(TextTranslator.Tr(text, null, true, "may")) else t.translateFull(text, null, done)
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

    private fun tvVoice(text: String, d: Delivery? = null, speakerName: String? = null, scene: List<VoiceLine>? = null) {
        val sp = speaker ?: return
        val sendAudio: (Pcm?, Float) -> Unit = { pcm, speed ->
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
        if (scene != null) sp.synthForTvScene(scene, sendAudio)
        else sp.synthForTv(text, d, speakerName, sendAudio)
    }

    /** Gửi phụ đề lên app Dịch game TV (nếu đã ghép). kind: sub | region | clear. */
    private fun sendTv(kind: String, text: String, speakIt: Boolean = false, force: Boolean = false) {
        val hidden = kind == "sub" && Prefs(this).listenOnly
        val outKind = if (hidden) "clear" else kind
        val outText = if (hidden && !speakIt) "" else text
        TvOut.publish(this, outKind, outText, speakIt)
        val p = getSharedPreferences("dg", MODE_PRIVATE)
        val ip = p.getString("tvip", "").orEmpty()
        val tok = p.getString("tvtoken", "").orEmpty()
        if (p.getString("playon", "phone") != "tv" || ip.isEmpty() || tok.isEmpty()) return
        val key = "$ip:$tok:$outKind:$speakIt:$outText"
        if (!force && key == tvLast) return
        tvLast = key
        val size = p.getInt("fs", 18) * p.getInt("subscale", 100) / 100 + 12
        tvExec.execute {
            try {
                val c = URL("http://$ip:8765/sub").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 1500; c.readTimeout = 1500; c.doOutput = true
                c.setRequestProperty("X-Token", tok)
                val body = JSONObject().put("kind", outKind).put("text", outText).put("size", size).put("speak", speakIt).put("fg", styleFg()).put("bg", styleBg())
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                try { if (c.responseCode !in 200..299 && tvLast == key) tvLast = "" } finally { c.disconnect() }
            } catch (_: Exception) { if (tvLast == key) tvLast = "" }
        }
    }

    /**
     * Hộp phụ đề: câu trước (nhỏ), câu gốc, rồi bản dịch (to). Nhiều người nói cùng lúc thì mỗi người một dòng,
     * tên người nói có màu riêng (cùng tên luôn cùng màu). Bản dịch còn nghi ngờ có dấu (?); lời nghe được từ âm thanh có biểu tượng loa.
     */
    private fun showCards(cards: List<Card>, src: Src) {
        val sb = SpannableStringBuilder()
        val fg = styleFg()
        val dim = (fg and 0x00FFFFFF) or 0xCC000000.toInt()
        val pr = Prefs(this)
        fun add(t: String, size: Float, color: Int) {
            if (t.isEmpty()) return
            val a = sb.length
            sb.append(t).append("\n")
            sb.setSpan(RelativeSizeSpan(size), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(color), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (pr.showPrev) add(prevVi, 0.7f, (fg and 0x00FFFFFF) or 0x99000000.toInt())
        for ((i, c) in cards.withIndex()) {
            if (pr.showOrig) add((if (src != Src.OCR && c.orig.isNotEmpty()) "🔊 " else "") + c.orig, 0.75f, dim)
            val d = c.tr.delivery
            if (i == cards.lastIndex && pr.emoTr && pr.emoLabel && d != null && d.label.isNotEmpty()) add("● " + d.label, 0.62f, d.mood.color)
            val a = sb.length
            val name = c.speaker
            if (name != null) {
                sb.append(name).append(": ")
                sb.setSpan(ForegroundColorSpan(SpeakerColor.of(name)), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val b = sb.length
            sb.append(c.tr.text)
            sb.setSpan(ForegroundColorSpan(fg), b, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (c.unsure) {
                val u = sb.length
                sb.append(" (?)")
                sb.setSpan(ForegroundColorSpan(0xFFFFB74D.toInt()), u, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append("\n")
            sb.setSpan(RelativeSizeSpan(1.15f), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (sb.isNotEmpty()) sb.delete(sb.length - 1, sb.length)
        overlay?.apply { text = sb; visibility = if (pr.listenOnly) View.GONE else View.VISIBLE }
        val vi = cards.joinToString(" ") { it.tr.text }
        // uncertainty là cảnh báo chất lượng, không phải lý do bỏ đọc một bản dịch đã có nội dung.
        val scene = cards.filter {
            TranslationSpeechPolicy.isSpeakable(TranslationSpeechPolicy.Candidate(it.tr.text, it.unsure))
        }.map { VoiceLine(it.tr.text.trim(), it.speaker, it.tr.delivery) }
        val tvSpeaks = speak && tvSpeakOn() && !tvVoiceOn() && scene.size == cards.size
        // TV tự đọc thì chỉ gửi lời dịch; không đọc thì gửi kèm tên người nói
        val tvText = if (tvSpeaks || cards.none { it.speaker != null }) cards.joinToString(" ") { it.tr.caption }
        else cards.joinToString("\n") { (it.speaker?.let { n -> "$n: " } ?: "") + it.tr.caption }
        sendTv("sub", tvText, tvSpeaks)
        if (speak && tvVoiceOn() && scene.isNotEmpty()) {
            tvVoice(scene.joinToString(" ") { it.text }, scene.last().delivery, scene.last().speaker, scene)
        }
        if (speak && ready && !editing && !tvSpeakOn()) speaker?.speakScene(scene)
        prevVi = vi; prevOrig = cards.joinToString(" ") { it.orig }; autoShown = false
    }

    /** Dịch toàn bộ chữ giao diện trong ảnh, vẽ từng khối tại vị trí gốc. */
    private fun menuOnce() {
        if (menuOverlay?.visibility == View.VISIBLE) {
            clearMenuOverlay(); menuUntil = 0L; show(""); return
        }
        if (busy || !ready || editing || narrate) return
        clearMenuOverlay()
        busy = true
        overlay?.visibility = View.INVISIBLE
        handler.postDelayed({ menuGrab() }, 400)
    }

    private fun menuGrab() {
        val img = try {
            ensureSize()
            captureBuffer.reader?.acquireLatestImage()
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
        // Explicit full-screen mode includes the subtitle area, too. The locked
        // subtitle frame has no visible pixels and must not exclude game text.
        val skip = listOfNotNull(rectOf(region))
        val scene = Ocr.thumbFine(bmp)
        val keep = runCatching { bmp.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        val scope = scanScope()
        reader.read(bmp, { m -> keep?.recycle(); if (validScan(scope)) ocrFail(m) else finishBusy() }) { r ->
            if (!validScan(scope)) { keep?.recycle(); finishBusy(); return@read }
            val items = try { menuItems(r, keep, skip) } catch (_: Exception) { emptyList() } finally { keep?.recycle() }
            if (!running) { finishBusy(); return@read }
            if (items.isEmpty()) {
                show("Không thấy chữ phù hợp để dịch trên màn hình.", false)
                finishBusy()
                return@read
            }
            renderMenuItems(items, scene)
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
        lastFine = null
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
        // Nút "Xong" ngay trên khung: bấm là khóa khung và ẩn khung đi, khỏi phải kéo thông báo xuống.
        val done = TextView(this).apply {
            text = "✓ Xong"
            setTextColor(0xFF001417.toInt())
            setBackgroundColor(0xFF00E5FF.toInt())
            textSize = 14f
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            setOnClickListener { try { setEditing(false) } catch (_: Throwable) {} }
        }
        box.addView(done, FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        doneBtn = done
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
        doneBtn?.visibility = if (on) View.VISIBLE else View.GONE
        wm.updateViewLayout(f, flp)
        last = ""; cand = ""; stable = 0; ocrStability.reset(); lastFine = null
        if (on) { clearMenuOverlay(); menuUntil = 0L; jobs.clear(); fusion.clear() }
        show(if (on) "Kéo khung xanh tới dòng phụ đề, kéo ô vuông góc dưới phải để đổi cỡ. Xong thì kéo thông báo xuống, bấm \"Chỉnh/Khóa khung\"." else "")
        if (!on) pump()
    }

    /** Bản dịch hiện ở phía đối diện khung để không bị đọc lại chính nó. */
    private fun placeTranslation() {
        val o = overlay ?: return
        val l = if (region != null) rlp else flp
        val g = if (l.y + l.height / 2 < ch / 2) Gravity.BOTTOM else Gravity.TOP
        if (tlp.gravity != g) { tlp.gravity = g; wm.updateViewLayout(o, tlp) }
    }

    private fun show(t: String, readAloud: Boolean = true) {
        // mọi thông báo hay lệnh xoá đều thay thế dòng phụ đề đang hiện: bộ giữ nhịp phải biết là đã hết
        pacer.cleared(); handler.removeCallbacks(hideCheck)
        if (t.isEmpty()) { clearMenuOverlay(); sendTv("clear", "") }
        overlay?.apply { text = t; visibility = if (t.isEmpty()) View.GONE else View.VISIBLE }
        if (readAloud && t.isNotEmpty() && speak && ready && !editing && !tvSpeakOn()) speaker?.speak(t)
    }

    /** Theo kích thước thật của màn hình (xoay ngang/dọc), tạo lại bộ đệm ảnh khi đổi. */
    @Suppress("DEPRECATION")
    private fun ensureSize() {
        val mediaProjection = projection ?: return
        val dm = DisplayMetrics()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(0).getRealMetrics(dm)
        val width = dm.widthPixels
        val height = dm.heightPixels
        val changed = captureBuffer.resize(width, height,
            create = { ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2) },
            attach = { next ->
                val current = display
                if (current == null) {
                    display = checkNotNull(mediaProjection.createVirtualDisplay(
                        "dg", width, height, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        next.surface, null, handler
                    )) { "Không tạo được màn hình ảo." }
                } else {
                    current.resize(width, height, dm.densityDpi)
                    current.surface = next.surface
                }
            }
        )
        if (changed) {
            clearMenuOverlay(); menuUntil = 0L; autoCand = ""; autoDone = ""; autoStable = 0
            relayoutFrame()
        }
    }

    private fun capture() {
        if (!ready || editing) return
        // Detect rotation even while translated menu text is held on screen.
        ensureSize()
        if (menuScene != null) { checkMenuScene(); return }
        if (busy || System.currentTimeMillis() < menuUntil) return
        val fr = frame ?: return
        val img = captureBuffer.reader?.acquireLatestImage() ?: return
        val crop = try {
            val p = img.planes[0]
            val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            try {
                full.copyPixelsFromBuffer(p.buffer)
                val loc = IntArray(2)
                fr.getLocationOnScreen(loc)
                val x = loc[0].coerceIn(0, img.width - 1)
                val y = loc[1].coerceIn(0, img.height - 1)
                val w = fr.width.coerceIn(1, img.width - x)
                val h = fr.height.coerceIn(1, img.height - y)
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
        stats.inc("quet")
        if (o.isCloud) {
            // Gemini: chỉ gửi khi hình trong khung thật sự đổi, để khỏi tốn lượt dùng.
            val sig = Ocr.thumb(crop)
            if (Ocr.same(sig, lastSig) && (cand.isEmpty() || cand == last) && skipRun < 12) {
                skipRun++
                // Không gửi ảnh trùng lên Gemini thì cũng phải giải phóng crop,
                // vì nhánh này không chuyển quyền sở hữu cho Ocr.read().
                if (!crop.isRecycled) runCatching { crop.recycle() }
                stats.inc("quet_dung_yen")
                return
            }
            lastSig = sig
            skipRun = 0
        } else if (Prefs(this).skipStatic) {
            // Đọc chữ trên máy: hình đứng yên y hệt lần đọc trước thì không đọc lại (đỡ pin, đỡ nóng). Nhưng không bỏ qua khi đang chờ
            // xác nhận một câu, và cứ vài lần lại đọc một lần để chắc chắn không sót thay đổi rất nhỏ.
            val fine = Ocr.thumbFine(crop)
            val settled = cand.isEmpty() || cand == last
            if (settled && skipRun < 12 && Ocr.sameStrict(fine, lastFine)) {
                skipRun++
                if (!crop.isRecycled) runCatching { crop.recycle() }
                stats.inc("quet_dung_yen")
                return
            }
            skipRun = 0
            lastFine = fine
        }
        busy = true
        val scanAt = System.currentTimeMillis()
        val scope = scanScope()
        o.read(crop, { m -> if (validScan(scope)) ocrFail(m) else finishBusy() }) { r ->
            if (validScan(scope)) onOcrResult(r, scanAt) else finishBusy()
        }
    }

    override fun onDestroy() {
        running = false
        cloudRefresh.close()
        jobs.clear()
        retired.forEach { runCatching { it.close() } }
        // dừng thu âm trước, rồi chờ bộ nhận dạng xử lý nốt, cuối cùng mới đóng hàng đợi; làm ở nền để không chặn luồng chính
        val cap = phoneAudio; val wk = audioWorker; val aq = audioQueue
        phoneAudio = null; audioWorker = null; audioQueue = null
        if (cap != null || wk != null || aq != null) {
            Thread {
                runCatching { cap?.stop() }
                runCatching { wk?.stopAndJoinUntilStopped() }
                runCatching { aq?.close() }
            }.start()
        }
        try {
            val rep = stats.report(System.currentTimeMillis(), batteryPct())
            SessionStats.lastReport = rep
            getSharedPreferences("dg", MODE_PRIVATE).edit().putString("lastreport", rep).apply()
        } catch (_: Exception) {}
        active = false
        handler.removeCallbacksAndMessages(null)
        clearMenuOverlay()
        runCatching { menuOverlay?.let { wm.removeView(it) } }; menuOverlay = null
        try { overlay?.let { wm.removeView(it) }; frame?.let { wm.removeView(it) }; region?.let { wm.removeView(it) } } catch (_: Exception) {}
        display?.release(); captureBuffer.close()
        projection?.stop()
        tt?.close(); ocr?.close()
        speaker?.shutdown(); tvExec.shutdown()
        super.onDestroy()
    }
}
