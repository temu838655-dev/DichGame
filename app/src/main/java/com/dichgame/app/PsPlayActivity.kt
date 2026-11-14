package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
        private const val REQ_CAPTURE = 911
    }

    private var console: PsConsole? = null
    private var player = 0
    private var mode = "phone"
    private var ratio = 16f / 9f

    private var session: Session? = null
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
    private var transBtn: TextView? = null
    private var gameAsrBtn: TextView? = null
    private var gameAsrSubtitle: TextView? = null
    private var gameAsr: GameAudioAsr? = null
    private var gameTranslator: TextTranslator? = null
    private var gameAsrStarting = false
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
        player = intent.getIntExtra(EXTRA_PLAYER, 0)
        padOn = mode != "tv"
        val c = PsStore.load(this)
        if (!PsNative.available || c == null || c.players.isEmpty()) {
            Toast.makeText(this, "Chưa có máy đã ghép nối, hoặc máy này chưa hỗ trợ chơi PS5 trong app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        console = c
        player = player.coerceIn(0, c.players.size - 1)
        try {
            val prof = PsStore.profile(this)
            if (prof.height > 0) ratio = prof.width.toFloat() / prof.height
        } catch (_: Throwable) {}
        build()
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
        if (console != null && !manual) requestConnect()
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
        activityStarted = false
        reconnectPending = false
        stopGameSpeech()
        shutdownSession()
        ui.removeCallbacksAndMessages(null)
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
        sv.holder.addCallback(this)
        val st = Ui.tv(this, "", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        statusView = st
        val vid = FitFrame(this, ratio)
        vid.setBackgroundColor(Color.BLACK)
        vid.addView(sv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        vid.addView(st, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        gameAsrSubtitle = Ui.tv(this, "", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@PsPlayActivity, 16), Ui.dp(this@PsPlayActivity, 10), Ui.dp(this@PsPlayActivity, 16), Ui.dp(this@PsPlayActivity, 10))
            background = Ui.bg(this@PsPlayActivity, Color.parseColor("#D9000000"), 12)
            visibility = View.GONE
            maxLines = 4
        }
        vid.addView(gameAsrSubtitle, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = Ui.dp(this@PsPlayActivity, 14)
            rightMargin = Ui.dp(this@PsPlayActivity, 14)
            bottomMargin = Ui.dp(this@PsPlayActivity, 14)
        })

        val padV = PadView(this) { s -> padState = s; push() }
        pad = padV
        padV.visibility = if (padOn) View.VISIBLE else View.GONE

        val chip = Ui.tv(this, "", 12f, Ui.SUB).apply {
            setPadding(Ui.dp(this@PsPlayActivity, 10), Ui.dp(this@PsPlayActivity, 5), Ui.dp(this@PsPlayActivity, 10), Ui.dp(this@PsPlayActivity, 5))
            background = Ui.bg(this@PsPlayActivity, Ui.CARD2, 14)
        }
        chipView = chip

        if (!land) {
            val root = Ui.vbox(this)
            root.setBackgroundColor(Color.parseColor("#0B0F0C"))
            val top = Ui.hbox(this)
            top.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8))
            top.addView(roundBtn("✕") { leave() })
            top.addView(Ui.tv(this, (console?.name ?: "PS5") + " · " + (console?.players?.getOrNull(player)?.label ?: ""), 14f, Ui.TEXT, true).apply {
                setPadding(Ui.dp(this@PsPlayActivity, 12), 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, Ui.lp(0, WRAP_CONTENT, 1f))
            top.addView(chip)
            root.addView(top)
            root.addView(vid, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
            val bar = toolbar()
            bar.background = Ui.bg(this, Color.parseColor("#17120D"), 24)
            root.addView(bar, Ui.lpm(this, 8, 4).apply { leftMargin = Ui.dp(this@PsPlayActivity, 10); rightMargin = Ui.dp(this@PsPlayActivity, 10) })
            root.addView(padV, Ui.lp(MATCH_PARENT, 0, 1f))
            setContentView(root)
        } else {
            val root = FrameLayout(this)
            root.setBackgroundColor(Color.BLACK)
            root.addView(vid, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
            padV.alpha = 0.8f
            root.addView(padV, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            val bar = toolbar()
            bar.background = Ui.bg(this, Color.parseColor("#B0120D0A"), 20)
            root.addView(bar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(this@PsPlayActivity, 6) })
            root.addView(chip, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                topMargin = Ui.dp(this@PsPlayActivity, 10); rightMargin = Ui.dp(this@PsPlayActivity, 12)
            })
            setContentView(root)
        }
        refreshStatus()
    }

    private fun roundBtn(label: String, onClick: () -> Unit): TextView = Ui.tv(this, label, 18f, Ui.TEXT, true).apply {
        gravity = Gravity.CENTER
        background = Ui.bg(this@PsPlayActivity, Ui.CARD2, 22)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@PsPlayActivity, 40), Ui.dp(this@PsPlayActivity, 40))
        setOnClickListener { onClick() }
    }

    private fun toolbar(): LinearLayout {
        val bar = Ui.hbox(this)
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4))
        fun item(label: String, onClick: (TextView) -> Unit): TextView {
            val t = Ui.tv(this, label, 20f, Ui.GOLD).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(this@PsPlayActivity, 12), Ui.dp(this@PsPlayActivity, 8), Ui.dp(this@PsPlayActivity, 12), Ui.dp(this@PsPlayActivity, 8))
            }
            t.setOnClickListener { onClick(t) }
            bar.addView(t, Ui.lp(0, WRAP_CONTENT, 1f))
            return t
        }
        transBtn = item(if (CaptureService.active) "💬" else "🌐") { toggleTranslate() }
        gameAsrBtn = item(if (gameAsr != null) "🎙✓" else "🎙") { toggleGameSpeech() }
        item("🎮") { padOn = !padOn; pad?.visibility = if (padOn) View.VISIBLE else View.GONE; if (!padOn) pad?.releaseAll() }
        item("⟳") {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        item("⚙") { PsQuality.sheet(this, null) { reconnect() } }
        item("❓") { help() }
        item("⏻") { leave() }
        return bar
    }

    private fun setStatus(text: String, show: Boolean) {
        statusView?.apply { this.text = text; visibility = if (show) View.VISIBLE else View.GONE }
        chipView?.text = if (connected) "● Đã kết nối · " + PsQuality.summary(this) else "● " + text.lines().first()
        chipView?.setTextColor(if (connected) Ui.OK else Ui.GOLD)
    }

    private fun refreshStatus() {
        if (connected) setStatus("", false) else setStatus(if (manual) "Đã ngắt kết nối" else "Đang vào game…", true)
        transBtn?.text = if (CaptureService.active) "💬" else "🌐"
        gameAsrBtn?.text = if (gameAsr != null) "🎙✓" else "🎙"
    }

    private fun help() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Mẹo khi chơi")
            .setMessage(
                "🌐 / 💬  Bật hoặc tắt dịch phụ đề trên hình game.\n" +
                    "🎮  Ẩn hoặc hiện tay cầm ảo (tay cầm thật nối với điện thoại vẫn dùng được).\n" +
                    "⟳  Xoay ngang để xem hình lớn, tay cầm trong suốt phủ lên hình.\n" +
                    "⚙  Đổi chất lượng hình, áp dụng ngay bằng cách kết nối lại.\n\n" +
                    "• Bật phụ đề tiếng Anh trong game, rồi kéo khung dịch tới đúng dòng phụ đề.\n" +
                    "• Máy báo đang bận: tắt Chơi từ xa ở thiết bị khác, đợi 30 giây rồi thử lại.\n" +
                    "• Hình giật: dùng Wi-Fi 5 GHz hoặc dây LAN cho máy chơi game, hạ chất lượng ở ⚙.\n" +
                    "• Tay cầm trên TV bị ngắt khi vào bằng người chơi chính: bấm nút PS trên tay cầm rồi chọn lại người chơi của bạn."
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
        var candidate: Session? = null
        try {
            val info = PsStore.connectInfo(this, c, player)
            val s = Session(info, null, false)
            candidate = s
            s.eventCallback = { ev -> runOnUiThread { if (g == gen && !isFinishing && !isDestroyed) onEvent(ev) } }
            val r = s.start()
            if (!r.isSuccess) {
                // start() thất bại vẫn phải dừng native session trước khi giải phóng,
                // giống cùng thứ tự stop -> dispose dùng cho phiên đang chạy.
                try { s.stop() } catch (_: Throwable) {}
                try { s.dispose() } catch (_: Throwable) {}
                candidate = null
                val message = "Không khởi động được phiên Remote Play: $r"
                if (activityStarted && !isFinishing && !isDestroyed) retryAfterNetworkDrop(message)
                else ended(message, true)
                return
            }
            session = s
            candidate = null
            surface?.let { s.setSurface(it) }
            push()
        } catch (e: Throwable) {
            // start() có thể ném lỗi trước khi Session được đưa vào biến session; giải phóng ứng viên để tránh rò tài nguyên native.
            candidate?.let { try { it.stop() } catch (_: Throwable) {}; try { it.dispose() } catch (_: Throwable) {} }
            val message = "Lỗi mở Remote Play: ${e.message ?: e.javaClass.simpleName}"
            if (activityStarted && !isFinishing && !isDestroyed) retryAfterNetworkDrop(message)
            else ended(message, true)
        }
    }

    private fun onEvent(ev: Event) {
        when (ev) {
            is ConnectedEvent -> {
                connected = true
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
        stopGameSpeech()
        val s = session
        if (s == null) {
            // Lỗi xảy ra trước khi Session được gán (ví dụ start() trả lỗi):
            // vẫn phải chạy lịch retry thay vì mắc kẹt ở trạng thái chờ.
            schedulePendingReconnect()
            return
        }
        session = null
        stopping = true
        Thread {
            try { s.stop() } catch (_: Throwable) {}
            try { s.dispose() } catch (_: Throwable) {}
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

    // ---------------- Nhận dạng và dịch giọng nói PS5 ----------------

    private fun toggleGameSpeech() {
        if (gameAsr != null) {
            stopGameSpeech()
            Toast.makeText(this, "Đã tắt nhận dạng giọng nói PS5.", Toast.LENGTH_SHORT).show()
            return
        }
        if (gameAsrStarting) {
            Toast.makeText(this, "Đang chuẩn bị bộ dịch giọng nói…", Toast.LENGTH_SHORT).show()
            return
        }
        if (!connected || session == null) {
            Toast.makeText(this, "Hãy kết nối Remote Play với PS5 trước.", Toast.LENGTH_SHORT).show()
            return
        }
        if (GameAsrModel.installed(this)) {
            startGameSpeech()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Tải nhận dạng giọng nói offline")
            .setMessage(
                "DichGame cần tải mô hình Whisper đa ngôn ngữ khoảng 111 MB (cần tối thiểu khoảng 222 MB bộ nhớ trống). " +
                    "Sau khi tải, nhận dạng âm thanh PS5 chạy trên điện thoại và không cần gửi âm thanh lên máy chủ. " +
                    "Tốc độ phụ thuộc cấu hình điện thoại."
            )
            .setNegativeButton("Để sau", null)
            .setPositiveButton("Tải mô hình") { _, _ -> downloadGameAsrModel() }
            .show()
    }

    private fun downloadGameAsrModel() {
        gameAsrStarting = true
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog = ProgressDialog(this).apply {
            setTitle("Đang tải Whisper")
            setMessage("Chuẩn bị tải mô hình…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            this.progress = 0
            setCancelable(true)
            setOnCancelListener { cancel.set(true) }
            show()
        }
        Thread {
            val error = GameAsrModel.download(this, cancel) { value ->
                runOnUiThread { if (!isFinishing && dialog.isShowing) dialog.progress = value }
            }
            runOnUiThread {
                if (dialog.isShowing) dialog.dismiss()
                gameAsrStarting = false
                if (error == null) {
                    Toast.makeText(this, "Đã cài mô hình Whisper offline.", Toast.LENGTH_LONG).show()
                    startGameSpeech()
                } else if (error != "Đã hủy tải mô hình.") {
                    AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Không tải được mô hình")
                        .setMessage(error)
                        .setPositiveButton("Đóng", null)
                        .show()
                }
            }
        }.start()
    }

    private fun startGameSpeech() {
        val activeSession = session
        if (activeSession == null || !connected || isFinishing || isDestroyed) return
        gameAsrStarting = true
        val prefs = Prefs(this)
        val translator = TextTranslator(this, prefs, prefs.lang)
        translator.prepare { ready ->
            if (!ready) {
                gameAsrStarting = false
                translator.close()
                if (!isFinishing) Toast.makeText(this, "Không tải được dữ liệu dịch offline. Kiểm tra mạng rồi thử lại.", Toast.LENGTH_LONG).show()
                return@prepare
            }
            if (isFinishing || isDestroyed || session !== activeSession || !connected) {
                gameAsrStarting = false
                translator.close()
                return@prepare
            }
            val engine = GameAudioAsr(
                this,
                onTranscript = { recognized ->
                    ui.post {
                        if (gameAsr == null || gameTranslator !== translator || isFinishing || isDestroyed) return@post
                        translator.translate(recognized) { vietnamese ->
                            if (gameTranslator === translator && gameAsr != null) {
                                gameAsrSubtitle?.text = "$vietnamese\n$recognized"
                                gameAsrSubtitle?.visibility = View.VISIBLE
                            }
                        }
                    }
                },
                onError = { message ->
                    ui.post {
                        if (gameTranslator === translator) {
                            stopGameSpeech()
                            Toast.makeText(this, "Whisper gặp lỗi: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
            try {
                gameTranslator = translator
                gameAsr = engine
                gameAsrStarting = false
                gameAsrBtn?.text = "🎙✓"
                engine.start(activeSession)
                Toast.makeText(this, "Đang nghe âm thanh trực tiếp từ PS5 bằng Whisper offline.", Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                gameAsrStarting = false
                if (gameAsr === engine) gameAsr = null
                if (gameTranslator === translator) gameTranslator = null
                engine.close()
                translator.close()
                Toast.makeText(this, "Không bật được nhận dạng: \${e.message ?: "lỗi không xác định"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stopGameSpeech() {
        gameAsrStarting = false
        gameAsr?.close()
        gameAsr = null
        gameTranslator?.close()
        gameTranslator = null
        gameAsrSubtitle?.text = ""
        gameAsrSubtitle?.visibility = View.GONE
        gameAsrBtn?.text = "🎙"
    }

    // ---------------- Dịch ----------------

    private fun toggleTranslate() {
        if (CaptureService.active) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP))
            CaptureService.active = false
            transBtn?.text = "🌐"
            Toast.makeText(this, "Đã tắt dịch phụ đề.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy bật \"Hiển thị trên các ứng dụng khác\" cho Dịch game, rồi quay lại.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_CAPTURE && res == RESULT_OK && data != null) {
            val q = Prefs(this)
            q.playOn = if (mode == "tv") "tv" else "phone"
            startForegroundService(Intent(this, CaptureService::class.java)
                .putExtra("code", res).putExtra("data", data)
                .putExtra("speak", q.speak).putExtra("lang", q.lang)
                .putExtra("automenu", q.autoMenu).putExtra("slow", q.slow).putExtra("narrate", q.narrate))
            CaptureService.active = true
            transBtn?.text = "💬"
            Toast.makeText(this, "Đang dịch. Bật phụ đề tiếng Anh trong game, kéo khung xanh tới dòng phụ đề.", Toast.LENGTH_LONG).show()
        }
    }
}
