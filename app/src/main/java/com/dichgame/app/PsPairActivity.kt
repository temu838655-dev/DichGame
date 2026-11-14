package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.metallic.chiaki.lib.ChiakiLog
import com.metallic.chiaki.lib.CreateError
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.DiscoveryService
import com.metallic.chiaki.lib.DiscoveryServiceOptions
import com.metallic.chiaki.lib.Regist
import com.metallic.chiaki.lib.RegistEvent
import com.metallic.chiaki.lib.RegistEventCanceled
import com.metallic.chiaki.lib.RegistEventFailed
import com.metallic.chiaki.lib.RegistEventSuccess
import com.metallic.chiaki.lib.RegistInfo
import java.net.InetSocketAddress

/**
 * Ghép nối PS5/PS4 trong 3 bước: Tìm máy, Mã người chơi (PSN account ID), Mã 8 số hiện trên máy.
 * Ghép được nhiều người chơi; nên ghép một người chơi phụ để điện thoại vào cùng lúc với người đang chơi trên TV.
 */
class PsPairActivity : Activity() {
    private var step = 1
    private var host = ""
    private var ps5 = true
    private var accountText = ""
    private var accountId: ByteArray? = null
    private var pin = ""

    private var discovery: DiscoveryService? = null
    private var foundBox: LinearLayout? = null
    private var found: List<DiscoveryHost> = emptyList()

