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
        /** Xoay ngang: thanh công cụ tự thu lại sau chừng này mili giây không chạm. */
        private const val TOOLS_IDLE_MS = 5000L
        /** Xoay dọc: lâu hơn một chút, và tính lại mỗi lần chạm tay cầm ảo (thanh thu lại thì tay cầm dời lên nên không thu khi đang chơi). */
        private const val TOOLS_IDLE_PORTRAIT_MS = 8000L
        /** Sau một thao tác (bật dịch, đóng bảng cài đặt…) thanh công cụ thu lại sau chừng này mili giây. */
        private const val TOOLS_ACTION_MS = 1300L
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
    private var adjustBar: View? = null
    // Hai nút "Dịch hết chữ" (một lần) và "Dịch menu" (liên tục) dưới thanh công cụ
    private class PillUi(val box: View, val icon: ToolIcon, val label: TextView, val kind: ToolIcon.Kind)
    private var pillOnce: PillUi? = null
    private var pillMenu: PillUi? = null
    private var pillsView: View? = null
    private var handleView: View? = null
    private var homeCell: View? = null
    private var speakIcon: ToolIcon? = null
    private var padIcon: ToolIcon? = null
    private var toolsBar: View? = null
    // Thanh công cụ: mở khi vào màn chơi, tự thu lại khi chỉnh xong (sau một thao tác) hoặc khi để yên (chỉ lúc xoay ngang, vì
    // khi đó nó phủ lên hình). Thu lại rồi thì còn một chấm nhỏ ở giữa mép trên để mở lại.
    private var toolsOpen = true
    private val idleRun = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
        // xoay dọc: đang giữ nút hoặc cần gạt trên tay cầm ảo thì chưa thu (thu thì tay cầm dời chỗ dưới ngón tay)
        if (playRoot?.land == false && padHeld()) armIdle() else setTools(false)
    }
    private val actionRun = Runnable { if (!isFinishing && !isDestroyed) setTools(false) }
    private var zoomLevel = 0
    // Chỉ bản debug: dựng giao diện mà không kết nối máy thật (để chụp ảnh kiểm tra giao diện trên máy giả lập).
    private var demo = false
    // Dịch phụ đề chạy ngay trong màn chơi (chụp hình thẳng từ SurfaceView), không cần quyền ghi màn hình.
    @Volatile private var live: LiveSubs? = null
    private var videoView: SurfaceView? = null
    // Dịch chữ giao diện của game (menu, vật phẩm, kỹ năng, nhiệm vụ…) bằng lớp phủ lên hình, độc lập với dịch phụ đề.
    @Volatile private var uiTr: UiTranslate? = null
    private var wantUiTr = false
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
        if (demo) {
            when (demoMode) {
                "adjust" -> {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) toggleAdjust() }, 1500)
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) toggleAdjust() }, 5000)
                }
                "land" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "tools" -> {
                    // Thanh công cụ khi xoay ngang: mở → tự thu lại khi để yên → chạm chấm để mở lại → bấm "Dịch hết chữ" (thu lại
                    // sau thao tác, dịch một lần rồi tự tắt) → bấm nút PS. Bấm bằng performClick trên đúng các nút thật.
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    fun st(tag: String) = android.util.Log.i("DG_PLAY",
                        "tools-$tag open=$toolsOpen top=${playRoot?.close?.visibility == View.VISIBLE} handle=${handleView?.visibility == View.VISIBLE} land=${playRoot?.land == true}")
                    fun at(ms: Long, f: () -> Unit) { ui.postDelayed({ if (!isFinishing && !isDestroyed) f() }, ms) }
                    at(2000) { st("dau") }
                    at(9000) { st("sau-5s") }
                    at(14000) { handleView?.performClick() }
                    at(15000) { st("mo-lai") }
                    at(21000) { pillOnce?.box?.performClick() }
                    at(21500) { android.util.Log.i("DG_PLAY", "mot-lan chay=${uiTr?.running} once=${uiTr?.once}") }
                    at(23500) { st("sau-bam") }
                    at(24000) { homeCell?.performClick() }
                }
                "toolsp" -> {
                    // Xoay dọc: thanh tự thu lại khi để yên (nút kéo giữa màn hình mở lại được), cả khi chưa kết nối máy chơi game
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    fun st(tag: String) = android.util.Log.i("DG_PLAY",
                        "toolsp-$tag open=$toolsOpen bar=${barView?.visibility == View.VISIBLE} land=${playRoot?.land == true} connected=$connected")
                    fun at(ms: Long, f: () -> Unit) { ui.postDelayed({ if (!isFinishing && !isDestroyed) f() }, ms) }
                    at(2500) { st("dau") }
                    at(13000) { st("sau-8s") }
                    at(14000) { gripView?.performClick() }
                    at(15000) { st("mo-lai") }
                    at(25000) { st("sau-8s-lan-2") }
                }
                "adjust2" -> {
                    // Chỉnh khung: kéo quá to → bấm Đặt lại → bấm Xong (bấm đúng các nút thật), ghi nhật ký từng bước
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) toggleAdjust() }, 1200)
                    ui.postDelayed({
                        if (!isFinishing && !isDestroyed) live?.box?.let { b ->
                            b.fl = 0.0f; b.ft = 0.02f; b.fr = 1f; b.fb = 1f; b.invalidate()
                            android.util.Log.i("DG_PLAY", "to khung=${b.save()} thanh=${adjustBar?.visibility == View.VISIBLE}")
                        }
                    }, 2200)
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) ((adjustBar as? ViewGroup)?.getChildAt(1))?.performClick() }, 12000)
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) ((adjustBar as? ViewGroup)?.getChildAt(2))?.performClick() }, 20000)
                    ui.postDelayed({ android.util.Log.i("DG_PLAY", "sau-xong dang-chinh=${live?.isAdjusting} thanh=${adjustBar?.visibility == View.VISIBLE}") }, 20700)
                }
                "settings" -> {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    // Cho CI chụp các dạng khác nhau của bảng (chế độ + loại giọng) mà không phải bấm tay
                    intent.getStringExtra("dg_engine")?.let { Prefs(this).voiceEngine = it }
                    if (intent.hasExtra("dg_narrate")) Prefs(this).narrate = intent.getBooleanExtra("dg_narrate", false)
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) showSettings() }, 1500)
                }
                "uitr" -> {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    // dựng lớp phủ trước, đợi nó được bố cục xong rồi mới vẽ (bố cục lần đầu xoá bản dịch cũ)
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) ensureUiTr() }, 800)
                    ui.postDelayed({ if (!isFinishing && !isDestroyed) demoUiOverlay() }, 1800)
                }
                // Xoay liên tiếp dọc ↔ ngang: CI kiểm tra Activity và SurfaceView không bị tạo lại.
                "spin" -> {
                    val seq = listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    seq.forEachIndexed { i, o -> ui.postDelayed({ if (!isFinishing && !isDestroyed) requestedOrientation = o }, 1500L * (i + 1)) }
                }
                else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else PsStore.applyOrient(this)
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
        uiTr?.let { u -> if (u.running) { u.stop(); syncTools() } }
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
        runCatching { uiTr?.release() }
        uiTr = null
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
        // Chỉ đổi cách xếp chỗ; SurfaceView, phiên Remote Play và tay cầm ảo giữ nguyên.
        // Ngón đang giữ nút bị hệ thống huỷ khi xoay: nhả hết để không kẹt nút.
        pad?.releaseAll()
        applyOrientation()
        if (demo) android.util.Log.i("DG_PLAY", "xoay land=${n.orientation == Configuration.ORIENTATION_LANDSCAPE} act=${System.identityHashCode(this)} sv=${System.identityHashCode(videoView)}")
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

    private var playRoot: PlayLayout? = null
    private var vcardView: FrameLayout? = null
    private var barView: View? = null
    private var chipHolder: View? = null
    private var gripView: View? = null
    private var pillLabel: TextView? = null
    private var viewsBuilt = false

    /**
     * Xếp chỗ cho các phần của màn chơi. Mọi view (SurfaceView, hình, tay cầm ảo, nút) chỉ được tạo MỘT lần; xoay màn hình chỉ
     * đổi cách xếp chỗ ở đây. Trước đây mỗi lần xoay là dựng lại toàn bộ giao diện và tạo SurfaceView mới, nên surface cũ bị huỷ,
     * bộ giải mã hình bị tháo ra (có lúc treo hẳn) và tay cầm ảo bị thay nên có thể kẹt nút.
     */
    private inner class PlayLayout(c: Context) : ViewGroup(c) {
        var land = false
        var vcard: View? = null
        var pad: View? = null
        var close: View? = null
        var padBtn: View? = null
        var chip: View? = null
        var pill: View? = null
        var bar: View? = null
        var grip: View? = null
        /** Hàng hai nút "Dịch hết chữ" và "Dịch menu" nằm dưới thanh công cụ. */
        var pills: View? = null
        /** Chấm nhỏ ở giữa mép trên (chỉ khi xoay ngang và thanh công cụ đã thu lại) để mở lại thanh. */
        var handle: View? = null
        /** Thanh "Xong / Đặt lại" hiện khi đang chỉnh khung đọc phụ đề. */
        var adj: View? = null

        override fun onMeasure(w: Int, h: Int) {
            setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
        }

        private fun measureChild(v: View, w: Int, wMode: Int, h: Int, hMode: Int) =
            v.measure(MeasureSpec.makeMeasureSpec(maxOf(0, w), wMode), MeasureSpec.makeMeasureSpec(maxOf(0, h), hMode))

        private fun put(v: View, x: Int, y: Int) = v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val vc = vcard ?: return
            val pd = pad ?: return
            val cl = close ?: return
            val pb = padBtn ?: return
            val pl = pill ?: return
            val ch = chip ?: return
            val br = bar ?: return
            val gr = grip ?: return
            val ps = pills ?: return
            val hd = handle ?: return
            val w = r - l
            val h = b - t
            val ex = MeasureSpec.EXACTLY
            val am = MeasureSpec.AT_MOST
            val m8 = dpi(8)
            val m10 = dpi(10)
            val btn = dpi(40)
            measureChild(cl, btn, ex, btn, ex)
            measureChild(pb, btn, ex, btn, ex)
            measureChild(pl, w, am, h, am)
            if (!land) {
                // Dọc: hàng trên (đóng, tay cầm, trạng thái, người chơi), hình trong khung vàng, thanh công cụ, hai nút dịch,
                // thanh kéo, tay cầm ảo.
                val pillLeft = w - m10 - pl.measuredWidth
                val chipLeft = m10 + btn * 2 + m8 * 2
                measureChild(ch, pillLeft - m8 - chipLeft, ex, h, am)
                val rowTop = dpi(8)
                val rowH = maxOf(btn, ch.measuredHeight, pl.measuredHeight)
                fun cy(v: View) = rowTop + (rowH - v.measuredHeight) / 2
                put(cl, m10, cy(cl)); put(pb, m10 + btn + m8, cy(pb)); put(ch, chipLeft, cy(ch)); put(pl, pillLeft, cy(pl))
                var y = rowTop + rowH + dpi(6)
                measureChild(vc, w - 2 * m10, ex, 0, MeasureSpec.UNSPECIFIED)
                put(vc, m10, y); y += vc.measuredHeight
                if (br.visibility != View.GONE) {
                    y += m10
                    measureChild(br, w - 2 * m10, ex, h, am)
                    put(br, m10, y); y += br.measuredHeight
                }
                if (ps.visibility != View.GONE) {
                    y += dpi(8)
                    measureChild(ps, w - 2 * m10, ex, h, am)
                    put(ps, m10, y); y += ps.measuredHeight
                }
                measureChild(gr, w, ex, dpi(22), ex)
                put(gr, 0, y); y += dpi(22)
                // đang chỉnh khung phụ đề: tay cầm ảo đã ẩn nên thanh Xong/Đặt lại nằm đúng chỗ đó
                adj?.takeIf { it.visibility != View.GONE }?.let { av -> measureChild(av, w - 2 * m10, ex, h, am); put(av, m10, y + dpi(4)) }
                measureChild(pd, w, ex, h - y, ex)
                put(pd, 0, y)
                measureChild(hd, 0, ex, 0, ex); put(hd, 0, 0)
            } else {
                // Ngang: hình giữa màn hình, tay cầm trong suốt phủ lên. Thanh công cụ nằm phủ trên hình: hàng trên có
                // [đóng][tay cầm][người chơi] bên trái và hàng icon bên phải, hai nút dịch ngay dưới hàng icon (bên phải).
                // Thu lại rồi thì chỉ còn chấm nhỏ giữa mép trên.
                measureChild(vc, w, am, h, am)
                put(vc, (w - vc.measuredWidth) / 2, (h - vc.measuredHeight) / 2)
                measureChild(pd, w, ex, h, ex)
                put(pd, 0, 0)
                val open = br.visibility != View.GONE
                val rowTop = dpi(6)
                if (open) {
                    val leftW = btn * 2 + m8 * 2 + pl.measuredWidth
                    val barW = minOf(dpi(372), maxOf(dpi(240), w - 3 * m10 - leftW))
                    measureChild(br, barW, ex, h, am)
                    val rowH = maxOf(btn, br.measuredHeight, pl.measuredHeight)
                    fun cy(v: View) = rowTop + (rowH - v.measuredHeight) / 2
                    var x = m10
                    put(cl, x, cy(cl)); x += btn + m8
                    put(pb, x, cy(pb)); x += btn + m8
                    put(pl, x, cy(pl))
                    put(br, w - m10 - barW, cy(br))
                    measureChild(ps, w - 2 * m10, am, h, am)
                    put(ps, w - m10 - ps.measuredWidth, rowTop + rowH + dpi(8))
                    measureChild(hd, 0, ex, 0, ex); put(hd, 0, 0)
                } else {
                    measureChild(br, 0, ex, 0, ex); put(br, 0, 0)
                    measureChild(ps, 0, ex, 0, ex); put(ps, 0, 0)
                    measureChild(hd, dpi(72), ex, dpi(28), ex)
                    put(hd, (w - hd.measuredWidth) / 2, dpi(2))
                }
                // thanh Xong/Đặt lại nằm giữa mép dưới (tay cầm ảo đã ẩn khi đang chỉnh khung)
                adj?.takeIf { it.visibility != View.GONE }?.let { av ->
                    measureChild(av, minOf(w - 2 * m10, dpi(520)), ex, h, am)
                    put(av, (w - av.measuredWidth) / 2, h - av.measuredHeight - dpi(16))
                }
            }
            // Hai view chỉ dùng ở chế độ dọc: không đo, không xếp khi đang ngang (đã ẩn bằng visibility)
            if (land) { measureChild(ch, 0, ex, 0, ex); put(ch, 0, 0); measureChild(gr, 0, ex, 0, ex); put(gr, 0, 0) }
        }
    }

    @Suppress("DEPRECATION")
    private fun build() {
        if (!viewsBuilt) { createViews(); viewsBuilt = true }
        applyOrientation()
    }

    private fun createViews() {
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
        // Phóng hình quanh tâm khung, tính lại mỗi khi khung đổi kích thước (xoay màn hình).
        vid.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.pivotX = v.width / 2f
            v.pivotY = v.height / 2f
        }
        vidFrame = vid
        val vcard = FrameLayout(this)
        vcard.addView(vid, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        vcardView = vcard
        live?.attach(vid)
        uiTr?.attach(vid)

        val padV = PadView(this) { st ->
            padState = st; push()
            if (playRoot?.land == false) armIdle()   // đang chơi ở chế độ dọc: tính lại giờ để thanh không thu giữa chừng
        }
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
        chipHolder = chip

        val (closeBtn, _) = roundIcon(ToolIcon.Kind.CLOSE) { leave() }
        val (padBtn, padIc) = roundIcon(ToolIcon.Kind.GAMEPAD) { togglePad() }
        padIcon = padIc
        val pill = playerPill()
        val bar = toolbar()
        toolsBar = bar
        barView = bar
        val pills = pillsRow()
        pillsView = pills
        // Chấm mở lại thanh công cụ (chỉ hiện khi xoay ngang và thanh đã thu lại)
        val handle = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Mở thanh công cụ"
            background = Ui.bg(this@PsPlayActivity, Color.parseColor("#99000000"), 14, Color.parseColor("#55F2B84B"), 1)
            setOnClickListener { setTools(true) }
            addView(ToolIcon(this@PsPlayActivity, ToolIcon.Kind.CHEVRON, Ui.GOLD), FrameLayout.LayoutParams(dpi(22), dpi(22), Gravity.CENTER))
        }
        handleView = handle

        // Thanh kéo (chỉ ở chế độ dọc): bấm để thu gọn hoặc mở lại thanh công cụ, dành chỗ cho tay cầm.
        val grip = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Thu gọn hoặc mở thanh công cụ"
            setOnClickListener { setTools(!toolsOpen) }
            addView(View(this@PsPlayActivity).apply { background = Ui.bg(this@PsPlayActivity, Color.parseColor("#88F2B84B"), 3) },
                FrameLayout.LayoutParams(dpi(44), dpi(5), Gravity.CENTER))
        }
        gripView = grip

        // Thanh hiện khi đang chỉnh khung đọc phụ đề: kéo khung xong thì bấm Xong, lỡ kéo quá to hoặc lệch thì bấm Đặt lại.
        val adjBar = Ui.hbox(this).apply {
            setPadding(dpi(14), dpi(10), dpi(10), dpi(10))
            background = Ui.bg(this@PsPlayActivity, Color.parseColor("#F017120D"), 22, Color.parseColor("#66F2B84B"), 1)
            addView(Ui.tv(this@PsPlayActivity, "Kéo khung tới chỗ phụ đề, kéo chấm tròn để đổi cỡ.", 13f, Ui.SUB), Ui.lp(0, WRAP_CONTENT, 1f))
            addView(Ui.tv(this@PsPlayActivity, "Đặt lại", 15f, Ui.TEXT, true).apply {
                background = Ui.bg(this@PsPlayActivity, Ui.CARD2, 20, Ui.LINE, 1)
                setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
                contentDescription = "Đặt lại khung về dải phụ đề phía dưới"
                setOnClickListener { resetRegion() }
            }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(8) })
            addView(Ui.tv(this@PsPlayActivity, "Xong", 15f, Color.parseColor("#2A1A08"), true).apply {
                background = Ui.bg(this@PsPlayActivity, Ui.GOLD, 20)
                setPadding(dpi(20), dpi(10), dpi(20), dpi(10))
                contentDescription = "Xong, ẩn khung chọn vùng"
                setOnClickListener { if (live?.isAdjusting == true) toggleAdjust() }
            }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(8) })
            visibility = View.GONE
        }
        adjustBar = adjBar

        val root = PlayLayout(this)
        root.adj = adjBar
        root.vcard = vcard; root.pad = padV; root.close = closeBtn; root.padBtn = padBtn
        root.chip = chip; root.pill = pill; root.bar = bar; root.grip = grip; root.pills = pills; root.handle = handle
        // Thứ tự vẽ: hình, tay cầm phủ lên hình, rồi các nút điều khiển nằm trên cùng.
        root.addView(vcard); root.addView(padV); root.addView(closeBtn); root.addView(padBtn)
        root.addView(bar); root.addView(pills); root.addView(pill); root.addView(chip); root.addView(grip); root.addView(handle); root.addView(adjBar)
        playRoot = root
        setContentView(root)
    }

    /** Áp dụng kiểu dáng theo hướng màn hình hiện tại; không tạo lại view nào. */
    @Suppress("DEPRECATION")
    private fun applyOrientation() {
        val root = playRoot ?: return
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility =
            if (land) (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            else View.SYSTEM_UI_FLAG_VISIBLE
        root.land = land
        root.setBackgroundColor(if (land) Color.BLACK else Color.parseColor("#0B0F0C"))
        vcardView?.apply {
            if (land) {
                background = null
                setPadding(0, 0, 0, 0)
                clipToOutline = false
            } else {
                // Khung video viền vàng, bo góc; phóng hình thì phần thừa bị cắt trong khung.
                background = Ui.bg(this@PsPlayActivity, Color.BLACK, 16, Ui.GOLD, 2)
                setPadding(dpi(2), dpi(2), dpi(2), dpi(2))
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
        }
        barView?.background = if (land) Ui.bg(this, Color.parseColor("#E617120D"), 22, Color.parseColor("#44F2B84B"), 1)
        else Ui.bg(this, Color.parseColor("#17120D"), 24, Color.parseColor("#44F2B84B"), 1)
        chipHolder?.visibility = if (land) View.GONE else View.VISIBLE
        gripView?.visibility = if (land) View.GONE else View.VISIBLE
        vidFrame?.let { v -> live?.attach(v); uiTr?.attach(v) }
        applyZoom()
        applyToolsVisibility()
        armIdle()
        root.requestLayout()
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
        pillLabel = label
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
                pillLabel?.text = console?.players?.getOrNull(player)?.label ?: "Người chơi 1"
                if (!demo) reconnect()
            }
            afterAction(300)
        }
    }

    private fun toolbar(): LinearLayout {
        val bar = Ui.hbox(this)
        bar.setPadding(dpi(4), dpi(2), dpi(4), dpi(2))
        /** [solid]: ô vàng đặc, biểu tượng tối (nút PS). [collapse]: sau khi bấm, thanh tự thu lại. */
        fun tool(kind: ToolIcon.Kind, desc: String, solid: Boolean = false, collapse: Boolean = true, onClick: () -> Unit): Pair<View, ToolIcon> {
            val ic = ToolIcon(this, kind, if (solid) Color.parseColor("#2A1A08") else Ui.GOLD)
            val cell = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = desc
                if (solid) background = Ui.bg(this@PsPlayActivity, Ui.GOLD, 18)
                setOnClickListener {
                    onClick()
                    if (collapse) afterAction() else armIdle()
                }
                addView(ic, FrameLayout.LayoutParams(dpi(24), dpi(24), Gravity.CENTER))
            }
            bar.addView(cell, Ui.lp(0, dpi(46), 1f).apply { if (solid) { leftMargin = dpi(2); rightMargin = dpi(2) } })
            return cell to ic
        }
        // Thứ tự giống thanh công cụ mẫu: xoay · PS · khung chọn vùng · phụ đề · loa · cỡ chữ · tốc độ · cài đặt · trợ giúp
        tool(ToolIcon.Kind.ROTATE, "Xoay ngang hoặc dọc", collapse = false) {
            // Đổi qua lại ngang ↔ dọc và nhớ lựa chọn; chọn "Tự động" ở bảng chất lượng (⚙) để theo cảm biến xoay của máy.
            val toPortrait = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            PsStore.setOrient(this, if (toPortrait) PsStore.ORIENT_PORTRAIT else PsStore.ORIENT_LANDSCAPE)
            PsStore.applyOrient(this)
        }
        homeCell = tool(ToolIcon.Kind.HOME, "Nút PS (Home) của tay cầm", solid = true) { tapPs() }.first
        scanIcon = tool(ToolIcon.Kind.CROP, "Khung chọn vùng phụ đề") { toggleAdjust() }.second
        subsIcon = tool(ToolIcon.Kind.SUBS, "Bật hoặc tắt dịch phụ đề") { toggleTranslate() }.second
        speakIcon = tool(ToolIcon.Kind.SPEAKER_ON, "Đọc to bản dịch") { toggleSpeak() }.second
        tool(ToolIcon.Kind.TEXT_SIZE, "Cỡ chữ phụ đề") { showFontDialog() }
        tool(ToolIcon.Kind.DIAL, "Tốc độ quét phụ đề") { showScanDialog() }
        tool(ToolIcon.Kind.GEAR, "Cài đặt: Dịch hoặc Thuyết minh, giọng đọc, phóng hình, chất lượng hình") { showSettings() }
        tool(ToolIcon.Kind.HELP, "Trợ giúp") { help() }
        syncTools()
        return bar
    }

    /** Hàng hai nút dưới thanh công cụ: "Dịch hết chữ" (đọc và dịch một lần rồi tự tắt) và "Dịch menu" (dịch liên tục). */
    private fun pillsRow(): LinearLayout {
        val row = Ui.hbox(this)
        row.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        fun pill(kind: ToolIcon.Kind, text: String, desc: String, onLong: (() -> Unit)?, onClick: () -> Unit): PillUi {
            val ic = ToolIcon(this, kind, Color.WHITE)
            val tv = Ui.tv(this, text, 15f, Color.WHITE, true).apply { maxLines = 1 }
            val box = Ui.hbox(this).apply {
                setPadding(dpi(14), dpi(9), dpi(16), dpi(9))
                isClickable = true
                isFocusable = true
                contentDescription = desc
                setOnClickListener { onClick(); afterAction() }
                if (onLong != null) setOnLongClickListener { onLong(); true }
                addView(ic, Ui.lp(dpi(20), dpi(20)).apply { rightMargin = dpi(8) })
                addView(tv, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
            }
            row.addView(box, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(10) })
            return PillUi(box, ic, tv, kind)
        }
        pillOnce = pill(ToolIcon.Kind.SCAN, "Dịch hết chữ",
            "Đọc và dịch toàn bộ chữ đang có trên hình một lần, giữ vài giây rồi tự tắt", null) { translateOnce() }
        pillMenu = pill(ToolIcon.Kind.BOLT, "Dịch menu",
            "Dịch liên tục chữ trong menu và giao diện game. Giữ nút để xem chuỗi chưa dịch được", { showUiMissed() }) { toggleUiTr() }
        stylePill(pillOnce, false)
        stylePill(pillMenu, false)
        return row
    }

    /** Nút dịch đang bật = nền trắng chữ tối (như thanh mẫu); đang tắt = kính tối viền mảnh chữ trắng. */
    private fun stylePill(p: PillUi?, on: Boolean) {
        p ?: return
        p.box.background = if (on) Ui.bg(this, Color.parseColor("#F5FFFFFF"), 22)
        else Ui.bg(this, Color.parseColor("#CC17120D"), 22, Color.parseColor("#66FFFFFF"), 1)
        val c = if (on) Color.parseColor("#17120D") else Color.WHITE
        p.icon.set(p.kind, c)
        p.label.setTextColor(c)
    }

    /** Vẽ lại trạng thái các nút công cụ (bật = vàng có nền sáng, tắt = trắng/xám). */
    private fun syncTools() {
        val running = live?.running == true
        val adjusting = live?.isAdjusting == true
        val pr0 = Prefs(this)
        val sp = pr0.speak || pr0.narrate
        fun mark(ic: ToolIcon?, kind: ToolIcon.Kind, on: Boolean, offColor: Int = Ui.GOLD) {
            ic ?: return
            ic.set(kind, if (on) Ui.GOLD else offColor)
            (ic.parent as? View)?.background = if (on) Ui.bg(this, Color.parseColor("#33F2B84B"), 16) else null
        }
        mark(scanIcon, ToolIcon.Kind.CROP, adjusting)
        mark(subsIcon, ToolIcon.Kind.SUBS, running)
        mark(speakIcon, if (sp) ToolIcon.Kind.SPEAKER_ON else ToolIcon.Kind.SPEAKER_OFF, sp, Ui.SUB)
        padIcon?.set(ToolIcon.Kind.GAMEPAD, if (padOn) Ui.GOLD else Ui.SUB)
        val u = uiTr
        stylePill(pillOnce, u != null && u.running && u.once)
        stylePill(pillMenu, u != null && u.running && !u.once)
    }

    private fun togglePad() {
        padOn = !padOn
        pad?.visibility = if (padOn && live?.isAdjusting != true) View.VISIBLE else View.GONE
        if (!padOn) pad?.releaseAll()
        syncTools()
        afterAction()
    }

    // ---- Thanh công cụ tự thu gọn ----

    /** Thanh công cụ (và hàng trên) hiện hay giấu theo [toolsOpen]; xoay dọc thì hàng trên luôn hiện. */
    private fun applyToolsVisibility() {
        val root = playRoot ?: return
        val land = root.land
        val top = if (!land || toolsOpen) View.VISIBLE else View.GONE
        root.close?.visibility = top
        root.padBtn?.visibility = top
        root.pill?.visibility = top
        val tools = if (toolsOpen) View.VISIBLE else View.GONE
        barView?.visibility = tools
        pillsView?.visibility = tools
        handleView?.visibility = if (land && !toolsOpen) View.VISIBLE else View.GONE
        // Xoay ngang: các nút trên cùng nằm đè lên nút vai (L1/L2/R1/R2) của tay cầm ảo, nên khi thanh đang mở thì mờ tay cầm
        // đi cho dễ nhìn; thu lại là tay cầm rõ như cũ. Tay cầm vẫn nhận chạm bình thường.
        pad?.alpha = if (land) (if (toolsOpen) 0.3f else 0.8f) else 1f
        root.requestLayout()
    }

    private fun setTools(open: Boolean) {
        toolsOpen = open
        ui.removeCallbacks(idleRun)
        ui.removeCallbacks(actionRun)
        applyToolsVisibility()
        armIdle()
        if (demo) android.util.Log.i("DG_PLAY", "tools open=$open land=${playRoot?.land == true}")
    }

    /** Xoay ngang và thanh đang mở: tính lại giờ tự thu lại (gọi mỗi lần người chơi chạm vào thanh). */
    private fun armIdle() {
        ui.removeCallbacks(idleRun)
        if (toolsOpen) ui.postDelayed(idleRun, if (playRoot?.land == true) TOOLS_IDLE_MS else TOOLS_IDLE_PORTRAIT_MS)
    }

    /** Tay cầm ảo đang có nút nhấn hoặc cần gạt lệch. */
    private fun padHeld(): Boolean {
        val s = padState
        return s.buttons != 0u || s.l2State.toInt() != 0 || s.r2State.toInt() != 0 ||
            s.leftX.toInt() != 0 || s.leftY.toInt() != 0 || s.rightX.toInt() != 0 || s.rightY.toInt() != 0
    }

    /** Vừa làm xong một thao tác (bật dịch, chỉnh xong, đóng bảng…): thanh tự thu lại để không vướng khi chơi. */
    private fun afterAction(ms: Long = TOOLS_ACTION_MS) {
        ui.removeCallbacks(idleRun)
        ui.removeCallbacks(actionRun)
        if (toolsOpen) ui.postDelayed(actionRun, ms)
    }

    /** Bấm nút PS trên tay cầm (mở trung tâm điều khiển của máy chơi game): nhấn khoảng 150 ms rồi nhả. */
    private fun tapPs() {
        val s = session
        if (s == null) {
            if (demo) android.util.Log.i("DG_PLAY", "ps-tap session=false")
            Toast.makeText(this, "Chưa kết nối với máy chơi game nên chưa bấm được nút PS.", Toast.LENGTH_SHORT).show()
            return
        }
        try { s.setControllerState(padState or phys or ControllerState(buttons = ControllerState.BUTTON_PS)) } catch (_: Throwable) {}
        ui.postDelayed({ if (!isFinishing && !isDestroyed) push() }, 150)
    }

    private val zooms = floatArrayOf(1f, 1.25f, 1.5f)

    private fun applyZoom() {
        val f = vidFrame ?: return
        val z = zooms[zoomLevel.coerceIn(0, zooms.size - 1)]
        f.pivotX = f.width / 2f
        f.pivotY = f.height / 2f
        f.scaleX = z
        f.scaleY = z
    }

    private fun toggleSpeak() {
        val pr = Prefs(this)
        if (pr.narrate) {
            Toast.makeText(this, "Đang ở chế độ Thuyết minh: luôn đọc to phụ đề. Muốn tắt tiếng thì bấm ⚙ và chọn Dịch.", Toast.LENGTH_LONG).show()
            return
        }
        pr.speak = !pr.speak
        live?.setSpeak(pr.speak)
        syncTools()
        Toast.makeText(this, if (pr.speak) "Đọc to bản dịch: bật." else "Đọc to bản dịch: tắt (chỉ hiện chữ).", Toast.LENGTH_SHORT).show()
    }

    private fun showFontDialog() {
        val a = this
        val pr = Prefs(a)
        Ui.sheet(a) { dlg ->
            dlg.setOnDismissListener { afterAction(300) }
            addView(Ui.tv(a, "Cỡ chữ phụ đề", 18f, Ui.TEXT, true))
            addView(Ui.seekRow(a, "Cỡ chữ", { v -> "$v%" }, 50, 200, pr.subScale) { pr.subScale = it; live?.restyle() }, Ui.lpm(a, 10, 8))
            addView(Ui.button(a, "Xong", true) { dlg.dismiss() }, Ui.lpm(a, 8, 0))
        }
    }

    private fun showScanDialog() {
        val a = this
        val pr = Prefs(a)
        Ui.sheet(a) { dlg ->
            dlg.setOnDismissListener { afterAction(300) }
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
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Mẹo khi chơi")
            .setMessage(
                "Thanh công cụ, từ trái sang phải:\n" +
                    "⟳  Xoay ngang hoặc dọc. Xoay ngang để xem hình lớn, tay cầm trong suốt phủ lên hình.\n" +
                    "⌂  Nút PS (Home) của tay cầm: mở trung tâm điều khiển của máy chơi game.\n" +
                    "⌗  Khung chọn vùng phụ đề: kéo khung tới đúng dòng phụ đề của game, kéo chấm tròn để đổi cỡ. Bấm Đặt lại nếu lỡ kéo quá to, bấm Xong để ẩn khung.\n" +
                    "▭  Bật hoặc tắt dịch phụ đề ngay trên hình game.\n" +
                    "🔊  Đọc to bản dịch (bật/tắt).\n" +
                    "A  Cỡ chữ phụ đề.\n" +
                    "◔  Tốc độ quét: quét nhiều đỡ sót câu nhưng hao pin hơn.\n" +
                    "⚙  Cài đặt: Dịch hoặc Thuyết minh, ngôn ngữ trong game, giọng đọc (cơ bản, AI offline, Gemini), tốc độ đọc, phóng hình và chất lượng hình (chất lượng hình áp dụng bằng cách kết nối lại).\n\n" +
                    "Hai nút dưới thanh công cụ:\n" +
                    "• Dịch hết chữ: đọc và dịch toàn bộ chữ đang có trên hình MỘT lần (kể cả phụ đề), giữ vài giây rồi tự tắt, hoặc tắt ngay khi cảnh đổi.\n" +
                    "• Dịch menu: dịch liên tục chữ trong menu, vật phẩm, kỹ năng, nhiệm vụ… Bản dịch hiện đè đúng chỗ chữ gốc; viền vàng là bản dịch còn nghi ngờ. Giữ nút này để xem chuỗi chưa dịch được.\n\n" +
                    "Tự thu gọn: bấm xong một nút thì thanh tự thu lại cho khỏi vướng; khi xoay ngang, để yên vài giây thanh cũng tự thu lại. Chạm chấm nhỏ ở giữa mép trên để mở lại.\n\n" +
                    "• Nút tay cầm ở góc trên: ẩn hoặc hiện tay cầm ảo (tay cầm thật nối với điện thoại vẫn dùng được).\n" +
                    "• Ô người chơi ở góc trên: đổi sang tài khoản đã ghép khác.\n" +
                    "• Thanh nhỏ dưới hình (khi xoay dọc): thu gọn hoặc mở thanh công cụ.\n" +
                    "• Máy báo đang bận: tắt Chơi từ xa ở thiết bị khác, đợi 30 giây rồi thử lại.\n" +
                    "• Hình giật: dùng Wi-Fi 5 GHz hoặc dây LAN cho máy chơi game, hạ chất lượng hình ở ⚙."
            )
            .setPositiveButton("Đã hiểu", null)
            .setOnDismissListener { afterAction(300) }
            .show()
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
                if (wantUiTr) ui.post { if (!isFinishing && !isDestroyed && uiTr?.running != true) startUiTr() }
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
        AlertDialog.Builder(this, R.style.DgDialog)
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
        AlertDialog.Builder(this, R.style.DgDialog)
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
        AlertDialog.Builder(this, R.style.DgDialog)
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
        if (demo) android.util.Log.i("DG_PLAY", "surface-tao act=${System.identityHashCode(this)} sv=${System.identityHashCode(videoView)}")
        activeSurfaceHolder = holder
        surface = holder.surface
        session?.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (activeSurfaceHolder !== holder) return
        // Chỉ đổi kích thước (xoay màn hình) thì surface vẫn là surface đã giao cho bộ giải mã: không giao lại.
        if (surface === holder.surface) return
        surface = holder.surface
        session?.setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (demo) android.util.Log.i("DG_PLAY", "surface-huy act=${System.identityHashCode(this)} sv=${System.identityHashCode(videoView)}")
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

    private fun ensureUiTr(): UiTranslate {
        var u = uiTr
        if (u == null) {
            u = UiTranslate(this, { videoView }, {
                // vùng phụ đề đang được dịch riêng: không dịch chữ giao diện chồng lên đó
                val l = live
                if (l != null && l.running) l.box.let { b -> RectF(b.fl, b.ft, b.fr, b.fb) } else null
            })
            uiTr = u
        }
        vidFrame?.let { u.attach(it) }
        return u
    }

    /** Bật/tắt dịch chữ giao diện game: đọc cả khung hình, dịch từng khối chữ rồi vẽ đè lên chữ gốc. */
    private fun toggleUiTr() {
        try {
            val u = ensureUiTr()
            if (u.running && !u.once) {
                wantUiTr = false
                u.stop()
                syncTools()
                Toast.makeText(this, "Đã tắt Dịch menu.", Toast.LENGTH_SHORT).show()
                return
            }
            if (u.running) u.stop()   // đang "Dịch hết chữ" một lần: chuyển sang dịch liên tục
            wantUiTr = true
            startUiTr()
        } catch (e: Throwable) {
            CrashLog.mark("choi:dich-gd-loi " + e.javaClass.simpleName)
            syncTools()
            Toast.makeText(this, "Không bật được dịch giao diện: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startUiTr() {
        try {
            val u = ensureUiTr()
            if (u.running) return
            u.start()
            syncTools()
            if (u.running) Toast.makeText(this, "Đang dịch menu và chữ giao diện của game. Giữ nút Dịch menu để xem chuỗi chưa dịch được.", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            CrashLog.mark("choi:dich-gd-loi " + e.javaClass.simpleName)
            syncTools()
            Toast.makeText(this, "Không bật được dịch giao diện: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * "Dịch hết chữ": chụp khung hình hiện tại, đọc và dịch mọi chữ trên đó (kể cả vùng phụ đề) MỘT lần, giữ bản dịch ít giây
     * rồi tự tắt (hoặc tắt ngay khi cảnh đổi). Bấm lần nữa khi đang chạy để tắt sớm.
     */
    private fun translateOnce() {
        try {
            val u = ensureUiTr()
            if (u.running && !u.once) {
                Toast.makeText(this, "Dịch menu đang bật nên chữ trên hình đã được dịch liên tục. Muốn dịch một lần thì tắt Dịch menu trước.", Toast.LENGTH_LONG).show()
                return
            }
            if (u.running) {
                u.stop()
                syncTools()
                Toast.makeText(this, "Đã tắt Dịch hết chữ.", Toast.LENGTH_SHORT).show()
                return
            }
            wantUiTr = false
            u.startOnce {
                if (demo) android.util.Log.i("DG_PLAY", "mot-lan da-tu-tat")
                syncTools()
            }
            syncTools()
            if (u.running) Toast.makeText(this, "Đang đọc và dịch toàn bộ chữ trên hình…", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            CrashLog.mark("choi:dich-het-loi " + e.javaClass.simpleName)
            syncTools()
            Toast.makeText(this, "Không bật được Dịch hết chữ: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    /** Danh sách chuỗi chưa dịch được hoặc còn nghi ngờ trong phiên này, để người dùng bổ sung vào gói Việt hoá. */
    private fun showUiMissed() {
        val rep = uiTr?.missedReport().orEmpty()
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Chuỗi chưa dịch được")
            .setMessage(
                if (rep.isEmpty()) "Chưa có chuỗi nào bị bỏ sót trong phiên này (hoặc chưa bật dịch giao diện).\n\nChữ như ID, số liệu, chỉ số viết tắt, tên riêng và chữ đã là tiếng Việt được bỏ qua có chủ ý."
                else rep + "\n\n(Chữ có dấu → là chưa dịch được hoặc nghi ngờ. Có thể thêm bản dịch chuẩn vào gói Việt hoá của game.)"
            )
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Sao chép") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("dg-ui-missed", rep))
            }
            .show()
    }

    /** Chỉ bản debug: vẽ vài khối chữ giả để CI chụp ảnh kiểm tra lớp phủ (không kiểm tra OCR). */
    private fun demoUiOverlay(tries: Int = 0) {
        val u = ensureUiTr()
        val vf = vidFrame ?: return
        val w = vf.width.toFloat()
        val h = vf.height.toFloat()
        // Lớp phủ chưa được bố cục xong (máy giả lập chậm): bố cục lần đầu sẽ xoá bản dịch cũ, nên đợi rồi mới vẽ
        if (w < 10f || h < 10f || u.overlay.width < 10 || u.overlay.height < 10) {
            if (tries < 40 && !isFinishing && !isDestroyed) ui.postDelayed({ demoUiOverlay(tries + 1) }, 250)
            return
        }
        fun r(l: Float, t: Float, rr: Float, b: Float) = RectF(l * w, t * h, rr * w, b * h)
        u.overlay.set(listOf(
            UiOverlay.Draw(r(0.06f, 0.06f, 0.36f, 0.13f), "Kho đồ", 0xFF1B2A3A.toInt(), Color.WHITE, false),
            UiOverlay.Draw(r(0.06f, 0.18f, 0.40f, 0.25f), "Cây kỹ năng", 0xFFE8E0C8.toInt(), 0xFF101010.toInt(), false),
            UiOverlay.Draw(r(0.06f, 0.32f, 0.46f, 0.39f), "Tăng 15% sát thương vật lý trong 10 giây", 0xFF2B1F3F.toInt(), Color.WHITE, false),
            UiOverlay.Draw(r(0.06f, 0.46f, 0.30f, 0.53f), "Kiếm ngắn thép", 0xFF3A1F1F.toInt(), Color.WHITE, true),
            UiOverlay.Draw(r(0.50f, 0.06f, 0.94f, 0.16f), "Nhiệm vụ: Tìm người thợ rèn ở làng phía bắc", 0xFF16301F.toInt(), Color.WHITE, false),
        ))
        android.util.Log.i("DG_PLAY", "demo lop-phu-giao-dien da ve")
    }

    /** Đặt khung đọc phụ đề về dải phụ đề mặc định ở phía dưới hình (khi lỡ kéo quá to hoặc lệch). */
    private fun resetRegion() {
        val l = ensureLive()
        l.resetRegion()
        Toast.makeText(this, "Đã đặt lại khung về dải phụ đề phía dưới hình.", Toast.LENGTH_SHORT).show()
        if (demo) android.util.Log.i("DG_PLAY", "dat-lai khung=${l.box.save()}")
    }

    /** Đổi chế độ hoặc ngôn ngữ: dịch phụ đề và dịch giao diện đang chạy thì khởi động lại để nhận cài đặt mới. */
    private fun restartTranslation() {
        live?.let { l -> if (l.running) { l.stop(); l.start() } }
        uiTr?.let { u -> if (u.running) { val once = u.once; u.stop(); if (!once) u.start() } }
        syncTools()
    }

    /**
     * Bảng cài đặt ngay trong màn chơi (không rời màn nên phiên Remote Play được giữ nguyên): chọn Dịch hoặc Thuyết minh,
     * ngôn ngữ trong game, giọng đọc (cơ bản / AI offline / Gemini, chọn giọng, tốc độ, nghe thử) và lối vào chất lượng hình.
     */
    private fun showSettings() {
        val a = this
        val pr = Prefs(a)
        var tester: Speaker? = null
        fun testerSp(): Speaker? {
            if (tester == null) tester = try {
                Speaker(a, pr).also { sp -> sp.onAiError = { m -> Toast.makeText(a, m, Toast.LENGTH_LONG).show() } }
            } catch (_: Throwable) { null }
            return tester
        }
        Ui.sheet(a) { dlg ->
            addView(Ui.tv(a, "Cài đặt", 26f, Ui.TEXT, true))

            val cMode = Ui.card(a)
            cMode.addView(Ui.tv(a, "Chế độ", 16f, Ui.TEXT, true))
            val hMode = Ui.vbox(a)
            cMode.addView(hMode, Ui.lpm(a, 8, 0))
            addView(cMode, Ui.lpm(a, 12, 0))

            val cVoice = Ui.card(a)
            cVoice.addView(Ui.tv(a, "Giọng đọc", 16f, Ui.TEXT, true))
            val hVoice = Ui.vbox(a)
            cVoice.addView(hVoice, Ui.lpm(a, 8, 0))
            addView(cVoice, Ui.lpm(a, 12, 0))

            val cZ = Ui.card(a)
            cZ.addView(Ui.tv(a, "Phóng hình", 16f, Ui.TEXT, true))
            cZ.addView(Ui.segmented(a, listOf("Vừa khung", "×1,25", "×1,5"), zoomLevel.coerceIn(0, zooms.size - 1)) { i ->
                zoomLevel = i
                applyZoom()
            }, Ui.lpm(a, 8, 0))
            addView(cZ, Ui.lpm(a, 12, 0))

            val cQ = Ui.card(a, 4)
            cQ.addView(Ui.navRow(a, null, "Chất lượng hình", PsQuality.summary(a)) {
                dlg.dismiss()
                PsQuality.sheet(a, null) { reconnect() }
            })
            addView(cQ, Ui.lpm(a, 12, 0))

            fun drawVoice() {
                hVoice.removeAllViews()
                if (pr.narrate) {
                    hVoice.addView(Ui.tv(a, "Thuyết minh luôn đọc to phụ đề tiếng Việt của game.", 13f, Ui.SUB), Ui.lpm(a, 0, 6))
                } else {
                    hVoice.addView(Ui.switchRow(a, "Đọc to bản dịch", "Tắt nếu chỉ muốn xem chữ.", pr.speak) {
                        pr.speak = it
                        live?.setSpeak(it)
                        syncTools()
                    })
                }
                hVoice.addView(Ui.segmented(a, listOf("Cơ bản", "AI offline", "Gemini AI"),
                    when (pr.voiceEngine) { "offline" -> 1; "ai" -> 2; else -> 0 }) { i ->
                    pr.voiceEngine = when (i) { 1 -> "offline"; 2 -> "ai"; else -> "system" }
                    runCatching { tester?.shutdown() }
                    tester = null
                    live?.reloadVoice()
                    drawVoice()
                }, Ui.lpm(a, 6, 6))
                when (pr.voiceEngine) {
                    "offline" -> {
                        val pack = Voices.pack(pr.offlineVoice) ?: Voices.PACKS[0]
                        if (Voices.installed(a, pack.id)) {
                            hVoice.addView(Ui.tv(a, "${pack.name}: ✔ đã tải về máy (${Voices.sizeMb(a, pack.id)} MB).", 13.5f, Ui.OK), Ui.lpm(a, 2, 6))
                        } else {
                            hVoice.addView(Ui.tv(a, "Chưa tải giọng nên app đang đọc bằng giọng cơ bản. Cần khoảng ${pack.downloadMb} MB, nên dùng Wi-Fi.", 13f, Ui.SUB), Ui.lpm(a, 2, 6))
                            val btn = Ui.button(a, "⬇  Tải giọng (${pack.downloadMb} MB)", true) {}
                            var downloading = false
                            btn.setOnClickListener {
                                if (downloading) return@setOnClickListener
                                downloading = true
                                btn.text = "Đang tải 0%…"
                                val app = applicationContext
                                Thread {
                                    val err = Voices.download(app, pack, java.util.concurrent.atomic.AtomicBoolean(false)) { pct ->
                                        runOnUiThread { btn.text = "Đang tải $pct%…" }
                                    }
                                    runOnUiThread {
                                        downloading = false
                                        if (isFinishing || isDestroyed) return@runOnUiThread
                                        if (err == null) {
                                            Toast.makeText(a, "Đã tải xong giọng đọc.", Toast.LENGTH_SHORT).show()
                                            live?.reloadVoice()
                                            runCatching { tester?.shutdown() }
                                            tester = null
                                            drawVoice()
                                        } else {
                                            btn.text = "⬇  Tải giọng (${pack.downloadMb} MB)"
                                            Toast.makeText(a, err, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.start()
                            }
                            hVoice.addView(btn, Ui.lpm(a, 0, 6))
                        }
                    }
                    "ai" -> {
                        hVoice.addView(Ui.dropRow(a, "Giọng Gemini",
                            { Prefs.AI_VOICES.firstOrNull { it.first == pr.aiVoice }?.let { "${it.first} · ${it.second}" } ?: pr.aiVoice },
                            { Prefs.AI_VOICES.map { "${it.first} · ${it.second}" } }
                        ) { i -> pr.aiVoice = Prefs.AI_VOICES[i].first; live?.reloadVoice() })
                        if (pr.geminiKeys().isEmpty())
                            hVoice.addView(Ui.tv(a, "Chưa có khóa Gemini nên app đọc bằng giọng cơ bản. Thêm khóa ở Cài đặt › Cách dịch (ngoài màn chơi).", 12.5f, Ui.DANGER), Ui.lpm(a, 2, 6))
                        else
                            hVoice.addView(Ui.tv(a, "Mỗi câu đọc tốn một lượt gọi Gemini; mất mạng hoặc hết lượt thì tự lùi về giọng của máy.", 12.5f, Ui.SUB), Ui.lpm(a, 2, 6))
                    }
                    else -> hVoice.addView(Ui.dropRow(a, "Chọn giọng",
                        { pr.voice.ifEmpty { "Mặc định" } },
                        { listOf("Mặc định") + (testerSp()?.voices() ?: emptyList()) }
                    ) { i ->
                        val list = listOf("") + (testerSp()?.voices() ?: emptyList())
                        pr.voice = list.getOrElse(i) { "" }
                        testerSp()?.applyVoice()
                        live?.reloadVoice()
                    })
                }
                val rateLabel = { v: Int -> String.format("%.1f", v / 10f).replace('.', ',') + "x" }
                val vt = Ui.tv(a, rateLabel(pr.rate10), 14f, Ui.SUB)
                val head = Ui.hbox(a)
                head.addView(Ui.tv(a, "Tốc độ đọc", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
                head.addView(vt)
                hVoice.addView(head, Ui.lpm(a, 6, 0))
                hVoice.addView(Ui.seekIcons(a, "🐢", "🐇", 5, 30, pr.rate10) { pr.rate10 = it; vt.text = rateLabel(it) }, Ui.lpm(a, 6, 0))
                hVoice.addView(Ui.switchRow(a, "Tự đọc nhanh khi câu dồn lại",
                    "Câu thoại mới tới lúc câu cũ chưa đọc xong thì đọc nhanh hơn để kịp hình.", pr.autoFast) { pr.autoFast = it })
                hVoice.addView(Ui.switchRow(a, "Đọc theo cảm xúc",
                    "Nhận ra câu giận, buồn, vui hay sợ qua câu chữ để đổi nhịp và giọng đọc.", pr.emotion) { pr.emotion = it })
                hVoice.addView(Ui.button(a, "▶  Nghe thử", true) {
                    testerSp()?.speak("Xin chào, đây là giọng đọc bản dịch trong game.")
                }, Ui.lpm(a, 6, 0))
            }

            fun drawMode() {
                hMode.removeAllViews()
                hMode.addView(Ui.segmented(a, listOf("Dịch sang tiếng Việt", "Thuyết minh"), if (pr.narrate) 1 else 0) { i ->
                    val n = i == 1
                    if (n != pr.narrate) {
                        pr.narrate = n
                        restartTranslation()
                    }
                    drawMode()
                    drawVoice()
                })
                hMode.addView(Ui.tv(a,
                    if (pr.narrate) "Thuyết minh: game hoặc phim đã có phụ đề tiếng Việt, app chỉ đọc to phụ đề, không dịch."
                    else "Dịch: đọc chữ ${Prefs.srcNameOf(pr.lang)} trong game rồi dịch sang tiếng Việt.", 12.5f, Ui.SUB), Ui.lpm(a, 8, 2))
                if (!pr.narrate) {
                    val codes = Prefs.LANGS.map { it.first }.filter { it != "vi" }
                    hMode.addView(Ui.dropRow(a, "Ngôn ngữ trong game",
                        { Prefs.langName(pr.lang) },
                        { codes.map { Prefs.langLabel(it) } }
                    ) { i ->
                        val c = codes[i]
                        if (c != pr.lang) { pr.lang = c; restartTranslation() }
                        drawMode()
                    })
                }
            }
            drawMode()
            drawVoice()
            dlg.setOnDismissListener { runCatching { tester?.shutdown() }; tester = null; afterAction(300) }
        }
    }

    /** Chọn khung đọc phụ đề: tay cầm ảo tạm ẩn để kéo khung cho dễ, bấm lần nữa để xong. */
    private fun toggleAdjust() {
        try {
            val l = ensureLive()
            vidFrame?.let { l.attach(it) }
            val on = !l.isAdjusting
            l.setAdjusting(on)
            adjustBar?.visibility = if (on) View.VISIBLE else View.GONE
            playRoot?.requestLayout()
            pad?.visibility = if (!on && padOn) View.VISIBLE else View.GONE
            if (on) pad?.releaseAll()
            // đang chỉnh khung thì thanh công cụ thu lại cho khỏi vướng; xong (bấm Xong) thì cũng giữ thu lại để chơi
            setTools(false)
            syncTools()
            if (demo) android.util.Log.i("DG_PLAY", "chinh khung on=$on pad=${pad?.visibility} act=${System.identityHashCode(this)} vid=${System.identityHashCode(vidFrame)}")
            Toast.makeText(
                this,
                if (on) "Kéo khung tới đúng chỗ phụ đề của game, kéo chấm tròn để đổi cỡ. Lỡ kéo quá to thì bấm Đặt lại, xong thì bấm Xong."
                else "Đã chọn khung. Khung ẩn đi khi đang dịch.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Không mở được khung chọn vùng: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}
