package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.metallic.chiaki.lib.*
import java.util.concurrent.atomic.AtomicBoolean
import java.net.InetSocketAddress

/**
 * Tích hợp Chiaki trực tiếp: ghép PS5/PS4 bằng IP + PSN Account ID + mã 8 số,
 * lưu khóa đăng ký trên thiết bị và mở phiên Remote Play trong app.
 */
class PsRemotePlayActivity : Activity(), SurfaceHolder.Callback {
    private val prefs by lazy { getSharedPreferences("ps_remote_play", MODE_PRIVATE) }
    private var regist: Regist? = null
    private var discovery: DiscoveryService? = null
    private var scanDialogShown = false
    private var session: Session? = null
    private var surface: android.view.Surface? = null
    private var controllerState = ControllerState()
    private var isPs5 = true
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private val closing = AtomicBoolean(false)
    private val streamEnded = AtomicBoolean(false)
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

    private fun pairingProgress(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val steps = listOf("①\\nTìm máy", "②\\nAccount ID", "③\\nMã trên máy")
        steps.forEachIndexed { index, label ->
            val item = TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) Color.WHITE else Color.LTGRAY)
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(if (index == 0) Color.rgb(78, 67, 55) else Color.rgb(35, 40, 50))
                    setStroke(dp(1), if (index == 0) Color.rgb(255, 190, 110) else Color.rgb(75, 82, 94))
                }
            }
            row.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        return row
    }

    private fun showPairForm() {
        reset("Ghép PlayStation", "Điện thoại và PS5/PS4 cần cùng mạng Wi‑Fi. Trên console mở Cài đặt → Hệ thống → Chơi từ xa → Ghép thiết bị để lấy mã 8 số.")
        add(pairingProgress())
        val host = field("IP của PS5/PS4 (ví dụ 192.168.1.20)")
        val account = field("PSN Account ID (Base64, đúng 8 byte)").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val pin = field("Mã ghép nối 8 chữ số").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val type = Button(this).apply {
            text = "Đang chọn: PS5  ·  Chạm để đổi sang PS4"
            setOnClickListener {
                isPs5 = !isPs5
                text = if (isPs5) "Đang chọn: PS5  ·  Chạm để đổi sang PS4" else "Đang chọn: PS4  ·  Chạm để đổi sang PS5"
            }
        }
        add(text("1. Địa chỉ IP máy PlayStation"))
        add(host)
        add(action("⌕  TÌM PS5 / PS4 TRONG MẠNG WI‑FI") { scanConsoles(host) })
        add(text("2. PSN Account ID"))
        add(account)
        add(action("ⓘ  HƯỚNG DẪN LẤY PSN ACCOUNT ID") { showAccountIdGuide() })
        add(text("Chỉ cần Account ID dạng Base64. Không nhập mật khẩu PSN hoặc mã đăng nhập tài khoản vào đây."))
        add(text("3. Mã ghép nối"))
        add(pin)
        add(type)
        add(action("GHÉP MÁY") {
            val ip = host.text.toString().trim()
            val pinText = pin.text.toString().trim()
            val accountBytes = try { Base64.decode(account.text.toString().trim(), Base64.DEFAULT) } catch (_: Exception) { byteArrayOf() }
            if (!isValidHostAddress(ip)) {
                host.error = "Nhập địa chỉ IP hợp lệ của PS5/PS4 (IPv4 hoặc IPv6), không nhập tên miền"; return@action
            }
            if (accountBytes.size != RegistInfo.ACCOUNT_ID_SIZE) {
                account.error = "Account ID phải giải mã thành đúng 8 byte Base64"; return@action
            }
            if (pinText.length != 8 || !pinText.all { it in '0'..'9' }) {
                pin.error = "Mã phải có đúng 8 chữ số"; return@action
            }
            register(ip, accountBytes, pinText.toInt())
        })
        if (prefs.contains("host")) add(action("Quay lại thiết bị đã ghép") { showSavedHost() })
    }

    private fun showAccountIdGuide() {
        val guide = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        fun step(number: String, heading: String, body: String) {
            guide.addView(text("$number  $heading", 17f))
            guide.addView(text(body, 14f))
        }
        step("1.", "Chọn người chơi phụ trên PS5",
            "Trên PS5, thêm hoặc chọn một người dùng phụ đã đăng nhập PSN. Không dùng tài khoản đang chơi game trên TV vì phiên Remote Play có thể làm gián đoạn người chơi đó.")
        step("2.", "Tìm Online ID",
            "Mở Hồ sơ của người dùng phụ. Online ID là tên công khai của tài khoản, không phải địa chỉ email và cũng không phải mật khẩu.")
        step("3.", "Tra Account ID và mã hóa Base64",
            "Dùng công cụ tra cứu Account ID PSN đáng tin cậy để lấy chuỗi Base64 tương ứng. Dán nguyên chuỗi vào ô PSN Account ID trong app.")
        step("4.", "Lấy mã ghép nối trên console",
            "Trên PS5: Cài đặt → Hệ thống → Chơi từ xa → Ghép thiết bị. Nhập mã 8 chữ số còn hiệu lực vào app. Với PS4, bật Chơi từ xa và mở chức năng thêm thiết bị để lấy mã.")
        guide.addView(text("Bảo mật: không gửi mật khẩu, mã xác minh hai bước, cookie hoặc token đăng nhập cho bất kỳ ai.", 13f))
        AlertDialog.Builder(this)
            .setTitle("Hướng dẫn ghép PS5 / PS4")
            .setView(guide)
            .setPositiveButton("Đã hiểu", null)
            .show()
    }

    private fun isValidHostAddress(value: String): Boolean {
        if (value.isBlank() || value.length > 64 || value.any { it.isWhitespace() }) return false
        if (value.contains(':')) {
            // Chỉ phân tích IPv6 literal; không cho phép tra cứu hostname qua DNS.
            return try {
                java.net.InetAddress.getByName(value) is java.net.Inet6Address
            } catch (_: Exception) {
                false
            }
        }
        val octets = value.split('.')
        return octets.size == 4 && octets.all { part ->
            part.isNotEmpty() && part.length <= 3 &&
                part.all { it in '0'..'9' } &&
                (part.toIntOrNull() ?: -1) in 0..255
        }
    }

    private fun scanConsoles(hostField: EditText) {
        try {
            discovery?.dispose()
            scanDialogShown = false
            status.text = "Đang tìm máy PlayStation trong mạng nội bộ…"
            discovery = DiscoveryService(DiscoveryServiceOptions(16U, 3U, 500U, InetSocketAddress("255.255.255.255", 987))) { hosts ->
                runOnUiThread {
                    if (scanDialogShown || hosts.isEmpty()) return@runOnUiThread
                    val usable = hosts.filter { !it.hostAddr.isNullOrBlank() }
                    if (usable.isEmpty()) return@runOnUiThread
                    scanDialogShown = true
                    val labels = usable.map { h ->
                        val kind = if (h.isPS5) "PS5" else "PS4"
                        val state = if (h.state == DiscoveryHost.State.STANDBY) "Chế độ nghỉ" else "Sẵn sàng"
                        kind + " · " + (h.hostName ?: "PlayStation") + " · " + h.hostAddr + " · " + state
                    }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("Chọn máy PlayStation")
                        .setItems(labels) { _, which ->
                            val selected = usable[which]
                            hostField.setText(selected.hostAddr)
                            isPs5 = selected.isPS5
                            status.text = "Đã tìm thấy " + (selected.hostName ?: "PlayStation")
                            discovery?.dispose()
                            discovery = null
                            scanDialogShown = false
                        }
                        .setNegativeButton("Đóng") { _, _ -> discovery?.dispose(); discovery = null; scanDialogShown = false }
                        .show()
                }
            }
            Toast.makeText(this, "Đang quét mạng. Có thể mất vài giây.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status.text = "Không thể khởi động quét mạng: " + (e.message ?: "lỗi không rõ")
        }
    }

    private fun register(host: String, accountId: ByteArray, pin: Int) {
        regist?.dispose()
        status.text = "Đang ghép với PlayStation…"
        val target = if (isPs5) com.metallic.chiaki.lib.Target.PS5_1 else com.metallic.chiaki.lib.Target.PS4_10
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
                    if (isFinishing || isDestroyed) return@runOnUiThread
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
        add(action("▶  KẾT NỐI VÀ CHƠI") { showPlayQualityDialog() })
        add(action("Chất lượng hình & tốc độ mạng") { showVideoQualityInfo() })
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

    /**
     * Hộp thoại xác nhận chất lượng trước khi chơi.
     * Tùy chọn "không hỏi lại" được lưu cục bộ; preset hiện tại là 720p60.
     */
    private fun showPlayQualityDialog() {
        if (prefs.getBoolean("skip_quality_dialog", false)) {
            startStream()
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(4))
        }
        panel.addView(text("ĐỘ PHÂN GIẢI", 13f))
        val selected = TextView(this).apply {
            text = "✓  720p · 60 FPS · H.264"
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(61, 55, 48))
        }
        panel.addView(selected)
        panel.addView(text("Đây là cấu hình đang được engine sử dụng. 1080p chưa được bật trong phiên bản này."))
        panel.addView(text("KẾT NỐI MẠNG", 13f).apply { setPadding(0, dp(12), 0, 0) })
        panel.addView(text("Ưu tiên Wi‑Fi 5 GHz hoặc Ethernet. Nếu hình giật hoặc âm thanh ngắt quãng, hãy đưa điện thoại gần bộ định tuyến hơn và giảm tải mạng."))
        val skip = CheckBox(this).apply {
            text = "Không hỏi lại khi bấm Chơi"
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(255, 190, 110))
            setPadding(0, dp(8), 0, 0)
        }
        panel.addView(skip)

        AlertDialog.Builder(this)
            .setTitle("Chất lượng hình")
            .setView(panel)
            .setNegativeButton("Đóng", null)
            .setPositiveButton("🎮  Chơi") { _, _ ->
                prefs.edit().putBoolean("skip_quality_dialog", skip.isChecked).apply()
                startStream()
            }
            .show()
    }

    /**
     * Hiển thị rõ cấu hình thực sự đang được engine dùng.
     * Không đưa ra lựa chọn 1080p/bitrate giả khi API Chiaki tích hợp hiện
     * mới được cấu hình bằng preset 720p60 H.264.
     */
    private fun showVideoQualityInfo() {
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        details.addView(text("ĐỘ PHÂN GIẢI", 13f))
        details.addView(text("720p · 60 FPS · H.264", 20f))
        details.addView(text("Đây là cấu hình Remote Play đang được bật trong engine hiện tại."))
        details.addView(text("1080p", 13f).apply { setPadding(0, dp(14), 0, 0) })
        details.addView(text("Chưa bật: cần xác minh preset và khả năng giải mã trên thiết bị trước khi cung cấp lựa chọn này."))
        details.addView(text("TỐC ĐỘ MẠNG", 13f).apply { setPadding(0, dp(14), 0, 0) })
        details.addView(text("Nên dùng Wi-Fi 5 GHz hoặc mạng LAN ổn định. Chất lượng thực tế còn phụ thuộc độ trễ, mất gói và khả năng tải lên của mạng tại nhà."))
        AlertDialog.Builder(this)
            .setTitle("Chất lượng hình")
            .setView(details)
            .setPositiveButton("Đã hiểu", null)
            .show()
    }

    private fun startStream() {
        if (session != null) {
            status.text = "Phiên Remote Play đang được khởi tạo hoặc đang chạy."
            return
        }
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
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(top)
        fun addButton(parent: LinearLayout, label: String, mask: UInt) {
            val b = Button(this).apply { text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0 }
            b.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN ||
                    event.action == android.view.MotionEvent.ACTION_UP ||
                    event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    controllerState.buttons = if (event.action == android.view.MotionEvent.ACTION_DOWN)
                        controllerState.buttons or mask else controllerState.buttons and mask.inv()
                    session?.setControllerState(controllerState)
                }
                true
            }
            parent.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        addButton(top, "SHARE", ControllerState.BUTTON_SHARE)
        addButton(top, "L1", ControllerState.BUTTON_L1)
        addButton(top, "OPTIONS", ControllerState.BUTTON_OPTIONS)
        addButton(top, "R1", ControllerState.BUTTON_R1)
        addButton(top, "PS", ControllerState.BUTTON_PS)

        val mid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(mid)
        val dpad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        mid.addView(dpad, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addButton(dpad, "↑", ControllerState.BUTTON_DPAD_UP)
        val drow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        dpad.addView(drow)
        addButton(drow, "←", ControllerState.BUTTON_DPAD_LEFT)
        addButton(drow, "↓", ControllerState.BUTTON_DPAD_DOWN)
        addButton(drow, "→", ControllerState.BUTTON_DPAD_RIGHT)
        val face = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        mid.addView(face, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addButton(face, "△", ControllerState.BUTTON_PYRAMID)
        val faceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        face.addView(faceRow)
        addButton(faceRow, "□", ControllerState.BUTTON_BOX)
        addButton(faceRow, "○", ControllerState.BUTTON_MOON)
        addButton(face, "✕", ControllerState.BUTTON_CROSS)

        val sticks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(sticks)
        sticks.addView(AnalogStickView(true), LinearLayout.LayoutParams(0, dp(112), 1f))
        val touchpad = TouchpadView()
        sticks.addView(touchpad, LinearLayout.LayoutParams(0, dp(84), 1f))
        sticks.addView(AnalogStickView(false), LinearLayout.LayoutParams(0, dp(112), 1f))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(bottom)
        addButton(bottom, "L3", ControllerState.BUTTON_L3)
        addButton(bottom, "R3", ControllerState.BUTTON_R3)
        fun trigger(label: String, left: Boolean) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(label, 11f))
            val seek = SeekBar(this).apply {
                max = 255
                progress = 0
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (left) controllerState.l2State = progress.toUByte() else controllerState.r2State = progress.toUByte()
                        session?.setControllerState(controllerState)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        seekBar?.progress = 0
                        if (left) controllerState.l2State = 0U else controllerState.r2State = 0U
                        session?.setControllerState(controllerState)
                    }
                })
            }
            col.addView(seek)
            bottom.addView(col, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        trigger("L2", true)
        trigger("R2", false)
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
            streamEnded.set(false)
            val profile = ConnectVideoProfile.preset(VideoResolutionPreset.RES_720P, VideoFPSPreset.FPS_60, Codec.CODEC_H264)
            val info = ConnectInfo(prefs.getBoolean("ps5", true), host, key, morning, profile)
            val s = Session(info, null, false)
            session = s
            s.eventCallback = { event ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    when (event) {
                        is ConnectedEvent -> status.text = "Đã kết nối PlayStation · 720p / 60 FPS"
                        is LoginPinRequestEvent -> requestLoginPin(event.pinIncorrect)
                        is QuitEvent -> {
                            if (streamEnded.compareAndSet(false, true)) {
                                showStreamEnded("Phiên chơi kết thúc: ${event.reason} ${event.reasonString.orEmpty()}")
                            }
                        }
                        is RumbleEvent -> { }
                    }
                }
            }
            val startResult = s.start()
            if (!startResult.isSuccess) {
                if (streamEnded.compareAndSet(false, true)) {
                    showStreamEnded("Không khởi động được phiên Remote Play: $startResult")
                }
                return
            }
            surface?.let { s.setSurface(it) }
        } catch (e: Exception) {
            if (streamEnded.compareAndSet(false, true)) {
                showStreamEnded("Lỗi mở Remote Play: ${e.message ?: "không rõ nguyên nhân"}")
            }
        }
    }

    private fun showStreamEnded(message: String) {
        if (isFinishing || isDestroyed) return
        status.text = message
        add(action("↻  THỬ KẾT NỐI LẠI") {
            releaseSession()
            startStream()
        })
        add(action("Ghép lại / chọn máy khác") {
            releaseSession()
            showPairForm()
        })
    }

    private fun releaseSession() {
        try { session?.stop() } catch (_: Exception) {}
        try { session?.dispose() } catch (_: Exception) {}
        session = null
        controllerState = ControllerState()
    }



    private inner class AnalogStickView(private val left: Boolean) : View(this@PsRemotePlayActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var nx = 0f
        private var ny = 0f
        private var active = false
        init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) * 0.38f).coerceAtLeast(1f)
            paint.color = 0x553F536B
            canvas.drawCircle(cx, cy, radius, paint)
            paint.color = 0xFF8295AA.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (active) 0xFF00C8D7.toInt() else 0xFFB7C4D4.toInt()
            canvas.drawCircle(cx + nx * radius * 0.72f, cy + ny * radius * 0.72f, radius * 0.32f, paint)
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val radius = (minOf(width, height) * 0.38f).coerceAtLeast(1f)
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                    active = true
                    nx = ((event.x - width / 2f) / radius).coerceIn(-1f, 1f)
                    ny = ((event.y - height / 2f) / radius).coerceIn(-1f, 1f)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    active = false; nx = 0f; ny = 0f
                }
                else -> return true
            }
            val dead = 0.08f
            val x = if (kotlin.math.abs(nx) < dead) 0f else nx
            val y = if (kotlin.math.abs(ny) < dead) 0f else ny
            if (left) {
                controllerState.leftX = (x * 32767).toInt().toShort()
                controllerState.leftY = (y * 32767).toInt().toShort()
            } else {
                controllerState.rightX = (x * 32767).toInt().toShort()
                controllerState.rightY = (y * 32767).toInt().toShort()
            }
            session?.setControllerState(controllerState)
            invalidate()
            return true
        }
    }

    private inner class TouchpadView : View(this@PsRemotePlayActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var touchId: UByte? = null
        init { setBackgroundColor(0x553F536B) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = Color.WHITE
            paint.textSize = dp(12).toFloat()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("TOUCHPAD", width / 2f, height / 2f + dp(4), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            canvas.drawRoundRect(2f, 2f, width - 2f, height - 2f, dp(8).toFloat(), dp(8).toFloat(), paint)
            paint.style = Paint.Style.FILL
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val x = ((event.x / width.coerceAtLeast(1)) * ControllerState.TOUCHPAD_WIDTH.toInt()).toInt()
                .coerceIn(0, ControllerState.TOUCHPAD_WIDTH.toInt()).toUShort()
            val y = ((event.y / height.coerceAtLeast(1)) * ControllerState.TOUCHPAD_HEIGHT.toInt()).toInt()
                .coerceIn(0, ControllerState.TOUCHPAD_HEIGHT.toInt()).toUShort()
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    controllerState.buttons = controllerState.buttons or ControllerState.BUTTON_TOUCHPAD
                    touchId = controllerState.startTouch(x, y)
                }
                android.view.MotionEvent.ACTION_MOVE -> touchId?.let { controllerState.setTouchPos(it, x, y) }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    touchId?.let { controllerState.stopTouch(it) }
                    touchId = null
                    controllerState.buttons = controllerState.buttons and ControllerState.BUTTON_TOUCHPAD.inv()
                }
            }
            session?.setControllerState(controllerState)
            return true
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
        val input = EditText(this).apply {
            hint = "PIN đăng nhập Remote Play"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
        }
        AlertDialog.Builder(this)
            .setTitle(if (incorrect) "PIN chưa đúng" else "Nhập PIN Remote Play")
            .setView(input)
            .setNegativeButton("Hủy") { _, _ -> finish() }
            .setPositiveButton("Gửi") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isEmpty() || value.length > 8 || !value.all { it in '0'..'9' }) {
                    Toast.makeText(this, "PIN chỉ được gồm tối đa 8 chữ số.", Toast.LENGTH_LONG).show()
                    requestLoginPin(incorrect)
                } else {
                    session?.setLoginPin(value)
                }
            }.show()
    }

    override fun surfaceCreated(holder: SurfaceHolder) { surface = holder.surface; session?.setSurface(surface) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { surface = holder.surface; session?.setSurface(surface) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surface = null; session?.setSurface(null) }

    override fun onDestroy() {
        if (closing.compareAndSet(false, true)) {
            try { discovery?.dispose() } catch (_: Exception) {}
            discovery = null
            try { regist?.stop(); regist?.dispose() } catch (_: Exception) {}
            regist = null
            try { session?.stop(); session?.dispose() } catch (_: Exception) {}
            session = null
        }
        super.onDestroy()
    }
}
