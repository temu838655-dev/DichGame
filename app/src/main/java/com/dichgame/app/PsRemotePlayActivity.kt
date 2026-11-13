package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.metallic.chiaki.lib.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tích hợp Chiaki trực tiếp: ghép PS5/PS4 bằng IP + PSN Account ID + mã 8 số,
 * lưu khóa đăng ký trên thiết bị và mở phiên Remote Play trong app.
 */
class PsRemotePlayActivity : Activity(), SurfaceHolder.Callback {
    private val prefs by lazy { getSharedPreferences("ps_remote_play", MODE_PRIVATE) }
    private var regist: Regist? = null
    private var session: Session? = null
    private var surface: android.view.Surface? = null
    private var controllerState = ControllerState()
    private var isPs5 = true
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private val closing = AtomicBoolean(false)
    private val translationRequestCode = 911

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        status = TextView(this).apply {
            text = "Sẵn sàng"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(16, 10, 16, 10)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(10, 13, 20))
        }
        setContentView(content)
        if (prefs.contains("host")) showSavedHost() else showPairForm()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun field(hintText: String, value: String = "") = EditText(this).apply {
        hint = hintText; setSingleLine(true); setText(value)
        setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.rgb(35, 40, 50))
    }
    private fun action(label: String, click: () -> Unit) = Button(this).apply {
        text = label; setOnClickListener { click() }
    }
    private fun add(view: View, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) {
        content.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
            bottomMargin = dp(8)
        })
    }
    private fun reset(title: String, description: String) {
        content.removeAllViews()
        add(text(title, 23f))
        add(text(description, 13f))
        add(status)
    }

    private fun showPairForm() {
        reset("Ghép PlayStation", "Điện thoại và PS5/PS4 cần cùng mạng Wi‑Fi. Trên console mở Cài đặt → Hệ thống → Chơi từ xa → Ghép thiết bị để lấy mã 8 số.")
        val host = field("IP của PS5/PS4 (ví dụ 192.168.1.20)")
        val account = field("PSN Account ID (Base64, đúng 8 byte)")
        val pin = field("Mã ghép nối 8 chữ số")
        val type = action("Đang chọn: PS5  ·  Chạm để đổi sang PS4") {
            isPs5 = !isPs5
            (it as Button).text = if (isPs5) "Đang chọn: PS5  ·  Chạm để đổi sang PS4" else "Đang chọn: PS4  ·  Chạm để đổi sang PS5"
        }
        add(text("1. Địa chỉ IP máy PlayStation"))
        add(host)
        add(text("2. PSN Account ID"))
        add(account)
        add(text("Có thể dùng công cụ tra Account ID PSN đáng tin cậy. Không nhập mật khẩu PSN hoặc mã đăng nhập tài khoản vào đây."))
        add(text("3. Mã ghép nối"))
        add(pin)
        add(type)
        add(action("GHÉP MÁY") {
            val ip = host.text.toString().trim()
            val pinText = pin.text.toString().trim()
            val accountBytes = try { Base64.decode(account.text.toString().trim(), Base64.DEFAULT) } catch (_: Exception) { byteArrayOf() }
            if (ip.isBlank() || ip.contains(" ") || ip.length > 64) {
                host.error = "Nhập IP hợp lệ"; return@action
            }
            if (accountBytes.size != RegistInfo.ACCOUNT_ID_SIZE) {
                account.error = "Account ID phải giải mã thành đúng 8 byte Base64"; return@action
            }
            if (pinText.length != 8 || !pinText.all { it.isDigit() }) {
                pin.error = "Mã phải có đúng 8 chữ số"; return@action
            }
            register(ip, accountBytes, pinText.toInt())
        })
        if (prefs.contains("host")) add(action("Quay lại thiết bị đã ghép") { showSavedHost() })
    }

    private fun register(host: String, accountId: ByteArray, pin: Int) {
        regist?.dispose()
        status.text = "Đang ghép với PlayStation…"
        val target = if (isPs5) Target.PS5_1 else Target.PS4_10
        val info = RegistInfo(target, host, false, null, accountId, pin)
        val log = ChiakiLog(ChiakiLog.Level.ALL.value) { level, message ->
            android.util.Log.println(
                when (level) {
                    ChiakiLog.Level.ERROR.value -> android.util.Log.ERROR
                    ChiakiLog.Level.WARNING.value -> android.util.Log.WARN
                    else -> android.util.Log.INFO
                }, "DichGameChiaki", message
            )
        }
        try {
            regist = Regist(info, log) { event ->
                runOnUiThread {
                    when (event) {
                        is RegistEventSuccess -> {
                            val h = event.host
                            prefs.edit()
                                .putString("host", host)
                                .putBoolean("ps5", h.target.isPS5)
                                .putString("nickname", h.serverNickname)
                                .putString("regist_key", Base64.encodeToString(h.rpRegistKey, Base64.NO_WRAP))
                                .putString("rp_key", Base64.encodeToString(h.rpKey, Base64.NO_WRAP))
                                .putInt("rp_key_type", h.rpKeyType.toInt())
                                .apply()
                            regist?.dispose()
                            regist = null
                            status.text = "Đã ghép: ${h.serverNickname}"
                            showSavedHost()
                        }
                        is RegistEventFailed -> status.text = "Ghép thất bại. Kiểm tra IP, Account ID, mã 8 số và trạng thái Chơi từ xa."
                        is RegistEventCanceled -> status.text = "Đã hủy ghép nối."
                    }
                }
            }
        } catch (e: Exception) {
            status.text = "Không khởi tạo được engine: ${e.message ?: "lỗi không xác định"}"
        }
    }

    private fun showSavedHost() {
        reset("PlayStation đã ghép", "Thiết bị: ${prefs.getString("nickname", "PlayStation")}  ·  ${prefs.getString("host", "")}")
        add(text("Khóa ghép nối được lưu cục bộ trên điện thoại. Không chia sẻ dữ liệu khóa này."))
        add(action("▶  KẾT NỐI VÀ CHƠI") { startStream() })
        add(action("Ghép một máy khác") { showPairForm() })
        add(action("Xóa thiết bị đã lưu") {
            AlertDialog.Builder(this)
                .setTitle("Xóa thiết bị?")
                .setMessage("Khóa ghép nối sẽ bị xóa khỏi điện thoại.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa") { _, _ ->
                    prefs.edit().clear().apply()
                    showPairForm()
                }.show()
        })
    }

    private fun startStream() {
        val host = prefs.getString("host", null) ?: run { showPairForm(); return }
        val key = try { Base64.decode(prefs.getString("regist_key", ""), Base64.DEFAULT) } catch (_: Exception) { byteArrayOf() }
        val morning = try { Base64.decode(prefs.getString("rp_key", ""), Base64.DEFAULT) } catch (_: Exception) { byteArrayOf() }
        if (key.isEmpty() || morning.isEmpty()) {
            status.text = "Thiếu khóa đăng ký. Hãy ghép lại PlayStation."
            return
        }
        content.removeAllViews()
        controllerState = ControllerState()
        add(text("Đang mở Remote Play…", 18f))
        add(status)
        val video = SurfaceView(this)
        add(video, 0)
        video.layoutParams = (video.layoutParams as LinearLayout.LayoutParams).apply { height = 0; weight = 1f }
        video.holder.addCallback(this)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun control(label: String, mask: UInt) {
            val b = Button(this).apply { text = label }
            b.setOnTouchListener { _, event ->
                val s = session
                if (s != null && (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL)) {
                    controllerState.buttons = if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                        controllerState.buttons or mask
                    } else {
                        controllerState.buttons and mask.inv()
                    }
                    s.setControllerState(controllerState)
                }
                true
            }
            controls.addView(b, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        control("←", ControllerState.BUTTON_DPAD_LEFT)
        control("↑", ControllerState.BUTTON_DPAD_UP)
        control("↓", ControllerState.BUTTON_DPAD_DOWN)
        control("→", ControllerState.BUTTON_DPAD_RIGHT)
        control("✕", ControllerState.BUTTON_CROSS)
        control("○", ControllerState.BUTTON_MOON)
        control("□", ControllerState.BUTTON_BOX)
        control("△", ControllerState.BUTTON_PYRAMID)
        add(controls)
        add(action(if (CaptureService.active) "■  Dừng dịch phụ đề" else "🌐  Bắt đầu dịch phụ đề") {
            if (CaptureService.active) {
                startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP))
                CaptureService.active = false
                status.text = "Đã dừng dịch phụ đề."
            } else {
                requestTranslationCapture()
            }
        })
        try {
            val profile = ConnectVideoProfile.preset(VideoResolutionPreset.RES_720P, VideoFPSPreset.FPS_60, Codec.CODEC_H264)
            val info = ConnectInfo(prefs.getBoolean("ps5", true), host, key, morning, profile)
            val s = Session(info, null, false)
            session = s
            s.eventCallback = { event ->
                runOnUiThread {
                    when (event) {
                        is ConnectedEvent -> status.text = "Đã kết nối PlayStation · 720p / 60 FPS"
                        is LoginPinRequestEvent -> requestLoginPin(event.pinIncorrect)
                        is QuitEvent -> status.text = "Phiên chơi kết thúc: ${event.reason} ${event.reasonString.orEmpty()}"
                        is RumbleEvent -> { }
                    }
                }
            }
            s.start().also { if (!it.isSuccess) status.text = "Không khởi động được phiên: $it" }
            surface?.let { s.setSurface(it) }
        } catch (e: Exception) {
            status.text = "Lỗi mở Remote Play: ${e.message ?: "không rõ nguyên nhân"}"
        }
    }


    private fun requestTranslationCapture() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Cần quyền hiển thị bản dịch")
                .setMessage("Cho phép Dịch Game hiển thị phụ đề trên hình Remote Play.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Để sau", null).show()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), translationRequestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != translationRequestCode || resultCode != RESULT_OK || data == null) return
        val p = Prefs(this)
        val intent = Intent(this, CaptureService::class.java)
            .putExtra("code", resultCode)
            .putExtra("data", data)
            .putExtra("speak", p.speak)
            .putExtra("lang", p.lang)
            .putExtra("automenu", p.autoMenu)
            .putExtra("slow", p.slow)
            .putExtra("narrate", p.narrate)
        startForegroundService(intent)
        CaptureService.active = true
        status.text = "Đang dịch phụ đề trực tiếp trên hình PlayStation."
        Toast.makeText(this, "Đã bật dịch. Phụ đề sẽ nổi trên hình Remote Play.", Toast.LENGTH_LONG).show()
    }

    private fun requestLoginPin(incorrect: Boolean) {
        val input = EditText(this).apply { hint = "PIN đăng nhập Remote Play"; inputType = 2 }
        AlertDialog.Builder(this)
            .setTitle(if (incorrect) "PIN chưa đúng" else "Nhập PIN Remote Play")
            .setView(input)
            .setNegativeButton("Hủy") { _, _ -> finish() }
            .setPositiveButton("Gửi") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) session?.setLoginPin(value)
            }.show()
    }

    override fun surfaceCreated(holder: SurfaceHolder) { surface = holder.surface; session?.setSurface(surface) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { surface = holder.surface; session?.setSurface(surface) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surface = null; session?.setSurface(null) }

    override fun onDestroy() {
        if (closing.compareAndSet(false, true)) {
            try { regist?.stop(); regist?.dispose() } catch (_: Exception) {}
            regist = null
            try { session?.stop(); session?.dispose() } catch (_: Exception) {}
            session = null
        }
        super.onDestroy()
    }
}
