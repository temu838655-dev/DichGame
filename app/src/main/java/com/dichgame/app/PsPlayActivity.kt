package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
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
    private var manual = false      // đã dừng hẳn, chờ người dùng bấm kết nối lại
    private var connected = false

    private var padState = ControllerState()
    private var phys = ControllerState()
    private var pad: PadView? = null
    private var padOn = true
    private var statusView: TextView? = null
    private var chipView: TextView? = null
    private var transBtn: TextView? = null
    private val ui = Handler(Looper.getMainLooper())

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
        if (console != null && session == null && !manual) connect()
    }

    override fun onStop() {
        super.onStop()
        // Rời màn hình thì dừng phiên: PS5 không cần giữ luồng hình cho một app đang ẩn.
        phys = ControllerState(); padState = ControllerState(); pad?.releaseAll()
        if (session != null) { shutdownSession(); if (!isFinishing) manual = false }
    }

    override fun onDestroy() {
        shutdownSession()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
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

    private fun connect() {
        val c = console ?: return
        if (session != null) return
        val g = ++gen
        manual = false
        connected = false
        refreshStatus()
        try {
            val info = PsStore.connectInfo(this, c, player)
            val s = Session(info, null, false)
            s.eventCallback = { ev -> runOnUiThread { if (g == gen && !isFinishing && !isDestroyed) onEvent(ev) } }
            val r = s.start()
            if (!r.isSuccess) {
                try { s.dispose() } catch (_: Throwable) {}
                ended("Không khởi động được phiên Remote Play: $r", true)
                return
            }
            session = s
            surface?.let { s.setSurface(it) }
            push()
        } catch (e: Throwable) {
            ended("Lỗi mở Remote Play: ${e.message ?: e.javaClass.simpleName}", true)
        }
    }

    private fun onEvent(ev: Event) {
        when (ev) {
            is ConnectedEvent -> { connected = true; setStatus("", false); push() }
            is LoginPinRequestEvent -> askPin(ev.pinIncorrect)
            is QuitEvent -> {
                shutdownSession()
                val why = ev.reason.toString() + (ev.reasonString?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                ended(if (ev.reason.isError) "Mất kết nối: $why" else "Phiên đã kết thúc: $why", ev.reason.isError)
            }
            is RumbleEvent -> {}
        }
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

    private fun shutdownSession() {
        gen++
        connected = false
        val s = session ?: return
        session = null
        Thread {
            try { s.stop() } catch (_: Throwable) {}
            try { s.dispose() } catch (_: Throwable) {}
        }.start()
    }

    private fun reconnect() {
        shutdownSession()
        manual = false
        refreshStatus()
        ui.postDelayed({ if (!isFinishing && !isDestroyed) connect() }, 1500)
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
                val v = et.text.toString().trim()
                if (v.isEmpty() || !v.all { it in '0'..'9' }) { Toast.makeText(this, "Mã chỉ gồm chữ số.", Toast.LENGTH_LONG).show(); askPin(incorrect) }
                else session?.setLoginPin(v)
            }.show()
    }

    // ---------------- Hình và điều khiển ----------------

    override fun surfaceCreated(holder: SurfaceHolder) { surface = holder.surface; session?.setSurface(holder.surface) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { surface = holder.surface; session?.setSurface(holder.surface) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surface = null; session?.setSurface(null) }

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

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_BACK && isPad(e.source)) {
            val down = e.action == KeyEvent.ACTION_DOWN
            when (e.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L2 -> { phys.l2State = if (down) UByte.MAX_VALUE else 0u.toUByte(); push(); return true }
                KeyEvent.KEYCODE_BUTTON_R2 -> { phys.r2State = if (down) UByte.MAX_VALUE else 0u.toUByte(); push(); return true }
            }
            val m = keyMask(e.keyCode)
            if (m != 0u) {
                phys.buttons = if (down) phys.buttons or m else phys.buttons and m.inv()
                push()
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if (isPad(e.source) && e.actionMasked == MotionEvent.ACTION_MOVE) {
            fun ax(a: Int): Float { val v = e.getAxisValue(a); return if (abs(v) < 0.1f) 0f else v }
            phys.leftX = (ax(MotionEvent.AXIS_X) * Short.MAX_VALUE).toInt().toShort()
            phys.leftY = (ax(MotionEvent.AXIS_Y) * Short.MAX_VALUE).toInt().toShort()
            phys.rightX = (ax(MotionEvent.AXIS_Z) * Short.MAX_VALUE).toInt().toShort()
            phys.rightY = (ax(MotionEvent.AXIS_RZ) * Short.MAX_VALUE).toInt().toShort()
            val lt = maxOf(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)).coerceIn(0f, 1f)
            val rt = maxOf(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)).coerceIn(0f, 1f)
            if (lt > 0.02f || phys.l2State != 0u.toUByte()) phys.l2State = (lt * 255f).toInt().toUByte()
            if (rt > 0.02f || phys.r2State != 0u.toUByte()) phys.r2State = (rt * 255f).toInt().toUByte()
            val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
            var b = phys.buttons and (ControllerState.BUTTON_DPAD_LEFT or ControllerState.BUTTON_DPAD_RIGHT or ControllerState.BUTTON_DPAD_UP or ControllerState.BUTTON_DPAD_DOWN).inv()
            if (hx < -0.5f) b = b or ControllerState.BUTTON_DPAD_LEFT
            if (hx > 0.5f) b = b or ControllerState.BUTTON_DPAD_RIGHT
            if (hy < -0.5f) b = b or ControllerState.BUTTON_DPAD_UP
            if (hy > 0.5f) b = b or ControllerState.BUTTON_DPAD_DOWN
            phys.buttons = b
            push()
            return true
        }
        return super.dispatchGenericMotionEvent(e)
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
