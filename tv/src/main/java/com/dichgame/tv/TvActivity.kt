package com.dichgame.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.View
import com.dichgame.update.Updater
import com.dichgame.update.Updater.S

/** Màn hình ghép nối: hiện địa chỉ TV và mã 8 số để nhập vào app Dịch game trên điện thoại. */
class TvActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var codeView: TextView
    private lateinit var info: TextView
    private lateinit var updBtn: Button
    private val updListener = Updater.Listener { showUpdate(it) }
    private val gold = Color.parseColor("#F4C38C")
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(14).toFloat()
        if (stroke != 0) setStroke(dp(3), stroke)
    }

    private fun btn(t: String, a: () -> Unit): Button = Button(this).apply {
        text = t
        setAllCaps(false)
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        // chọn bằng remote: viền vàng + nền vàng nhạt
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(Color.parseColor("#3A2E1E"), gold))
            addState(intArrayOf(android.R.attr.state_pressed), shape(Color.parseColor("#3A2E1E"), gold))
            addState(intArrayOf(), shape(Color.parseColor("#2A2A2E")))
        }
        setPadding(dp(24), dp(14), dp(24), dp(14))
        setOnClickListener { a() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // Cập nhật ngay trong app: số bản dựng và tên file APK của app TV trong bản phát hành
        Updater.init(applicationContext, BuildConfig.BUILD_NO, "DichGame-TV.apk")
        // Chỉ bản debug (kiểm thử trên máy giả lập): kiểm tra bản mới ở máy chủ giả thay vì GitHub
        if (BuildConfig.DEBUG) intent?.getStringExtra("dg_update_api")?.let {
            com.dichgame.update.Update.testApi = it
            Updater.resetForTest()
        }
        startForegroundService(Intent(this, TvService::class.java))
        window.statusBarColor = Color.parseColor("#111113")
        window.navigationBarColor = Color.parseColor("#111113")

        val title = TextView(this).apply {
            text = "Dịch game TV"; textSize = 32f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        }
        val hint = TextView(this).apply {
            text = "Nhập mã dưới đây vào app Dịch game trên điện thoại (cùng Wi-Fi)."
            textSize = 18f; setTextColor(Color.parseColor("#B4B4BA"))
        }
        codeView = TextView(this).apply {
            textSize = 64f; setTextColor(gold); setTypeface(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.08f
        }
        info = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE); setLineSpacing(0f, 1.15f) }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fun add(b: Button) = addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            add(btn("Cấp quyền hiển thị trên ứng dụng khác") { grant() })
            add(btn("Bỏ qua tối ưu hóa pin (nên bật)") { battery() })
            add(btn("Gửi câu thử lên màn hình") {
                startService(Intent(this@TvActivity, TvService::class.java).setAction("test"))
            })
            add(btn("Xóa thiết bị đã ghép") {
                TvState.tokens.clear()
                getSharedPreferences("tv", MODE_PRIVATE).edit().clear().apply()
            })
            add(btn("Tắt app") {
                startService(Intent(this@TvActivity, TvService::class.java).setAction("stop"))
                finish()
            })
        }

        // Nút cập nhật: chỉ hiện khi có bản mới; chọn bằng remote rồi bấm OK, app tải và cài ngay tại đây
        updBtn = btn("") {}.apply { visibility = View.GONE }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111113"))
            setPadding(dp(56), dp(32), dp(56), dp(32))
            addView(title)
            addView(hint)
            addView(codeView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(info, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            addView(updBtn)
            addView(buttons)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#111113")); addView(root) })
        buttons.getChildAt(0).requestFocus()
    }

    override fun onStart() { super.onStart(); Updater.setVisible(true); Updater.addListener(updListener) }
    override fun onStop() { Updater.removeListener(updListener); Updater.setVisible(false); super.onStop() }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        Updater.onResume()
        showUpdate(Updater.state)
        Updater.check(false)
    }

    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun showUpdate(s: S) {
        fun show(text: String, click: () -> Unit) {
            val first = updBtn.visibility != View.VISIBLE
            updBtn.visibility = View.VISIBLE
            updBtn.text = text
            updBtn.setOnClickListener { click() }
            if (first) updBtn.requestFocus()
        }
        when (s) {
            is S.Available -> show("⬆ Có bản mới (bản dựng ${s.info.no}): bấm OK để cập nhật ngay trong app") { Updater.start(s.info) }
            is S.Downloading -> {
                val pct = if (s.total > 0) " ${(s.done * 100 / s.total).toInt()}%" else ""
                show("Đang tải bản mới$pct… (bấm OK để hủy)") { Updater.cancel() }
            }
            is S.Downloaded -> show("Đã tải xong: bấm OK để cài đặt") { Updater.installNow() }
            is S.NeedPermission -> show("Cho phép cài đặt: bấm OK để mở cài đặt, bật \"Dịch game TV\" rồi quay lại") {
                try { startActivity(Updater.permissionIntent()) }
                catch (_: Exception) {
                    Toast.makeText(this, "TV không mở được trang này. Dùng máy tính: adb shell appops set $packageName REQUEST_INSTALL_PACKAGES allow", Toast.LENGTH_LONG).show()
                }
            }
            is S.Installing -> show("Đang cài đặt… hãy bấm Cập nhật ở hộp thoại của TV") {}
            is S.Failed -> {
                val i = s.info
                if (i != null) show("Cập nhật chưa được: ${s.msg} (bấm OK để thử lại)") { Updater.start(i) }
                else updBtn.visibility = View.GONE
            }
            else -> updBtn.visibility = View.GONE
        }
    }

    private fun refresh() {
        val ok = Settings.canDrawOverlays(this)
        val c = TvState.code
        codeView.text = if (c.length == 8) c.substring(0, 4) + " " + c.substring(4) else c
        info.text = "Địa chỉ TV: ${ip()}   ·   Mã đổi mỗi 60 giây\n" +
            (if (ok) "Đã cấp quyền, sẵn sàng nhận phụ đề từ điện thoại."
            else "Chưa cấp quyền hiển thị trên ứng dụng khác. Nếu TV không mở được trang cài đặt, dùng máy tính: " +
                "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow")
    }

    private fun ip(): String = try {
        java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            .flatMap { java.util.Collections.list(it.inetAddresses) }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress ?: "chưa có mạng"
    } catch (e: Exception) { "chưa có mạng" }

    private fun grant() {
        val uri = Uri.parse("package:$packageName")
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)) }
        catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)) } catch (_: Exception) {}
        }
    }

    private fun battery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {}
    }
}
