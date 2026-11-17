package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.metallic.chiaki.lib.ConnectedEvent
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.lib.Event
import com.metallic.chiaki.lib.LoginPinRequestEvent
import com.metallic.chiaki.lib.QuitEvent
import com.metallic.chiaki.lib.RumbleEvent
import com.metallic.chiaki.lib.Session
import kotlin.math.abs

/**
 * Màn chơi Remote Play ngay trong app: hình từ máy chơi game, tay cầm ảo (hoặc tay cầm thật nối với điện thoại),
 * và bản dịch của app hiện đè lên hình qua đúng cơ chế dịch màn hình đang dùng.
 * Dọc: hình ở trên, thanh công cụ, tay cầm ở dưới. Ngang: hình toàn màn hình, tay cầm trong suốt phủ lên.
 */
class PsPlayActivity : Activity(), SurfaceHolder.Callback {
    companion object {
        const val EXTRA_MODE = "mode"       // "tv" hoặc "phone"
        const val EXTRA_PLAYER = "player"
        const val EXTRA_TRANSLATE = "translate"   // bật dịch phụ đề ngay khi vào chơi
    }

    private var console: PsConsole? = null
    private var player = 0
    private var mode = "phone"
    private var ratio = 16f / 9f

    private var session: Session? = null
    // PCM game audio is handed off through a bounded queue; the native callback never waits.
    private var gameAudioPcmQueue: GameAudioPcmQueue? = null
    private var gameAudioAsrWorker: GameAudioAsrWorker? = null
    private var gen = 0
    private var surface: Surface? = null
    // SurfaceView có thể bị thay khi xoay màn hình; chỉ holder hiện tại được phép điều khiển surface. 
    private var activeSurfaceHolder: SurfaceHolder? = null
    private var manual = false      // đã dừng hẳn, chờ người dùng bấm kết nối lại
    private var connected = false
    private var stopping = false
    private var activityStarted = false
    private var reconnectPending = false
    // Mạng chập chờn: thử khôi phục phiên theo backoff, không bật hộp thoại ngay lần rớt đầu.
    private var reconnectAttempt = 0
    private var pendingReconnectDelayMs = 0L
    private var reconnectRunnable: Runnable? = null