    private var regist: Regist? = null
    private var running = false
    private var status = ""
    private val log = StringBuilder()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        PsStore.load(this)?.let { host = it.host; ps5 = it.ps5 }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (step == 1) startDiscovery()
    }

    override fun onPause() {
        super.onPause()
        stopDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        val r = regist
        regist = null
        if (r != null) Thread { try { r.stop(); r.dispose() } catch (_: Throwable) {} }.start()
    }

    // ---------------- Tìm máy trong mạng ----------------

    private fun startDiscovery() {
        if (discovery != null || !PsNative.available) return
        try {
            discovery = DiscoveryService(DiscoveryServiceOptions(16UL, 3UL, 500UL, InetSocketAddress("255.255.255.255", 987))) { hosts ->
                runOnUiThread { found = hosts; drawFound() }
            }
        } catch (_: CreateError) { discovery = null } catch (_: Throwable) { discovery = null }
    }

    private fun stopDiscovery() {
        val d = discovery ?: return
        discovery = null
        Thread { try { d.dispose() } catch (_: Throwable) {} }.start()
    }

    private fun drawFound() {
        val box = foundBox ?: return
        box.removeAllViews()
        val list = found.filter { !it.hostAddr.isNullOrEmpty() }
        if (list.isEmpty()) {
            box.addView(Ui.tv(this, "Đang tìm máy trong mạng Wi-Fi…", 13f, Ui.SUB))
            return
        }
        for (h in list) {
            val state = when (h.state) {
                DiscoveryHost.State.READY -> "Sẵn sàng"
                DiscoveryHost.State.STANDBY -> "Đang nghỉ"
                else -> ""
            }
            val label = (h.hostName ?: if (h.isPS5) "PS5" else "PS4") + "  ·  " + h.hostAddr + (if (state.isNotEmpty()) "  ·  $state" else "")
            box.addView(Ui.button(this, label) {
                host = h.hostAddr ?: ""
                ps5 = h.isPS5
                step = 2
                stopDiscovery()
                render()
            }, Ui.lpm(this, 6, 0))
        }
    }

    // ---------------- Giao diện ----------------

    private fun render() {
        screen("Ghép nối " + (if (ps5) "PS5" else "PS4")) {
            put(stepBar(), 0, 12)
            when (step) {
                1 -> stepFind(this)
                2 -> stepAccount(this)
                else -> stepPin(this)
            }
        }
        if (step == 1) { drawFound(); startDiscovery() }
    }

    private fun stepBar(): LinearLayout {
        val bar = Ui.hbox(this)
        val names = listOf("Tìm máy", "Mã người chơi", "Mã trên máy")
        names.forEachIndexed { i, n ->
            val on = step == i + 1
            val done = step > i + 1
            bar.addView(TextView(this).apply {
                text = (if (done) "✓ " else "${i + 1}  ") + n
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(if (on) Color.parseColor("#2A1A08") else if (done) Ui.OK else Ui.SUB)
                background = if (on) Ui.goldBg(this@PsPairActivity, 16) else Ui.bg(this@PsPairActivity, Ui.CARD2, 16, Ui.LINE, 1)
                setPadding(Ui.dp(this@PsPairActivity, 10), Ui.dp(this@PsPairActivity, 8), Ui.dp(this@PsPairActivity, 10), Ui.dp(this@PsPairActivity, 8))
            }, Ui.lp(0, WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PsPairActivity, 6) })
        }
        return bar
    }

    private fun stepFind(root: LinearLayout) {
        if (!PsNative.available) {
            root.put(note("Máy này chưa hỗ trợ chơi PS5 trong app", "Phần chơi PS5 cần điện thoại chip arm64 (hầu hết điện thoại đời mới). Bản APK cho máy ảo hoặc chip cũ không có phần này. Bạn vẫn dùng được app chơi từ xa khác và dịch đè lên."), 0, 12)
            return
        }
        val c = Ui.card(this)
        c.addView(Ui.tv(this, "Chuẩn bị trên máy chơi game", 16f, Ui.TEXT, true))
        c.addView(Ui.tv(this,
            "1. Vào Cài đặt › Hệ thống › Chơi từ xa, bật \"Bật Chơi từ xa\".\n" +
                "2. Điện thoại và máy chơi game dùng chung Wi-Fi ở nhà (không dùng Wi-Fi khách), tắt VPN.\n" +
                "3. Muốn app tự bật máy đang nghỉ: Cài đặt › Hệ thống › Tiết kiệm điện › Tính năng khi nghỉ, bật \"Giữ kết nối Internet\" và \"Cho phép bật máy qua mạng\".",
            13.5f, Ui.TEXT), Ui.lpm(this, 8, 0))
        root.put(c, 0, 12)

        root.put(Ui.segmented(this, listOf("PS5", "PS4 (từ bản 8)"), if (ps5) 0 else 1) { ps5 = it == 0; render() }, 0, 12)

        val f = Ui.card(this)
        f.addView(Ui.tv(this, "Máy tìm thấy trong mạng", 16f, Ui.TEXT, true))
        val box = Ui.vbox(this)
        foundBox = box
        f.addView(box, Ui.lpm(this, 6, 0))
        root.put(f, 0, 12)

        val m = Ui.card(this)
        m.addView(Ui.tv(this, "Hoặc nhập địa chỉ IP", 16f, Ui.TEXT, true))
        m.addView(Ui.tv(this, "Không thấy máy? Trên máy chơi game vào Cài đặt › Mạng › Trạng thái kết nối › Xem trạng thái kết nối, lấy dòng IPv4 Address (dạng 192.168.x.x).", 12.5f, Ui.SUB), Ui.lpm(this, 4, 8))
        val et = Ui.field(this, "192.168.1.20", host)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        m.addView(et)
        m.addView(Ui.button(this, "Tiếp tục", true) {
            val h = et.text.toString().trim()
            if (!Regex("^[0-9A-Za-z.:-]{3,80}$").matches(h)) toast("Địa chỉ chưa đúng.") else { host = h; step = 2; stopDiscovery(); render() }
        }, Ui.lpm(this, 10, 0))
        root.put(m, 0, 0)
    }

    private fun stepAccount(root: LinearLayout) {
        val w = Ui.card(this)
        w.background = Ui.bg(this, Color.parseColor("#3A2E1F"), 20, Ui.GOLD, 1)
        w.addView(Ui.tv(this, "👥  Nên dùng người chơi phụ", 16f, Ui.GOLD, true))
        w.addView(Ui.tv(this,
            "Lấy mã của một người chơi khác, không phải người bạn đang chơi trên TV. Dùng chung người đó thì tay cầm trên TV bị ngắt.\n" +
                "Chưa có người chơi phụ? Trên máy chơi game: ảnh đại diện › Chuyển người dùng › Thêm người dùng, đăng nhập một lần bằng tài khoản PlayStation miễn phí (không cần mua game hay PS Plus). Sau đó bật Chơi từ xa cho người đó.",
            13.5f, Ui.TEXT), Ui.lpm(this, 6, 0))
        root.put(w, 0, 12)

        val s = Ui.card(this)
        s.addView(Ui.tv(this, "Bước 2: Lấy mã người chơi", 16f, Ui.TEXT, true))
        s.addView(Ui.tv(this,
            "1. Trên máy chơi game, vào người chơi phụ: ảnh đại diện › Hồ sơ. Tên đăng nhập (Online ID) nằm ngay dưới tên.\n" +
                "2. Bấm \"Mở trang tra mã\" bên dưới, gõ Online ID đó rồi bấm Search.\n" +
                "3. Sao chép dòng \"BASE64 (FOR REMOTE PLAY)\", chuỗi kết thúc bằng dấu =. Đừng lấy dòng HEX hay NPID.\n" +
                "4. Quay lại app, bấm Dán mã.",
            13.5f, Ui.TEXT), Ui.lpm(this, 8, 10))
        s.addView(Ui.button(this, "🧭  Mở trang tra mã") { web("https://www.psntools.com/psn/checker") })
        s.addView(Ui.tv(this, "Đây là trang của bên thứ ba, không thuộc Dịch game.", 11.5f, Ui.SUB), Ui.lpm(this, 4, 0))
        root.put(s, 0, 12)

        val f = Ui.card(this)
        f.addView(Ui.tv(this, "Mã người chơi (BASE64)", 14f, Ui.TEXT, true))
        val et = Ui.field(this, "Ví dụ: AbCdEfGhIjk=", accountText)
        f.addView(et, Ui.lpm(this, 6, 0))
        val err = Ui.tv(this, "", 12.5f, Ui.DANGER)
        f.addView(err, Ui.lpm(this, 4, 0))
        val r = Ui.hbox(this)
        r.addView(Ui.button(this, "📋  Dán mã") {
            val t = clipboardText()
            if (t.isEmpty()) toast("Chưa có gì trong bộ nhớ tạm.") else et.setText(t.trim())
        }, Ui.lp(0, WRAP_CONTENT, 1f))
        r.addView(Ui.button(this, "Tiếp tục", true) {
            accountText = et.text.toString().trim()
            val id = decodeAccount(accountText)
            if (id == null) err.text = "Chưa đúng mã. Cần chuỗi BASE64 đủ cả dấu = ở cuối, lấy từ dòng BASE64 (FOR REMOTE PLAY)."
            else { accountId = id; step = 3; render() }
        }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@PsPairActivity, 10) })
        f.addView(r, Ui.lpm(this, 10, 0))
        root.put(f, 0, 8)
        root.put(Ui.button(this, "← Quay lại") { step = 1; render() }, 0, 0)
    }

    private fun stepPin(root: LinearLayout) {
        val s = Ui.card(this)
        s.addView(Ui.tv(this, "Bước 3: Mã 8 số trên máy", 16f, Ui.TEXT, true))
        s.addView(Ui.tv(this,
            "Trên máy chơi game, đúng người chơi phụ đã lấy mã ở bước trước, vào:\n" +
                "Cài đặt › Hệ thống › Chơi từ xa › Ghép nối thiết bị.\n" +
                "TV hiện 8 chữ số kèm thời gian còn lại. Nhập ngay vào ô dưới rồi bấm Ghép nối.",
            13.5f, Ui.TEXT), Ui.lpm(this, 8, 0))
        root.put(s, 0, 12)

        val f = Ui.card(this)
        f.addView(Ui.tv(this, "Máy: $host", 13f, Ui.SUB))
        val et = Ui.field(this, "8 chữ số", pin)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.filters = arrayOf<android.text.InputFilter>(android.text.InputFilter.LengthFilter(8))
        et.textSize = 22f
        et.gravity = Gravity.CENTER
        f.addView(et, Ui.lpm(this, 8, 0))
        val st = Ui.tv(this, status, 13f, if (running) Ui.GOLD else Ui.DANGER)
        f.addView(st, Ui.lpm(this, 8, 0))
        val go = Ui.button(this, if (running) "Đang ghép nối…" else "Ghép nối", true) {
            if (running) return@button
            pin = et.text.toString()
            if (parsePairingPin(pin) == null) { st.text = "Mã phải gồm đúng 8 chữ số từ 0 đến 9."; st.setTextColor(Ui.DANGER); return@button }
            doRegist()
        }
        f.addView(go, Ui.lpm(this, 10, 0))
        if (log.isNotEmpty() && !running) f.addView(Ui.button(this, "Xem chi tiết lỗi") { showLog() }, Ui.lpm(this, 8, 0))
        root.put(f, 0, 8)
        root.put(Ui.button(this, "← Quay lại") { if (!running) { step = 2; render() } }, 0, 0)
    }

    private fun note(title: String, body: String): LinearLayout {
        val c = Ui.card(this)
        c.addView(Ui.tv(this, title, 16f, Ui.GOLD, true))
        c.addView(Ui.tv(this, body, 13.5f, Ui.TEXT), Ui.lpm(this, 6, 0))
        return c
    }

    // ---------------- Ghép nối ----------------

    private fun decodeAccount(s: String): ByteArray? {
        if (s.isEmpty()) return null
        return try {
            val b = Base64.decode(s, Base64.DEFAULT)
            if (b.size == RegistInfo.ACCOUNT_ID_SIZE) b else null
        } catch (_: IllegalArgumentException) { null }
    }

    private fun doRegist() {
        val id = accountId ?: return
        val pairingCode = parsePairingPin(pin)
        if (pairingCode == null) {
            running = false
            status = "Mã ghép nối không hợp lệ. Hãy nhập đúng 8 chữ số từ 0 đến 9."
            render()
            return
        }
        log.setLength(0)
        running = true
        status = "Đang ghép nối, chờ vài giây…"
        render()
        val info = RegistInfo(PsStore.target(ps5), host, false, null, id, pairingCode)
        val lg = ChiakiLog(ChiakiLog.Level.ALL.value) { level, text ->
            synchronized(log) { if (log.length < 20000) log.append(ChiakiLog.formatLog(level, text)).append('\n') }
        }
        try {
            regist = Regist(info, lg) { ev ->
                runOnUiThread {
                    // Registration có thể trả kết quả sau khi người dùng đã rời màn hình.
                    // onDestroy() chịu trách nhiệm dừng/dispose Regist; callback muộn không được điều hướng UI.
                    if (!isFinishing && !isDestroyed) onRegistEvent(ev)
                }
            }
        } catch (e: CreateError) {
            running = false
            status = "Không bắt đầu được: ${e.errorCode}"
            render()
        } catch (e: Throwable) {
            running = false
            status = "Không bắt đầu được: ${e.message}"
            render()
        }
    }

    private fun onRegistEvent(ev: RegistEvent) {
        val r = regist
        regist = null
        if (r != null) Thread { try { r.dispose() } catch (_: Throwable) {} }.start()
        running = false
        when (ev) {
            is RegistEventSuccess -> {
                try {
                    PsStore.addPlayer(this, host, ps5, ev.host)
                    toast("Ghép nối thành công.")
                    setResult(RESULT_OK)
                    startActivity(Intent(this, PsConnectActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    status = e.message ?: "Không thể lưu thông tin ghép nối an toàn."
                    render()
                }
            }
            is RegistEventCanceled -> { status = "Đã hủy."; render() }
            is RegistEventFailed -> {
                status = "Máy từ chối ghép nối. Thường do: mã 8 số đã hết hạn, hoặc mã người chơi ở bước 2 không phải của người đang chọn trên máy. " +
                    "Hãy chuyển đúng người chơi phụ, lấy mã mới rồi nhập lại."
                render()
            }
        }
    }

    private fun showLog() {
        val text = synchronized(log) { log.toString() }
        val tv = Ui.tv(this, text, 11f, Ui.TEXT).apply { setTextIsSelectable(true) }
        val sv = android.widget.ScrollView(this)
        sv.addView(tv)
        sv.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Chi tiết")
            .setView(sv)
            .setPositiveButton("Sao chép") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("log", text))
                toast("Đã sao chép.")
            }
            .setNegativeButton("Đóng", null).show()
    }

    // ---------------- Tiện ích ----------------

    private fun clipboardText(): String {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: ""
    }

    private fun web(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast("Không mở được trình duyệt.") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}