    private var padState = ControllerState()
    private var phys = ControllerState()
    // Không trộn trạng thái từ nhiều tay cầm cùng lúc; thiết bị đầu tiên điều khiển phiên.
    private var activePhysicalDeviceId: Int? = null
    // Giữ riêng trạng thái cò nhấn bằng nút và cò analog để hai nguồn không ghi đè lẫn nhau.
    private var physicalL2Digital = false
    private var physicalR2Digital = false
    private var physicalL2Analog = 0f
    private var physicalR2Analog = 0f
    // D-pad có thể đến từ KeyEvent hoặc HAT axis; giữ riêng hai nguồn để không ghi đè nhau.
    private var physicalDpadKeys = 0u
    private var physicalDpadHat = 0u
    private var pad: PadView? = null
    private var padOn = true
    private var statusView: TextView? = null
    private var chipView: TextView? = null
    private var statusSub: TextView? = null
    private var statusCard: View? = null
    private var subsIcon: ToolIcon? = null
    private var scanIcon: ToolIcon? = null
    private var speakIcon: ToolIcon? = null
    private var padIcon: ToolIcon? = null
    private var toolsBar: View? = null
    private var toolsOpen = true
    private var zoomLevel = 0
    // Chỉ bản debug: dựng giao diện mà không kết nối máy thật (để chụp ảnh kiểm tra giao diện trên máy giả lập).
    private var demo = false
    // Dịch phụ đề chạy ngay trong màn chơi (chụp hình thẳng từ SurfaceView), không cần quyền ghi màn hình.
    @Volatile private var live: LiveSubs? = null
    private var videoView: SurfaceView? = null
    // Người dùng muốn dịch: bật lại tự động mỗi khi kết nối (kể cả sau khi rời màn hình rồi quay lại)
    private var wantTranslate = false
    private var vidFrame: FrameLayout? = null
    private val ui = Handler(Looper.getMainLooper())
    private var inputListenerRegistered = false
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // NetworkCallback không đảm bảo chạy trên main thread; mọi thay đổi UI/session phải về main.
            ui.post {
                // Chỉ bỏ trạng thái chờ khi chưa có lịch retry. Một callback mạng
                // khác (ví dụ 4G xuất hiện trong lúc Wi-Fi vẫn hoạt động) không được
                // hủy backoff đã đặt sau lỗi stream, tránh retry dồn dập.
                if (reconnectPending && reconnectRunnable == null &&
                    activityStarted && !manual && session == null && !stopping) {
                    pendingReconnectDelayMs = 0L
                    requestConnect()
                }
            }
        }

        override fun onLost(network: Network) {
            ui.post {
                // Android có thể báo mất một mạng cũ sau khi Wi-Fi/Ethernet khác đã hoạt động.
                // Chỉ cảnh báo khi thiết bị thực sự không còn transport mạng dùng được.
                if (connected && !hasUsableNetwork()) {
                    setStatus("Mất kết nối mạng · đang chờ mạng trở lại…", true)
                }
            }
        }
    }
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) {
            // Android có thể thay đổi cấu hình trục/nút khi tay cầm reconnect.
            // Reset để không giữ nút/cò cũ trong lúc driver phát lại trạng thái mới.
            if (activePhysicalDeviceId == deviceId) resetPhysicalControllerState()
        }
        override fun onInputDeviceRemoved(deviceId: Int) {
            // Chỉ reset khi thiết bị đang điều khiển bị rút; không làm mất trạng thái của tay cầm đang dùng khác.
            if (activePhysicalDeviceId == deviceId) resetPhysicalControllerState()
        }
    }

    private fun resetPhysicalControllerState() {
        activePhysicalDeviceId = null
        phys = ControllerState()
        physicalL2Digital = false
        physicalR2Digital = false
        physicalL2Analog = 0f
        physicalR2Analog = 0f
        physicalDpadKeys = 0u
        physicalDpadHat = 0u
        push()
    }

    private fun updatePhysicalDpad() {
        phys.buttons = ControllerDpad.combine(phys.buttons, physicalDpadKeys, physicalDpadHat)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mode = intent.getStringExtra(EXTRA_MODE) ?: "phone"
        wantTranslate = intent.getBooleanExtra(EXTRA_TRANSLATE, false)
        player = intent.getIntExtra(EXTRA_PLAYER, 0)
        padOn = mode != "tv"
        val demoMode = if (BuildConfig.DEBUG) intent.getStringExtra("dg_demo") else null
        demo = demoMode != null
        val c = if (demo) PsConsole("192.168.1.20", true, "PS5 (xem thử)", mutableListOf(
            PsPlayer("Người chơi 1", ByteArray(0), 0, ByteArray(0), ByteArray(0), "demo"),
            PsPlayer("Người chơi 2", ByteArray(0), 0, ByteArray(0), ByteArray(0), "demo2"),
        )) else PsStore.load(this)
        if (!demo && (!PsNative.available || c == null || c.players.isEmpty())) {
            Toast.makeText(this, "Chưa có máy đã ghép nối, hoặc máy này chưa hỗ trợ chơi PS5 trong app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        console = c
        CrashLog.mark("choi:vao mode=$mode")
        if (c == null) return
        player = player.coerceIn(0, c.players.size - 1)
        if (!demo) try {
            val prof = PsStore.profile(this)
            if (prof.height > 0) ratio = prof.width.toFloat() / prof.height
        } catch (_: Throwable) {}
        build()
        if (demo) android.util.Log.i("DG_PLAY", "tao man choi act=${System.identityHashCode(this)} mode=$demoMode")
        // Chỉ bản debug: tự thao tác vài bước để CI chụp được ảnh giao diện.
        if (demoMode == "adjust") {
            ui.postDelayed({ if (!isFinishing && !isDestroyed) toggleAdjust() }, 1500)
            ui.postDelayed({ if (!isFinishing && !isDestroyed) toggleAdjust() }, 5000)
        } else if (demoMode == "land") {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        if (!inputListenerRegistered) {
            (getSystemService(Context.INPUT_SERVICE) as? InputManager)?.registerInputDeviceListener(inputDeviceListener, ui)
            inputListenerRegistered = true
        }
        if (!networkCallbackRegistered) {
            runCatching {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build()
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .registerNetworkCallback(request, networkCallback)
                networkCallbackRegistered = true
            }
        }
        if (console != null && !manual && !demo) requestConnect()
    }

    override fun onStop() {
        activityStarted = false
        if (inputListenerRegistered) {
            (getSystemService(Context.INPUT_SERVICE) as? InputManager)?.unregisterInputDeviceListener(inputDeviceListener)
            inputListenerRegistered = false
        }
        if (networkCallbackRegistered) {
            runCatching { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        super.onStop()
        live?.let { l -> if (l.running) { l.stop(); syncTools() } }
        // Rời màn hình thì dừng phiên: PS5 không cần giữ luồng hình cho một app đang ẩn.
        resetPhysicalControllerState()
        padState = ControllerState(); pad?.releaseAll()
        reconnectPending = false
        reconnectRunnable?.let(ui::removeCallbacks)
        reconnectRunnable = null
        pendingReconnectDelayMs = 0L
        if (session != null) {
            if (!isFinishing) manual = false
            shutdownSession()
        }
    }

    override fun onDestroy() {
        if (demo) android.util.Log.i("DG_PLAY", "huy man choi act=${System.identityHashCode(this)}")
        activityStarted = false
        reconnectPending = false
        runCatching { live?.release() }
        live = null
        shutdownSession()
        ui.removeCallbacksAndMessages(null)
        CrashLog.mark("choi:xong")
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            // Dialog, màn hình hệ thống hoặc app khác có thể lấy focus mà không
            // gửi đủ ACTION_UP cho các ngón đang giữ trên tay cầm ảo.
            resetPhysicalControllerState()
            pad?.releaseAll()
        }
    }

    override fun onConfigurationChanged(n: Configuration) {
        super.onConfigurationChanged(n)
        build()
    }

    // ---------------- Giao diện ----------------

    private class FitFrame(c: Context, private val ratio: Float) : FrameLayout(c) {
        override fun onMeasure(wSpec: Int, hSpec: Int) {
            val maxW = MeasureSpec.getSize(wSpec)
            val maxH = MeasureSpec.getSize(hSpec)
            var w = maxW
            var h = (w / ratio).toInt()
            if (MeasureSpec.getMode(hSpec) != MeasureSpec.UNSPECIFIED && maxH in 1 until h) { h = maxH; w = (h * ratio).toInt() }
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        }
    }

    /** Vòng xoay vàng trên thẻ "Đang tìm máy…". Chỉ chạy khi đang hiện. */
    private class Spin(c: Context) : View(c) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Ui.GOLD }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFD54A }
        private val box = RectF()
        private var angle = 0f
        private var anim: ValueAnimator? = null

        private fun begin() {
            if (anim != null) return
            anim = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { angle = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        private fun end() { anim?.cancel(); anim = null }

        override fun onVisibilityAggregated(isVisible: Boolean) {
            super.onVisibilityAggregated(isVisible)
            if (isVisible && isAttachedToWindow) begin() else end()
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); if (isShown) begin() }
        override fun onDetachedFromWindow() { end(); super.onDetachedFromWindow() }

        override fun onDraw(cv: Canvas) {
            val s = minOf(width, height).toFloat()
            if (s <= 0f) return
            val w = s * 0.1f
            ring.strokeWidth = w
            track.strokeWidth = w
            val pad = w
            box.set((width - s) / 2f + pad, (height - s) / 2f + pad, (width + s) / 2f - pad, (height + s) / 2f - pad)
            cv.drawArc(box, 0f, 360f, false, track)
            cv.drawArc(box, angle, 100f, false, ring)
        }
    }

    private fun dpi(n: Number): Int = Ui.dp(this, n)

    @Suppress("DEPRECATION")
    private fun build() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility =
            if (land) (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            else View.SYSTEM_UI_FLAG_VISIBLE

        val sv = SurfaceView(this)
        videoView = sv
        sv.holder.addCallback(this)

        // Thẻ chờ kết nối đè lên hình: vòng xoay + "Đang tìm máy…" + gợi ý.
        val title = Ui.tv(this, "Đang tìm máy…", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        val sub = Ui.tv(this, "", 12.5f, Ui.SUB).apply { gravity = Gravity.CENTER }
        statusView = title
        statusSub = sub
        val card = Ui.vbox(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E00B0F0C"))
            setPadding(dpi(16), dpi(8), dpi(16), dpi(8))
            addView(Spin(this@PsPlayActivity), Ui.lp(dpi(46), dpi(46)).apply { bottomMargin = dpi(10) })
            addView(title, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
            addView(sub, Ui.lpm(this@PsPlayActivity, 4, 0))
        }
        statusCard = card

        val vid = FitFrame(this, ratio)
        vid.setBackgroundColor(Color.BLACK)
        vid.addView(sv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        vid.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        vidFrame = vid
        live?.attach(vid)
        applyZoom()

        val padV = PadView(this) { st -> padState = st; push() }
        pad = padV
        padV.visibility = if (padOn && live?.isAdjusting != true) View.VISIBLE else View.GONE

        val chip = Ui.tv(this, "", 12f, Ui.SUB).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dpi(10), dpi(5), dpi(10), dpi(5))
            background = Ui.bg(this@PsPlayActivity, Ui.CARD2, 14)
        }
        chipView = chip

        val (closeBtn, _) = roundIcon(ToolIcon.Kind.CLOSE) { leave() }
        val (padBtn, padIc) = roundIcon(ToolIcon.Kind.GAMEPAD) { togglePad() }
        padIcon = padIc
        val pill = playerPill()
        val bar = toolbar()
        toolsBar = bar
        bar.visibility = if (toolsOpen) View.VISIBLE else View.GONE

        if (!land) {
            val root = Ui.vbox(this)
            root.setBackgroundColor(Color.parseColor("#0B0F0C"))
            val top = Ui.hbox(this)
            top.setPadding(dpi(10), dpi(8), dpi(10), dpi(6))
            top.addView(closeBtn)
            top.addView(padBtn, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(8) })
            top.addView(chip, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dpi(8); rightMargin = dpi(8) })
            top.addView(pill)
            root.addView(top)

            // Khung video viền vàng, bo góc; phóng hình thì phần thừa bị cắt trong khung.
            val vcard = FrameLayout(this).apply {
                background = Ui.bg(this@PsPlayActivity, Color.BLACK, 16, Ui.GOLD, 2)
                setPadding(dpi(2), dpi(2), dpi(2), dpi(2))
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            vcard.addView(vid, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            root.addView(vcard, Ui.lp(MATCH_PARENT, WRAP_CONTENT).apply { leftMargin = dpi(10); rightMargin = dpi(10) })

            bar.background = Ui.bg(this, Color.parseColor("#17120D"), 24, Color.parseColor("#44F2B84B"), 1)
            root.addView(bar, Ui.lpm(this, 10, 0).apply { leftMargin = dpi(10); rightMargin = dpi(10) })

            // Thanh kéo: bấm để thu gọn hoặc mở lại thanh công cụ, dành chỗ cho tay cầm.
            val grip = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Thu gọn hoặc mở thanh công cụ"
                setOnClickListener { toggleTools() }
                addView(View(this@PsPlayActivity).apply { background = Ui.bg(this@PsPlayActivity, Color.parseColor("#88F2B84B"), 3) },
                    FrameLayout.LayoutParams(dpi(44), dpi(5), Gravity.CENTER))
            }
            root.addView(grip, Ui.lp(MATCH_PARENT, dpi(22)))
            root.addView(padV, Ui.lp(MATCH_PARENT, 0, 1f))
            setContentView(root)
        } else {
            val root = FrameLayout(this)
            root.setBackgroundColor(Color.BLACK)
            root.addView(vid, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
            padV.alpha = 0.8f
            root.addView(padV, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            // Hàng điều khiển ở giữa phía trên, nằm giữa hai cụm L2/L1 và R2/R1 của tay cầm nên không đè lên nút nào.
            bar.background = Ui.bg(this, Color.parseColor("#C017120D"), 22, Color.parseColor("#44F2B84B"), 1)
            val topRow = Ui.hbox(this)
            topRow.addView(closeBtn)
            topRow.addView(padBtn, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(8) })
            topRow.addView(bar, Ui.lp(dpi(312), WRAP_CONTENT).apply { leftMargin = dpi(8) })
            topRow.addView(pill, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(8) })
            root.addView(topRow, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dpi(6) })
            setContentView(root)
        }
        refreshStatus()
    }

    /** Nút tròn nhỏ có biểu tượng vẽ nét (✕, tay cầm). */
    private fun roundIcon(kind: ToolIcon.Kind, onClick: () -> Unit): Pair<FrameLayout, ToolIcon> {
        val ic = ToolIcon(this, kind, Ui.GOLD)
        val f = FrameLayout(this).apply {
            background = Ui.bg(this@PsPlayActivity, Ui.CARD2, 22, Color.parseColor("#44F2B84B"), 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(ic, FrameLayout.LayoutParams(dpi(20), dpi(20), Gravity.CENTER))
        }
        f.layoutParams = LinearLayout.LayoutParams(dpi(40), dpi(40))
        return f to ic
    }

    /** Ô chọn người chơi (tài khoản PSN đã ghép) ở góc trên bên phải. */
    private fun playerPill(): View {
        val pill = Ui.hbox(this)
        pill.background = Ui.bg(this, Ui.CARD2, 20, Color.parseColor("#44F2B84B"), 1)
        pill.setPadding(dpi(12), dpi(8), dpi(10), dpi(8))
        pill.isClickable = true
        pill.isFocusable = true
        val label = Ui.tv(this, console?.players?.getOrNull(player)?.label ?: "Người chơi 1", 13f, Ui.TEXT, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dpi(120)
        }
        pill.addView(label)
        pill.addView(ToolIcon(this, ToolIcon.Kind.CHEVRON, Ui.GOLD), Ui.lp(dpi(14), dpi(14)).apply { leftMargin = dpi(4) })
        pill.setOnClickListener { pickPlayer() }
        return pill
    }

    private fun pickPlayer() {
        val c = console ?: return
        val labels = c.players.map { it.label }
        if (labels.size < 2) {
            Toast.makeText(this, "Mới có một người chơi. Thêm người chơi ở màn Ghép nối.", Toast.LENGTH_SHORT).show()
            return
        }
        Ui.pick(this, "Chọn người chơi", labels) { i ->
            if (i != player) {
                player = i
                build()
                if (!demo) reconnect()
            }
        }
    }

    private fun toolbar(): LinearLayout {
        val bar = Ui.hbox(this)
        bar.setPadding(dpi(4), dpi(2), dpi(4), dpi(2))
        fun tool(kind: ToolIcon.Kind, desc: String, onClick: () -> Unit): ToolIcon {
            val ic = ToolIcon(this, kind, Ui.GOLD)
            val cell = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = desc
                setOnClickListener { onClick() }
                addView(ic, FrameLayout.LayoutParams(dpi(24), dpi(24), Gravity.CENTER))
            }
            bar.addView(cell, Ui.lp(0, dpi(46), 1f))
            return ic
        }
        scanIcon = tool(ToolIcon.Kind.SCAN, "Khung chọn vùng phụ đề") { toggleAdjust() }
        subsIcon = tool(ToolIcon.Kind.SUBS, "Bật hoặc tắt dịch phụ đề") { toggleTranslate() }
        speakIcon = tool(ToolIcon.Kind.SPEAKER_ON, "Đọc to bản dịch") { toggleSpeak() }
        tool(ToolIcon.Kind.TEXT_SIZE, "Cỡ chữ phụ đề") { showFontDialog() }
        tool(ToolIcon.Kind.DIAL, "Tốc độ quét phụ đề") { showScanDialog() }
        tool(ToolIcon.Kind.ZOOM, "Phóng to hình") { toggleZoom() }
        tool(ToolIcon.Kind.ROTATE, "Xoay ngang hoặc dọc") {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        tool(ToolIcon.Kind.GEAR, "Cài đặt chất lượng hình") { PsQuality.sheet(this, null) { reconnect() } }
        tool(ToolIcon.Kind.HELP, "Trợ giúp") { help() }
        syncTools()
        return bar
    }

    /** Vẽ lại trạng thái các nút công cụ (bật = vàng có nền sáng, tắt = trắng/xám). */
    private fun syncTools() {
        val running = live?.running == true
        val adjusting = live?.isAdjusting == true
        val sp = Prefs(this).speak
        fun mark(ic: ToolIcon?, kind: ToolIcon.Kind, on: Boolean, offColor: Int = Ui.GOLD) {
            ic ?: return
            ic.set(kind, if (on) Ui.GOLD else offColor)
            (ic.parent as? View)?.background = if (on) Ui.bg(this, Color.parseColor("#33F2B84B"), 16) else null
        }
        mark(scanIcon, ToolIcon.Kind.SCAN, adjusting)
        mark(subsIcon, ToolIcon.Kind.SUBS, running)
        mark(speakIcon, if (sp) ToolIcon.Kind.SPEAKER_ON else ToolIcon.Kind.SPEAKER_OFF, sp, Ui.SUB)
        padIcon?.set(ToolIcon.Kind.GAMEPAD, if (padOn) Ui.GOLD else Ui.SUB)
    }

    private fun togglePad() {
        padOn = !padOn
        pad?.visibility = if (padOn && live?.isAdjusting != true) View.VISIBLE else View.GONE
        if (!padOn) pad?.releaseAll()
        syncTools()
    }

    private fun toggleTools() {
        toolsOpen = !toolsOpen
        toolsBar?.visibility = if (toolsOpen) View.VISIBLE else View.GONE
    }

    private val zooms = floatArrayOf(1f, 1.25f, 1.5f)

    private fun applyZoom() {
        val f = vidFrame ?: return
        val z = zooms[zoomLevel.coerceIn(0, zooms.size - 1)]
        f.post {
            f.pivotX = f.width / 2f
            f.pivotY = f.height / 2f
            f.scaleX = z
            f.scaleY = z
        }
    }

    private fun toggleZoom() {
        zoomLevel = (zoomLevel + 1) % zooms.size
        applyZoom()
        Toast.makeText(this, if (zoomLevel == 0) "Hình vừa khung." else "Phóng hình ×" + zooms[zoomLevel], Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeak() {
        val pr = Prefs(this)
        pr.speak = !pr.speak
        live?.setSpeak(pr.speak)
        syncTools()
        Toast.makeText(this, if (pr.speak) "Đọc to bản dịch: bật." else "Đọc to bản dịch: tắt (chỉ hiện chữ).", Toast.LENGTH_SHORT).show()
    }

    private fun showFontDialog() {
        val a = this
        val pr = Prefs(a)
        Ui.sheet(a) { dlg ->
            addView(Ui.tv(a, "Cỡ chữ phụ đề", 18f, Ui.TEXT, true))
            addView(Ui.seekRow(a, "Cỡ chữ", { v -> "$v%" }, 50, 200, pr.subScale) { pr.subScale = it; live?.restyle() }, Ui.lpm(a, 10, 8))
            addView(Ui.button(a, "Xong", true) { dlg.dismiss() }, Ui.lpm(a, 8, 0))
        }
    }

    private fun showScanDialog() {
        val a = this
        val pr = Prefs(a)
        Ui.sheet(a) { dlg ->
            val box = this
            fun fill() {
                box.removeAllViews()
                box.addView(Ui.tv(a, "Tốc độ đọc phụ đề", 18f, Ui.TEXT, true))
                val rates = listOf(3, 4, 6, 8)
                val sel = rates.indexOfFirst { abs(1000 / it - pr.scanMs) <= 20 }
                box.addView(Ui.tv(a, "Mỗi giây quét mấy lần", 15f, Ui.TEXT, true), Ui.lpm(a, 14, 0))
                box.addView(Ui.segmented(a, rates.map { it.toString() }, sel) { pr.scanMs = 1000 / rates[it]; fill() }, Ui.lpm(a, 6, 0))
                box.addView(Ui.tv(a, "Quét nhiều đỡ sót câu ngắn nhưng máy nóng và hao pin hơn.", 12.5f, Ui.SUB), Ui.lpm(a, 6, 0))
                box.addView(Ui.tv(a, "Cách đọc chữ trên hình", 15f, Ui.TEXT, true), Ui.lpm(a, 14, 0))
                box.addView(Ui.segmented(a, listOf("Kỹ", "Nhanh"), if (pr.slow) 0 else 1) { pr.slow = it == 0; fill() }, Ui.lpm(a, 6, 0))
                box.addView(Ui.tv(a, "Kỹ: chờ chữ hiện đủ rồi mới dịch (áp dụng từ lần bật dịch sau). Nhanh: dịch ngay khi thấy chữ.", 12.5f, Ui.SUB), Ui.lpm(a, 6, 0))
                box.addView(Ui.button(a, "Xong", true) { dlg.dismiss() }, Ui.lpm(a, 14, 0))
            }
            fill()
        }
    }

    private fun setStatus(text: String, show: Boolean) {
        val lines = text.lines()
        statusView?.text = lines.firstOrNull().orEmpty()
        val rest = lines.drop(1).joinToString("\n")
        statusSub?.text = rest
        statusSub?.visibility = if (rest.isEmpty()) View.GONE else View.VISIBLE
        statusCard?.visibility = if (show) View.VISIBLE else View.GONE
        chipView?.text = if (connected) "● Đã kết nối · " + PsQuality.summary(this) else "● " + lines.first()
        chipView?.setTextColor(if (connected) Ui.OK else Ui.GOLD)
    }

    private fun refreshStatus() {
        if (connected) setStatus("", false)
        else if (manual) setStatus("Đã ngắt kết nối", true)
        else if (session == null) setStatus("Đang tìm máy…\nMáy chơi game cần đang bật (hoặc chế độ nghỉ) và cùng Wi-Fi/LAN với điện thoại.", true)
        else setStatus("Đang kết nối…", true)
        syncTools()
    }

    private fun help() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Mẹo khi chơi")
            .setMessage(
                "Thanh công cụ, từ trái sang phải:\n" +
                    "⛶  Khung chọn vùng phụ đề: kéo khung tới đúng dòng phụ đề của game, bấm lần nữa hoặc ✓ Xong để ẩn khung.\n" +
                    "▭  Bật hoặc tắt dịch phụ đề ngay trên hình game.\n" +
                    "🔊  Đọc to bản dịch (bật/tắt).\n" +
                    "A  Cỡ chữ phụ đề.\n" +
                    "◔  Tốc độ quét: quét nhiều đỡ sót câu nhưng hao pin hơn.\n" +
                    "⊕  Phóng to hình.\n" +
                    "⟳  Xoay ngang để xem hình lớn, tay cầm trong suốt phủ lên hình.\n" +
                    "⚙  Chất lượng hình, áp dụng bằng cách kết nối lại.\n\n" +
                    "• Nút tay cầm ở góc trên: ẩn hoặc hiện tay cầm ảo (tay cầm thật nối với điện thoại vẫn dùng được).\n" +
                    "• Ô người chơi ở góc trên bên phải: đổi sang tài khoản đã ghép khác.\n" +
                    "• Thanh nhỏ dưới hình: thu gọn hoặc mở thanh công cụ.\n" +
                    "• Máy báo đang bận: tắt Chơi từ xa ở thiết bị khác, đợi 30 giây rồi thử lại.\n" +
                    "• Hình giật: dùng Wi-Fi 5 GHz hoặc dây LAN cho máy chơi game, hạ chất lượng ở ⚙."
            )
            .setPositiveButton("Đã hiểu", null).show()
    }

    // ---------------- Kết nối ----------------

    private fun hasUsableNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        // Không chỉ kiểm tra activeNetwork: Android có thể ưu tiên 4G/5G làm mạng mặc định
        // khi Wi-Fi không có Internet, dù Wi-Fi nội bộ vẫn kết nối trực tiếp tới PS5/PS4.
        // Remote Play trong LAN không cần Wi-Fi phải có Internet.
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
    }

    private fun requestConnect() {
        if (console == null || manual || isFinishing || isDestroyed) return
        if (!hasUsableNetwork()) {
            reconnectPending = true
            setStatus("Đang chờ Wi-Fi hoặc mạng di động…", true)
            return
        }
        if (stopping) {
            reconnectPending = true
            return
        }
        reconnectPending = false
        connect()
    }

    private fun connect() {
        val c = console ?: return
        if (stopping) {
            reconnectPending = true
            return
        }
        if (session != null) return
        val g = ++gen
        manual = false
        connected = false
        refreshStatus()
        CrashLog.mark("choi:ket-noi")
        var candidate: Session? = null
        var candidateAudioQueue: GameAudioPcmQueue? = null
        try {
            val info = PsStore.connectInfo(this, c, player)
            val s = Session(info, null, false)
            candidate = s
            // Native Chiaki invokes this on its decoder thread. offer() only copies into
            // a bounded queue and never performs ASR, translation, or UI work here.
            val audioQueue = GameAudioPcmQueue()
            candidateAudioQueue = audioQueue
            s.audioFrameCallback = { samples, sampleRate, channels ->
                audioQueue.offer(samples, sampleRate, channels)
            }
            s.eventCallback = { ev -> runOnUiThread { if (g == gen && !isFinishing && !isDestroyed) onEvent(ev) } }
            val r = s.start()
            if (!r.isSuccess) {
                // start() thất bại vẫn phải dừng native session trước khi giải phóng,
                // giống cùng thứ tự stop -> dispose dùng cho phiên đang chạy.
                s.audioFrameCallback = null
                try { s.stop() } catch (_: Throwable) {}
                try { s.dispose() } catch (_: Throwable) {}
                candidateAudioQueue?.close()
                candidateAudioQueue = null
                candidate = null
                val message = "Không khởi động được phiên Remote Play: $r"
                if (activityStarted && !isFinishing && !isDestroyed) retryAfterNetworkDrop(message)
                else ended(message, true)
                return
            }
            CrashLog.mark("choi:phien-da-mo")
            session = s
            gameAudioPcmQueue = candidateAudioQueue
            candidateAudioQueue = null
            candidate = null
            val modelDirectory = java.io.File(filesDir, "whisper-tiny")
            if (WhisperTinyModelInstaller(modelDirectory).isInstalled()) {
                val worker = GameAudioAsrWorker(
                    queue = gameAudioPcmQueue!!,
                    modelDirectory = modelDirectory,
                    language = Prefs(this).lang,
                    onTranscript = { text -> routeAudio(text, 0L, 1f) },
                    onTranscriptMeta = { text, ms, rms -> routeAudio(text, ms, rms) },
                    onError = { message ->
                        ui.post {
                            if (!isFinishing && !isDestroyed) {
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
                gameAudioAsrWorker = worker
                worker.start()
            }
            surface?.let { s.setSurface(it) }
            push()
        } catch (e: Throwable) {
            // start() có thể ném lỗi trước khi Session được đưa vào biến session; giải phóng ứng viên để tránh rò tài nguyên native.
            candidate?.let { it.audioFrameCallback = null; try { it.stop() } catch (_: Throwable) {}; try { it.dispose() } catch (_: Throwable) {} }
            candidateAudioQueue?.close()
            candidateAudioQueue = null
            val message = "Lỗi mở Remote Play: ${e.message ?: e.javaClass.simpleName}"
            if (activityStarted && !isFinishing && !isDestroyed) retryAfterNetworkDrop(message)
            else ended(message, true)
        }
    }

    private fun onEvent(ev: Event) {
        when (ev) {
            is ConnectedEvent -> {
                CrashLog.mark("choi:da-ket-noi")
                connected = true
                if (wantTranslate) ui.post { if (!isFinishing && !isDestroyed && live?.running != true) startTranslate() }
                reconnectAttempt = 0
                reconnectPending = false
                reconnectRunnable?.let(ui::removeCallbacks)
                reconnectRunnable = null
                setStatus("", false)
                push()
            }
            is LoginPinRequestEvent -> askPin(ev.pinIncorrect)
            is QuitEvent -> {
                val why = ev.reason.toString() + (ev.reasonString?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                val message = if (ev.reason.isError) "Mất kết nối: $why" else "Phiên đã kết thúc: $why"
                if (ev.reason.isError && activityStarted && !isFinishing && !isDestroyed) {
                    retryAfterNetworkDrop(message)
                } else {
                    shutdownSession()
                    ended(message, ev.reason.isError)
                }
            }
            is RumbleEvent -> {}
        }
    }

    /**
     * Transport đã đóng thì không thể giữ nguyên luồng video; tạo lại phiên Remote Play
     * với backoff tối đa 5 lần để tránh retry storm khi PS tắt hoặc không thể truy cập.
     */
    private fun retryAfterNetworkDrop(message: String) {
        val delay = RemotePlayRetryPolicy.delayForAttempt(reconnectAttempt)
        if (delay == null) {
            shutdownSession()
            ended(message + "\n\nĐã thử tự kết nối lại " + RemotePlayRetryPolicy.maxAttempts + " lần nhưng chưa thành công.", true)
            return
        }
        reconnectAttempt++
        manual = false
        reconnectPending = true
        pendingReconnectDelayMs = delay
        setStatus("Mạng bị gián đoạn · thử lại lần " + reconnectAttempt + "/" + RemotePlayRetryPolicy.maxAttempts + " sau " + (delay / 1000) + " giây…", true)
        shutdownSession()
    }

    private fun ended(message: String, error: Boolean) {
        connected = false
        manual = true
        refreshStatus()
        setStatus(message, true)
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(if (error) "Không vào được game" else "Đã ngắt kết nối")
            .setMessage(message + if (error)
                "\n\nThử: tắt Chơi từ xa ở thiết bị khác rồi đợi 30 giây; kiểm tra máy chơi game đã bật và cùng Wi-Fi; nếu vẫn lỗi, xóa ghép nối cũ trên máy chơi game rồi ghép lại." else "")
            .setCancelable(false)
            .setPositiveButton("Kết nối lại") { _, _ -> reconnect() }
            .setNegativeButton("Thoát") { _, _ -> finish() }
            .show()
    }

    /** Lên lịch kết nối lại sau khi phiên cũ đã dừng, hoặc ngay nếu chưa tạo được phiên. */
    private fun schedulePendingReconnect() {
        if (!reconnectPending || !activityStarted || manual || isFinishing || isDestroyed) return
        val delay = pendingReconnectDelayMs
        pendingReconnectDelayMs = 0L
        reconnectRunnable?.let(ui::removeCallbacks)
        val retry = Runnable {
            reconnectRunnable = null
            if (reconnectPending && activityStarted && !manual && !isFinishing && !isDestroyed) {
                if (hasUsableNetwork()) requestConnect()
                else setStatus("Đang chờ mạng trở lại…", true)
            }
        }
        reconnectRunnable = retry
        if (delay > 0L) ui.postDelayed(retry, delay) else ui.post(retry)
    }

    private fun shutdownSession() {
        gen++
        connected = false
        val s = session
        if (s == null) {
            // Lỗi xảy ra trước khi Session được gán (ví dụ start() trả lỗi):
            // vẫn phải chạy lịch retry thay vì mắc kẹt ở trạng thái chờ.
            schedulePendingReconnect()
            return
        }
        session = null
        stopping = true
        // Detach first so no new PCM chunks enter while the native session is stopping.
        s.audioFrameCallback = null
        val audioQueue = gameAudioPcmQueue
        gameAudioPcmQueue = null
        val asrWorker = gameAudioAsrWorker
        gameAudioAsrWorker = null
        Thread {
            // Detaching the callback prevents future delivery, but a JNI callback may
            // already be in flight. Stop and join Chiaki first so no producer can enqueue
            // PCM after the ASR worker has finished draining the queue.
            try { s.stop() } catch (_: Throwable) {}
            try { s.dispose() } catch (_: Throwable) {}
            // The worker owns copied PCM and may still be decoding a long segment.
            // Wait off the UI thread, then close the queue only after the final drain.
            try { asrWorker?.stopAndJoin(0L) } catch (_: Throwable) {}
            try { audioQueue?.close() } catch (_: Throwable) {}
            ui.post {
                stopping = false
                // Không tạo phiên mới cho tới khi stop() và dispose() cũ hoàn tất.
                schedulePendingReconnect()
            }
        }.start()
    }

    private fun reconnect() {
        manual = false
        reconnectAttempt = 0
        reconnectRunnable?.let(ui::removeCallbacks)
        reconnectRunnable = null
        pendingReconnectDelayMs = 0L
        reconnectPending = true
        refreshStatus()
        if (stopping) return
        if (session != null) shutdownSession() else requestConnect()
    }

    private fun leave() {
        if (session == null) { finish(); return }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Thoát phiên Remote Play?")
            .setMessage("Hình và tay cầm sẽ dừng. Máy đã ghép nối vẫn được lưu để kết nối lại nhanh.")
            .setNegativeButton("Tiếp tục chơi", null)
            .setPositiveButton("Thoát") { _, _ -> finish() }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = leave()

    private fun askPin(incorrect: Boolean) {
        val et = EditText(this).apply {
            hint = "Mã đăng nhập Remote Play"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(8))
        }
        val box = Ui.vbox(this)
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0)
        box.addView(et)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(if (incorrect) "Mã chưa đúng" else "Nhập mã đăng nhập")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Hủy") { _, _ -> finish() }
            .setPositiveButton("Gửi") { _, _ ->
                val v = et.text.toString()
                if (!isValidPairingPin(v)) { Toast.makeText(this, "Mã đăng nhập phải gồm đúng 8 chữ số (0–9).", Toast.LENGTH_LONG).show(); askPin(incorrect) }
                else session?.setLoginPin(v)
            }.show()
    }

    // ---------------- Hình và điều khiển ----------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        activeSurfaceHolder = holder
        surface = holder.surface
        session?.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Callback từ SurfaceView cũ có thể đến trễ sau khi build() thay giao diện lúc xoay màn hình.
        if (activeSurfaceHolder !== holder) return
        surface = holder.surface
        session?.setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Không để holder cũ tháo surface mới đã được gắn vào phiên Remote Play.
        if (activeSurfaceHolder !== holder) return
        activeSurfaceHolder = null
        surface = null
        session?.setSurface(null)
    }

    private fun push() {
        try { session?.setControllerState(padState or phys) } catch (_: Throwable) {}
    }

    private fun isPad(src: Int) = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

    private fun keyMask(code: Int): UInt = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerState.BUTTON_CROSS
        KeyEvent.KEYCODE_BUTTON_B -> ControllerState.BUTTON_MOON
        KeyEvent.KEYCODE_BUTTON_X -> ControllerState.BUTTON_BOX
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerState.BUTTON_PYRAMID
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
        KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerState.BUTTON_SHARE
        KeyEvent.KEYCODE_BUTTON_MODE -> ControllerState.BUTTON_PS
        KeyEvent.KEYCODE_DPAD_UP -> ControllerState.BUTTON_DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> ControllerState.BUTTON_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> ControllerState.BUTTON_DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerState.BUTTON_DPAD_RIGHT
        else -> 0u
    }

    private fun updatePhysicalTriggers() {
        phys.l2State = (maxOf(if (physicalL2Digital) 1f else 0f, physicalL2Analog) * 255f).toInt().coerceIn(0, 255).toUByte()
        phys.r2State = (maxOf(if (physicalR2Digital) 1f else 0f, physicalR2Analog) * 255f).toInt().coerceIn(0, 255).toUByte()
    }

    private fun acceptsPhysicalDevice(deviceId: Int): Boolean {
        val active = activePhysicalDeviceId
        if (active == null) {
            activePhysicalDeviceId = deviceId
            return true
        }
        return active == deviceId
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_BACK && isPad(e.source) && acceptsPhysicalDevice(e.deviceId)) {
            val down = e.action == KeyEvent.ACTION_DOWN
            when (e.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L2 -> { physicalL2Digital = down; updatePhysicalTriggers(); push(); return true }
                KeyEvent.KEYCODE_BUTTON_R2 -> { physicalR2Digital = down; updatePhysicalTriggers(); push(); return true }
            }
            val m = keyMask(e.keyCode)
            if (m != 0u) {
                val dpadMask = ControllerState.BUTTON_DPAD_LEFT or ControllerState.BUTTON_DPAD_RIGHT or
                    ControllerState.BUTTON_DPAD_UP or ControllerState.BUTTON_DPAD_DOWN
                if ((m and dpadMask) != 0u) {
                    physicalDpadKeys = if (down) physicalDpadKeys or m else physicalDpadKeys and m.inv()
                    updatePhysicalDpad()
                } else {
                    phys.buttons = if (down) phys.buttons or m else phys.buttons and m.inv()
                }
                push()
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if (isPad(e.source) && e.actionMasked == MotionEvent.ACTION_MOVE && acceptsPhysicalDevice(e.deviceId)) {
            // Dead zone tròn cho mỗi cần giúp hướng chéo không bị tăng lực quá mức.
            fun flat(a: Int): Float = e.device?.getMotionRange(a, e.source)?.flat ?: 0.1f
            fun hasAxis(a: Int): Boolean = e.device?.getMotionRange(a, e.source) != null
            fun preferredAxis(primary: Int, fallback: Int): Int =
                if (hasAxis(primary)) primary else fallback
            val left = AnalogAxis.normalizePair(
                e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y),
                flat(MotionEvent.AXIS_X), flat(MotionEvent.AXIS_Y)
            )
            phys.leftX = (left.first * Short.MAX_VALUE).toInt().toShort()
            phys.leftY = (left.second * Short.MAX_VALUE).toInt().toShort()
            // Android có tay cầm xuất cần phải qua Z/RZ, có loại lại dùng RX/RY.
            val rightX = preferredAxis(MotionEvent.AXIS_Z, MotionEvent.AXIS_RX)
            val rightY = preferredAxis(MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY)
            val right = AnalogAxis.normalizePair(
                e.getAxisValue(rightX), e.getAxisValue(rightY),
                flat(rightX), flat(rightY)
            )
            phys.rightX = (right.first * Short.MAX_VALUE).toInt().toShort()
            phys.rightY = (right.second * Short.MAX_VALUE).toInt().toShort()
            fun trigger(a: Int): Float {
                val range = e.device?.getMotionRange(a, e.source)
                return TriggerAxis.normalize(
                    e.getAxisValue(a),
                    range?.flat ?: 0f,
                    range?.min ?: 0f,
                    range?.max ?: 1f
                )
            }
            physicalL2Analog = maxOf(trigger(MotionEvent.AXIS_LTRIGGER), trigger(MotionEvent.AXIS_BRAKE))
            physicalR2Analog = maxOf(trigger(MotionEvent.AXIS_RTRIGGER), trigger(MotionEvent.AXIS_GAS))
            updatePhysicalTriggers()
            val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
            var hat = 0u
            if (hx < -0.5f) hat = hat or ControllerState.BUTTON_DPAD_LEFT
            if (hx > 0.5f) hat = hat or ControllerState.BUTTON_DPAD_RIGHT
            if (hy < -0.5f) hat = hat or ControllerState.BUTTON_DPAD_UP
            if (hy > 0.5f) hat = hat or ControllerState.BUTTON_DPAD_DOWN
            physicalDpadHat = hat
            updatePhysicalDpad()
            push()
            return true
        }
        return super.dispatchGenericMotionEvent(e)
    }

    // ---------------- Dịch ----------------

    private fun routeAudio(text: String, ms: Long, rms: Float) {
        val l = live
        if (l != null && l.running) l.acceptAudio(text, ms, rms)
        else CaptureService.submitAudioTranscript(this, text, ms, rms)
    }

    private fun ensureLive(): LiveSubs {
        var l = live
        if (l == null) { l = LiveSubs(this) { videoView }; live = l }
        return l
    }

    /** Bật/tắt dịch phụ đề ngay trong màn chơi: hình lấy thẳng từ video Remote Play, không cần ghi màn hình. */
    private fun toggleTranslate() {
        try {
            val l = ensureLive()
            if (l.running) {
                wantTranslate = false
                l.stop()
                syncTools()
                Toast.makeText(this, "Đã tắt dịch phụ đề.", Toast.LENGTH_SHORT).show()
                return
            }
            wantTranslate = true
            startTranslate()
        } catch (e: Throwable) {
            CrashLog.mark("choi:dich-loi " + e.javaClass.simpleName)
            syncTools()
            Toast.makeText(this, "Không bật được dịch phụ đề: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTranslate() {
        try {
            val l = ensureLive()
            if (l.running) return
            if (l.isAdjusting) toggleAdjust()
            vidFrame?.let { l.attach(it) }
            Prefs(this).playOn = if (mode == "tv") "tv" else "phone"
            l.start()
            syncTools()
            if (l.running) Toast.makeText(this, "Đang dịch phụ đề ngay trong màn chơi. Bật phụ đề trong game, bấm nút khung (⛶) để kéo khung tới đúng dòng phụ đề.", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            CrashLog.mark("choi:dich-loi " + e.javaClass.simpleName)
            syncTools()
            Toast.makeText(this, "Không bật được dịch phụ đề: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    /** Chọn khung đọc phụ đề: tay cầm ảo tạm ẩn để kéo khung cho dễ, bấm lần nữa để xong. */
    private fun toggleAdjust() {
        try {
            val l = ensureLive()
            vidFrame?.let { l.attach(it) }
            val on = !l.isAdjusting
            l.setAdjusting(on)
            pad?.visibility = if (!on && padOn) View.VISIBLE else View.GONE
            if (on) pad?.releaseAll()
            syncTools()
            if (demo) android.util.Log.i("DG_PLAY", "chinh khung on=$on pad=${pad?.visibility} act=${System.identityHashCode(this)} vid=${System.identityHashCode(vidFrame)}")
            Toast.makeText(
                this,
                if (on) "Kéo khung tới đúng chỗ phụ đề của game, kéo chấm tròn để đổi cỡ. Bấm ⛶ lần nữa hoặc ✓ Xong để ẩn khung."
                else "Đã chọn khung. Khung ẩn đi khi đang dịch.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Không mở được khung chọn vùng: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}
