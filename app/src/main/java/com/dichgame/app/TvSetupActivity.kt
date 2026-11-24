package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Phụ đề trên TV: chọn hãng TV rồi làm theo các bước.
 *  - Google TV / Android TV: cài app Dịch game TV, ghép bằng mã 8 số (đã chạy được).
 *  - TV LG: ghép qua LG Connect Apps và hiện phụ đề bằng thông báo của TV, đè lên cả hình HDMI (THỬ NGHIỆM, chưa thử trên TV thật).
 *  - TV Samsung: chưa có cách hiện chữ đè lên hình HDMI; chiếu màn hình hoặc mở trang web phụ đề.
 * Hướng dẫn Developer Mode của LG/Samsung nằm trong "Hướng dẫn chi tiết".
 */
class TvSetupActivity : Activity() {
    private lateinit var p: Prefs
    private var brand = 0
    private lateinit var status: TextView
    private lateinit var ipField: EditText
    private lateinit var codeField: EditText
    private var samsungIp = ""
    private var samsungInstalling = false
    private var samsungStatus: TextView? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        brand = when (intent?.getStringExtra("brand")) { "lg" -> 1; "samsung" -> 2; else -> if (p.lgPaired && !p.tvPaired) 1 else 0 }
        render()
    }

    private fun render() {
        val a = this
        if (p.webSub) TvOut.startWeb(this)
        screen("Phụ đề trên TV") {
            put(Ui.tv(a, "Bản dịch hiện ngay trên màn hình TV, phía trên hình game. Giọng đọc cũng phát ra loa TV được (Android TV).", 14f, Ui.SUB), 0, 4)

            group("TV CỦA BẠN HÃNG GÌ?", 14) {
                val row = Ui.hbox(a)
                row.addView(brandTile("Google TV", "Truyền APK qua Wi-Fi", 0), Ui.lp(0, MATCH_PARENT, 1f))
                row.addView(brandTile("TV LG", "Cài IPK từ Android", 1), tileLp())
                row.addView(brandTile("TV Samsung", "App Tizen thử nghiệm", 2), tileLp())
                addView(row)
                addView(Ui.tv(a, brandInfo(), 13.5f, if (brand == 0) Ui.SUB else Ui.GOLD2).apply {
                    setPadding(Ui.dp(a, 2), Ui.dp(a, 12), 0, Ui.dp(a, 2))
                })
                addView(Ui.tv(a, "📶  TV và điện thoại phải dùng chung một mạng Wi-Fi", 13.5f, Ui.SUB).apply {
                    setPadding(Ui.dp(a, 2), Ui.dp(a, 6), 0, 0)
                })
            }

            when (brand) {
                0 -> {
                    group("CÀI APK TỪ ĐIỆN THOẠI") { shareGoogle(this) }
                    group("CÁCH DÙNG") { stepsGoogle(this) }
                    group("KẾT NỐI TV") { pairGoogle(this) }
                }
                1 -> {
                    group("CÁCH DÙNG") { stepsLg(this) }
                    group("CÀI APP LG TỪ ĐIỆN THOẠI (THỬ NGHIỆM)") { developerLg(this) }
                    group("KẾT NỐI TV LG (THỬ NGHIỆM)") { connectLg(this) }
                    group("XEM PHỤ ĐỀ BẰNG TRÌNH DUYỆT (THỬ NGHIỆM)") { webCard(this) }
                }
                else -> {
                    group("CÀI WGT TỪ ĐIỆN THOẠI (THỬ NGHIỆM)") { developerSamsung(this) }
                    group("CÁCH DÙNG") { stepsSamsung(this) }
                    group("XEM PHỤ ĐỀ BẰNG TRÌNH DUYỆT (THỬ NGHIỆM)") { webCard(this) }
                    group("CHIẾU MÀN HÌNH ĐIỆN THOẠI LÊN TV") { mirrorBlock(this) }
                }
            }
            group(null, 0) {
                addView(Ui.navRow(a, null, "Hướng dẫn chi tiết", "Bật Developer Mode trên TV, cài app từ Android hoặc iPhone") { showGuide() })
            }
        }
    }

    private fun tileLp() = Ui.lp(0, MATCH_PARENT, 1f).apply { leftMargin = Ui.dp(this@TvSetupActivity, 8) }

    private fun brandInfo(): String = when (brand) {
        0 -> "TV Sony, TCL, Xiaomi... chạy Android TV hoặc Google TV, Android 8 trở lên. Cài app ngay trên TV bằng remote, không cần máy tính."
        1 -> "TV LG webOS: Dịch game ghép với TV qua LG Connect Apps rồi gửi phụ đề thành thông báo chữ của TV, nằm đè lên cả hình HDMI của máy chơi game. (thử nghiệm, chưa thử trên TV LG thật; trình cài webOS từ Android đang thử nghiệm)"
        else -> "TV Samsung (Tizen): app thử nghiệm dùng cửa sổ HDMI bên trong ứng dụng; cần WGT ký chứng chỉ phù hợp. Chưa kiểm thử trên TV thật."
    }

    private fun brandTile(name: String, how: String, idx: Int): View {
        val on = brand == idx
        val v = Ui.vbox(this)
        v.gravity = Gravity.CENTER
        v.background = if (on) Ui.goldBg(this, 20) else Ui.bg(this, Ui.CARD2, 20, Ui.LINE, 1)
        val pad = Ui.dp(this, 12)
        v.setPadding(pad / 2, pad, pad / 2, pad)
        val fg = if (on) Color.parseColor("#2A1A08") else Ui.TEXT
        v.addView(Ui.tv(this, if (idx == 0) "📺" else if (idx == 1) "📡" else "🖥", 24f).apply { gravity = Gravity.CENTER })
        v.addView(Ui.tv(this, name, 14f, fg, true).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(this@TvSetupActivity, 4), 0, 0) })
        v.addView(Ui.tv(this, how, 11.5f, if (on) fg else Ui.SUB).apply { gravity = Gravity.CENTER })
        v.isClickable = true
        Ui.ripple(v, 20)
        v.setOnClickListener { brand = idx; render() }
        return v
    }

    private fun step(n: Int, text: String): View {
        val row = Ui.hbox(this)
        row.gravity = Gravity.TOP
        val num = Ui.tv(this, n.toString(), 15f, Color.parseColor("#2A1A08"), true)
        num.gravity = Gravity.CENTER
        num.background = Ui.goldBg(this, 20)
        row.addView(num, Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32)))
        // Bọc chữ trong một cột như màn Hướng dẫn: đặt TextView trực tiếp làm con có trọng số thì dòng cuối bị cắt.
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, text, 14.5f, Ui.TEXT).apply { setLineSpacing(0f, 1.1f) })
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@TvSetupActivity, 12) })
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6))
        return row
    }

    private fun copy(label: String, text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Đã chép.", Toast.LENGTH_SHORT).show()
    }

    // ---------------- Google TV / Android TV ----------------

    private fun shareGoogle(c: LinearLayout) {
        c.addView(Ui.tv(this, "Không cần máy tính hoặc tải từ GitHub trên TV. Mở Downloader hoặc trình duyệt hỗ trợ tải APK trên TV, nhập đường dẫn do điện thoại tạo. TV vẫn yêu cầu bạn xác nhận cài đặt.", 14f, Ui.TEXT))
        val links = Ui.vbox(this)
        var busy = false
        c.addView(Ui.button(this, "Tạo link cài TV qua Wi-Fi", true) {
            if (!busy) {
                busy = true
                links.removeAllViews()
                links.addView(Ui.tv(this, "Đang chuẩn bị APK...", 13f, Ui.SUB))
                Thread {
                    val result = runCatching { TvApkShare.start(applicationContext) }
                    runOnUiThread {
                        busy = false
                        if (isDestroyed || isFinishing) { TvApkShare.stop(); return@runOnUiThread }
                        links.removeAllViews()
                        result.fold({ addresses ->
                            for (address in addresses) {
                                links.addView(Ui.tv(this, address, 14f, Ui.GOLD).apply { setTextIsSelectable(true) })
                                links.addView(Ui.button(this, "Chép link") { copy("apk", address) })
                            }
                            links.addView(Ui.tv(this, "Link hết hạn sau 10 phút. Sau khi cài, mở DichGame TV và cấp quyền hiển thị trên ứng dụng khác.", 13f, Ui.SUB))
                        }, { error -> links.addView(Ui.tv(this, "Không mở được bộ cài: " + (error.message ?: "Lỗi Wi-Fi"), 13f, Ui.DANGER)) })
                    }
                }.start()
            }
        }, Ui.lpm(this, 8, 8))
        c.addView(links)
        c.addView(Ui.button(this, "Dừng chia sẻ APK") {
            TvApkShare.stop(); links.removeAllViews()
        }, Ui.lpm(this, 8, 8))
    }

    private fun stepsGoogle(c: LinearLayout) {
        c.addView(step(1, "Trên TV, mở app Downloader (hoặc cài bằng USB), tải file DichGame-TV.apk trong mục Releases của dự án này trên GitHub để cài app Dịch game TV."))
        val urlRow = Ui.hbox(this)
        val url = Ui.field(this, "Dán link tải APK TV", p.sp.getString("tvapkurl", "https://github.com/temu838655-dev/DichGame/releases/latest/download/DichGame-TV.apk").orEmpty())
        urlRow.addView(url, Ui.lp(0, WRAP_CONTENT, 1f))
        urlRow.addView(Ui.button(this, "Chép") {
            val s = url.text.toString().trim()
            p.sp.edit().putString("tvapkurl", s).apply()
            if (s.isEmpty()) Toast.makeText(this, "Chưa có đường dẫn.", Toast.LENGTH_SHORT).show() else copy("apk", s)
        }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@TvSetupActivity, 8) })
        c.addView(urlRow, Ui.lpm(this, 0, 6))
        c.addView(step(2, "Mở app trên TV, cấp quyền hiển thị trên ứng dụng khác. TV hiện mã 8 số."))
        c.addView(step(3, "Trên điện thoại, bấm Kết nối TV rồi nhập mã 8 số. Chuyển TV sang cổng HDMI của máy chơi game: phụ đề hiện ngay trên hình game."))
        c.addView(Ui.tv(this, "Máy chơi game vẫn cắm thẳng vào TV nên hình không trễ. Không hỗ trợ Android Box.", 12.5f, Ui.SUB), Ui.lpm(this, 8, 0))
    }

    private fun pairGoogle(c: LinearLayout) {
        status = Ui.tv(this, if (p.tvPaired) "Đã ghép TV ${p.tvIp}." else "Chưa ghép TV.", 14f, if (p.tvPaired) Ui.OK else Ui.SUB)
        c.addView(status, Ui.lpm(this, 0, 10))
        ipField = Ui.field(this, "Địa chỉ IP của TV (vd 192.168.1.40)", p.tvIp)
        codeField = Ui.field(this, "Mã 8 số đang hiện trên TV", "")
        codeField.inputType = InputType.TYPE_CLASS_NUMBER
        c.addView(Ui.button(this, "Tìm TV trong mạng") { findTv() }, Ui.lpm(this, 0, 8))
        c.addView(ipField, Ui.lpm(this, 0, 8))
        c.addView(codeField, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Kết nối TV", true) { pairTv() }, Ui.lpm(this, 4, 8))
        c.addView(Ui.button(this, "Gửi câu thử lên TV") { testTv() }, Ui.lpm(this, 0, 8))
        if (p.tvPaired) c.addView(Ui.button(this, "Bỏ ghép TV") { p.clearTv(); render() })
    }

    // ---------------- TV LG ----------------

    private fun stepsLg(c: LinearLayout) {
        c.addView(step(1, "Trên TV LG, bật cho phép điều khiển từ điện thoại: mục \"LG Connect Apps\" (tên và vị trí khác nhau tuỳ đời TV, thường trong Cài đặt, Kết nối hoặc Chung)."))
        c.addView(step(2, "Trên điện thoại bấm Tìm TV LG (hoặc nhập IP của TV), rồi bấm Kết nối TV LG."))
        c.addView(step(3, "TV hiện hộp hỏi cho phép kết nối: chọn Có. Bấm Gửi câu thử, thấy thông báo hiện trên TV là xong. Khi dịch, phụ đề sẽ hiện trên TV như thông báo chữ."))
        c.addView(Ui.tv(this, "Không cần bật Developer Mode để dùng cách này. Thông báo của TV là chữ ngắn, hiện vài giây rồi mất, không chỉnh được cỡ chữ hay màu.", 12.5f, Ui.SUB), Ui.lpm(this, 8, 0))
    }

    private fun connectLg(c: LinearLayout) {
        status = Ui.tv(this, if (p.lgPaired) "Đã ghép TV LG ${p.lgIp}." else "Chưa ghép TV LG.", 14f, if (p.lgPaired) Ui.OK else Ui.SUB)
        c.addView(status, Ui.lpm(this, 0, 10))
        ipField = Ui.field(this, "Địa chỉ IP của TV LG (vd 192.168.1.50)", p.lgIp)
        c.addView(Ui.button(this, "Tìm TV LG trong mạng") { findLg() }, Ui.lpm(this, 0, 8))
        c.addView(ipField, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Kết nối TV", true) { connectLgNow() }, Ui.lpm(this, 4, 8))
        c.addView(Ui.button(this, "Gửi câu thử lên TV") { testLg() }, Ui.lpm(this, 0, 8))
        if (p.lgPaired) {
            c.addView(Ui.button(this, "Mở app trên TV LG (thử nghiệm)") { openLgReceiver() }, Ui.lpm(this, 0, 8))
            c.divide()
            c.addView(Ui.switchRow(this, "Gửi phụ đề lên TV LG khi đang dịch", "Tắt nếu chỉ muốn thử ghép.", p.lgSubs) { p.lgSubs = it }, Ui.lpm(this, 12, 12))
            c.addView(Ui.button(this, "Bỏ ghép TV LG") { p.clearLg(); TvOut.resetLg(); render() })
        }
    }

    private var installingLg = false

    private fun developerLg(c: LinearLayout) {
        c.addView(Ui.tv(this, "Bật Dev Mode Status và Key Server trong ứng dụng Developer Mode trên TV. Nhập IP và passphrase 6 ký tự. Không cần máy tính; khóa TV được ghi nhớ sau khi bạn xác nhận.", 14f, Ui.TEXT))
        val ip = Ui.field(this, "IP TV LG", p.sp.getString("lg_dev_ip", p.lgIp).orEmpty())
        val pass = Ui.field(this, "Passphrase 6 ký tự", "")
        pass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        pass.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        val result = Ui.tv(this, "Chưa cài đặt.", 14f, Ui.SUB)
        c.addView(ip, Ui.lpm(this, 8, 8))
        c.addView(pass, Ui.lpm(this, 0, 8))
        c.addView(result, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Cài đặt & mở ứng dụng", true) {
            val address = ip.text.toString().trim()
            val secret = pass.text.toString().toByteArray(Charsets.UTF_8)
            if (!LgDeveloperKey.isPrivateIpv4(address) || secret.size != 6 || installingLg) {
                secret.fill(0)
                result.text = "Kiểm tra IP nội bộ, passphrase 6 ký tự hoặc chờ lượt cài hiện tại."
            } else {
                installingLg = true
                pass.text.clear()
                result.text = "Đang kết nối Developer Mode..."
                Thread {
                    var session: com.jcraft.jsch.Session? = null
                    var installed = false
                    try {
                        val connected = LgDeveloperSsh.connect(address, secret) { fingerprint ->
                            approveLgFingerprint(address, fingerprint)
                        }
                        session = connected
                        runOnUiThread { result.text = "Đang truyền và cài IPK..." }
                        LgDeveloperInstaller.install(applicationContext, connected)
                        installed = true
                        p.sp.edit().putString("lg_dev_ip", address).apply()
                        runOnUiThread { result.text = "Đã cài. Đang mở ứng dụng..." }
                        val ips = PsFinder.localIps(applicationContext).filter { LgDeveloperKey.isPrivateIpv4(it) }
                        val phoneIp = ips.firstOrNull { it.substringBeforeLast('.') == address.substringBeforeLast('.') } ?: ips.singleOrNull()
                        p.webSub = true
                        val started = TvOut.startWeb(applicationContext) != null
                        LgDeveloperInstaller.launch(connected, if (started) phoneIp else null)
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed && brand == 1) {
                                render()
                                Toast.makeText(this, if (started) "TV đã mở app. Nhập mã 8 số trong mục máy chủ phụ đề." else "Đã mở app; hãy bật máy chủ phụ đề.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            result.setTextColor(Ui.DANGER)
                            result.text = (if (installed) "Đã cài nhưng chưa mở được: " else "Chưa cài được: ") + (e.message ?: "Lỗi kết nối")
                        }
                    } finally {
                        secret.fill(0)
                        session?.disconnect()
                        runOnUiThread { installingLg = false }
                    }
                }.start()
            }
        }, Ui.lpm(this, 0, 8))
    }

    private fun approveLgFingerprint(ip: String, fingerprint: String): Boolean {
        val known = p.sp.getString("lg_host_key_$ip", null)
        if (known == fingerprint) return true
        val latch = java.util.concurrent.CountDownLatch(1)
        val approved = java.util.concurrent.atomic.AtomicBoolean(false)
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                latch.countDown()
            } else {
                AlertDialog.Builder(this, R.style.DgDialog)
                    .setTitle(if (known == null) "Xác nhận khóa TV LG" else "Khóa TV LG đã thay đổi")
                    .setMessage("IP: $ip\n$fingerprint\n" + if (known == null) "Chỉ chấp nhận nếu đây là TV của bạn." else "Khóa cũ không khớp. Chỉ tiếp tục nếu bạn đã xác minh TV hoặc vừa đặt lại Developer Mode.")
                    .setPositiveButton("Tin cậy") { _, _ -> p.sp.edit().putString("lg_host_key_$ip", fingerprint).apply(); approved.set(true); latch.countDown() }
                    .setNegativeButton("Hủy") { _, _ -> latch.countDown() }
                    .setOnCancelListener { latch.countDown() }
                    .show()
            }
        }
        return latch.await(90, java.util.concurrent.TimeUnit.SECONDS) && approved.get()
    }

    private fun openLgReceiver() {
        val ip = p.lgIp
        val key = p.lgKey
        val label = status
        label.text = "Đang mở ứng dụng trên TV LG..."
        Thread {
            val tv = LgTv(ip, key)
            val err = try { tv.connect(8000) ?: tv.launchReceiver() } finally { tv.close() }
            runOnUiThread {
                if (isFinishing || isDestroyed || brand != 1 || status !== label) return@runOnUiThread
                label.setTextColor(if (err == null) Ui.OK else Ui.DANGER)
                label.text = if (err == null) "TV đã xác nhận mở app phụ đề." else "Chưa mở được app: $err. Kiểm tra app webOS đã cài."
            }
        }.start()
    }

    private fun findLg() {
        status.setTextColor(Ui.SUB)
        status.text = "Đang tìm TV LG..."
        Thread {
            val legs = try { PsFinder.openLegs(this) } catch (_: Throwable) { emptyList() }
            val found = try { LgDiscover.search(legs, 3500) } catch (_: Throwable) { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (found.isNotEmpty()) {
                    ipField.setText(found[0])
                    status.setTextColor(Ui.SUB)
                    status.text = "Thấy TV LG ở ${found[0]}" + (if (found.size > 1) " (và ${found.size - 1} TV khác)" else "") + ". Bấm Kết nối TV."
                } else {
                    status.setTextColor(Ui.DANGER)
                    status.text = "Không thấy TV LG. Bật TV, bật LG Connect Apps, cùng Wi-Fi/LAN; hoặc nhập IP của TV (xem trong cài đặt mạng của TV)."
                }
            }
        }.start()
    }

    private fun connectLgNow() {
        val ip = ipField.text.toString().trim()
        if (ip.isEmpty()) { status.setTextColor(Ui.DANGER); status.text = "Hãy nhập IP của TV hoặc bấm Tìm TV LG."; return }
        status.setTextColor(Ui.SUB)
        status.text = "Đang kết nối TV LG..."
        Thread {
            val tv = LgTv(ip, if (ip == p.lgIp) p.lgKey else "")
            val err = tv.connect(60_000) {
                runOnUiThread { status.text = "Nhìn lên TV và chọn Có (Cho phép) trong hộp hỏi kết nối..." }
            }
            if (err == null) { p.saveLg(ip, tv.key); TvOut.resetLg() }
            val toastErr = if (err == null) tv.toast("Dịch game đã kết nối ✓") else null
            tv.close()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (err == null) {
                    render() // dựng lại trang (công tắc gửi phụ đề, nút Bỏ ghép) rồi mới ghi kết quả vào dòng trạng thái mới
                    status.setTextColor(Ui.OK)
                    status.text = "Đã ghép TV LG $ip." + (if (toastErr != null) " Nhưng gửi thông báo thử bị lỗi: $toastErr" else " TV vừa hiện thông báo thử.")
                } else {
                    status.setTextColor(Ui.DANGER)
                    status.text = "Không ghép được: $err"
                }
            }
        }.start()
    }

    private fun testLg() {
        if (!p.lgPaired) { status.setTextColor(Ui.DANGER); status.text = "Hãy ghép TV LG trước."; return }
        status.setTextColor(Ui.SUB)
        status.text = "Đang gửi..."
        Thread {
            val tv = LgTv(p.lgIp, p.lgKey)
            var err = tv.connect(10_000)
            if (err == null) err = tv.toast("Đây là câu thử phụ đề trên TV LG.")
            tv.close()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.setTextColor(if (err == null) Ui.OK else Ui.DANGER)
                status.text = if (err == null) "Đã gửi câu thử. Xem trên TV." else "Gửi không được: $err"
            }
        }.start()
    }

    // ---------------- TV Samsung ----------------

    private fun developerSamsung(c: LinearLayout) {
        val ips = PsFinder.localIps(this).filter { LgDeveloperKey.isPrivateIpv4(it) }
        c.addView(Ui.tv(this, "Trên Samsung: Apps → nhập 12345 → Developer Mode ON → Host PC IP là IP điện thoại → khởi động lại TV. IP điện thoại: " + ips.joinToString(), 14f, Ui.TEXT))
        val ip = Ui.field(this, "IP TV Samsung", p.sp.getString("samsung_dev_ip", "").orEmpty())
        c.addView(ip, Ui.lpm(this, 8, 8))
        val label = Ui.tv(this, if (samsungInstalling) "Đang cài..." else "Chọn DichGame WGT đã ký cho TV của bạn. Chưa hỗ trợ tự tạo chứng chỉ.", 13f, Ui.SUB)
        samsungStatus = label
        c.addView(label)
        c.addView(Ui.button(this, "Xuất dự án Tizen để ký WGT") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "DichGame-Samsung-source.zip")
            }, 7320)
        }, Ui.lpm(this, 8, 8))
        c.addView(Ui.tv(this, "ZIP là mã nguồn chưa ký. Dùng Samsung Certificate Manager / Tizen Studio với DUID TV để ký, rồi nhập WGT. Không gửi khóa riêng lên GitHub.", 12.5f, Ui.SUB))
        c.addView(Ui.button(this, "Cài lại WGT đã lưu cho IP này") {
            val address = ip.text.toString().trim()
            if (!LgDeveloperKey.isPrivateIpv4(address) || samsungInstalling) return@button
            val saved = p.sp.getString("samsung_wgt_uri_$address", "").orEmpty()
            if (saved.isBlank()) { label.text = "Chưa lưu WGT cho IP này. Hãy chọn WGT đã ký."; return@button }
            confirmSamsungInstall(address, android.net.Uri.parse(saved))
        }, Ui.lpm(this, 8, 8))
        c.addView(Ui.button(this, "Quên WGT đã lưu cho IP này") {
            val address = ip.text.toString().trim()
            if (!LgDeveloperKey.isPrivateIpv4(address) || samsungInstalling) return@button
            val saved = p.sp.getString("samsung_wgt_uri_$address", "").orEmpty()
            p.sp.edit().remove("samsung_wgt_uri_$address").apply()
            if (saved.isNotBlank() && !p.sp.all.any { (key, value) -> key.startsWith("samsung_wgt_uri_") && value == saved }) {
                runCatching { contentResolver.releasePersistableUriPermission(android.net.Uri.parse(saved), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            label.text = "Đã quên đường dẫn WGT."
        }, Ui.lpm(this, 8, 8))
        c.addView(Ui.button(this, "Chọn WGT đã ký & cài", true) {
            if (samsungInstalling) return@button
            val address = ip.text.toString().trim()
            if (!LgDeveloperKey.isPrivateIpv4(address)) {
                label.text = "Nhập IP nội bộ của TV."; return@button
            }
            samsungIp = address
            p.sp.edit().putString("samsung_pending_ip", address).apply()
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, 7319)
        }, Ui.lpm(this, 8, 8))
    }

    @Deprecated("Activity result callback for document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7320 && resultCode == RESULT_OK) {
            val target = data?.data ?: return
            Thread {
                val result = runCatching {
                    assets.open("tv/DichGame-Samsung-source.zip").use { input ->
                        contentResolver.openOutputStream(target, "w")?.use { input.copyTo(it) }
                            ?: error("Không mở được nơi lưu ZIP")
                    }
                }
                runOnUiThread { Toast.makeText(this, if (result.isSuccess) "Đã xuất ZIP chưa ký." else "Xuất ZIP thất bại: " + result.exceptionOrNull()?.message, Toast.LENGTH_LONG).show() }
            }.start()
            return
        }
        if (requestCode != 7319 || resultCode != RESULT_OK || samsungInstalling) return
        val uri = data?.data ?: return
        val address = samsungIp.ifBlank { p.sp.getString("samsung_pending_ip", "").orEmpty() }
        p.sp.edit().remove("samsung_pending_ip").apply()
        if (!LgDeveloperKey.isPrivateIpv4(address)) return
        confirmSamsungInstall(address, uri, (data?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun confirmSamsungInstall(address: String, uri: android.net.Uri, canPersist: Boolean = false) {
        AlertDialog.Builder(this, R.style.DgDialog).setTitle("Cài DichGame TV")
            .setMessage("Cài WGT đã chọn lên TV Samsung tại $address?")
            .setNegativeButton("Hủy", null).setPositiveButton("Cài") { _, _ ->
                samsungInstalling = true
                Thread {
                    val result = runCatching {
                        SamsungWgtInstaller.install(applicationContext, address, uri) { message ->
                            runOnUiThread { samsungStatus?.text = message }
                        }
                    }
                    runOnUiThread {
                        samsungInstalling = false
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        result.fold({ message ->
                            p.sp.edit().putString("samsung_dev_ip", address).apply()
                            if (canPersist && runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess) {
                                p.sp.edit().putString("samsung_wgt_uri_$address", uri.toString()).apply()
                            }
                            p.webSub = true; TvOut.startWeb(this)
                            if (brand == 2) render()
                            Toast.makeText(this, message + " Ghép mã 8 số trên TV.", Toast.LENGTH_LONG).show()
                        }, { error -> samsungStatus?.text = "Chưa cài được: " + (error.message ?: "Lỗi SDB") })
                    }
                }.start()
            }.show()
    }

    private fun stepsSamsung(c: LinearLayout) {
        c.addView(step(1, "Ghép nối PS4/PS5 trong app một lần: Trang chủ, Ghép nối máy chơi game."))
        c.addView(step(2, "Vào chơi trong app, bấm 🌐 để bật dịch phụ đề rồi kéo khung tới dòng phụ đề của game."))
        c.addView(step(3, "Chiếu điện thoại lên TV Samsung bằng Smart View (xem khung chiếu bên dưới), hoặc mở trang web phụ đề trên trình duyệt của TV để xem chữ dịch ở màn hình thứ hai."))
        c.addView(Ui.tv(this, "App Tizen thử nghiệm có nhận phụ đề qua mã ghép nối; cần đóng gói và ký WGT phù hợp trước khi cài. Xem Hướng dẫn chi tiết.", 12.5f, Ui.SUB), Ui.lpm(this, 8, 0))
    }

    /** Hướng dẫn chiếu màn hình điện thoại lên TV: phụ đề nằm sẵn trên hình điện thoại nên TV nào cũng thấy. */
    private fun mirrorBlock(c: LinearLayout) {
        c.addView(Ui.tv(this, "Samsung dùng Smart View, LG dùng Screen Share, TV khác dùng Chromecast hoặc AirPlay. Có cáp USB-C ra HDMI thì cắm thẳng vào TV (điện thoại phải hỗ trợ xuất hình). TV hiện đúng hình game kèm phụ đề tiếng Việt vì phụ đề nằm sẵn trên màn hình điện thoại.", 14f, Ui.TEXT), Ui.lpm(this, 0, 8))
        c.addView(Ui.tv(this, "Chiếu không dây trễ khoảng 0,1 đến 0,3 giây: hợp game cốt truyện hơn game đối kháng. Cáp gần như không trễ. Chơi PS4/PS5 trong app còn là bản thử nghiệm.", 12.5f, Ui.SUB), Ui.lpm(this, 0, 10))
        c.addView(Ui.button(this, "Mở ghép nối PS4/PS5", true) { startActivity(Intent(this, PsConnectActivity::class.java)) })
    }

    // ---------------- Trang web phụ đề ----------------

    private fun webCard(c: LinearLayout) {
        val ips = try { PsFinder.localIps(this) } catch (_: Throwable) { emptyList() }
        val urls = ips.map { "http://$it:${TvOut.WEB_PORT}" }
        c.addView(Ui.switchRow(this, "Bật máy chủ phụ đề TV",
            "App TV ghép bằng mã 8 số. Bật riêng chế độ trình duyệt nếu cần. Chỉ dùng trên mạng Wi-Fi tin cậy.", p.webSub) { on ->
            p.webSub = on
            if (on && TvOut.startWeb(this) == null) {
                Toast.makeText(this, "Không mở được cổng ${TvOut.WEB_PORT}. Thử tắt rồi bật lại.", Toast.LENGTH_LONG).show()
                p.webSub = false
            }
            if (!on) TvOut.stopWeb()
            render()
        })
        if (p.webSub) {
            c.divide()
            val pairingCode = Ui.field(this, "Mã 8 số trên app TV", "")
            pairingCode.inputType = InputType.TYPE_CLASS_NUMBER
            pairingCode.filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            c.addView(pairingCode, Ui.lpm(this, 8, 8))
            c.addView(Ui.button(this, "Ghép app LG / Samsung", true) {
                val ok = TvOut.web?.approveReceiver(pairingCode.text.toString().trim()) == true
                Toast.makeText(this, if (ok) "Đã ghép TV" else "Mã sai, hết hạn hoặc đã đạt giới hạn TV", Toast.LENGTH_LONG).show()
                if (ok) pairingCode.text.clear()
            })
            c.addView(Ui.button(this, "Gửi câu thử đến app TV") {
                val server = TvOut.web
                server?.push("DichGame: phụ đề thử nghiệm")
                android.os.Handler(mainLooper).postDelayed({ server?.clearTestSubtitle("DichGame: phụ đề thử nghiệm") }, 5000)
            }, Ui.lpm(this, 8, 8))
            c.addView(Ui.button(this, "Bỏ ghép tất cả app TV") { TvOut.web?.revokeReceivers(); Toast.makeText(this, "Đã bỏ ghép", Toast.LENGTH_SHORT).show() }, Ui.lpm(this, 8, 8))
            c.addView(Ui.switchRow(this, "Cho phép trình duyệt không cần mã", "Chỉ bật khi cần; mọi thiết bị cùng mạng có thể đọc phụ đề.", p.sp.getBoolean("web_legacy", false)) { enabled ->
                p.sp.edit().putBoolean("web_legacy", enabled).apply()
                TvOut.web?.legacyEnabled = enabled
            }, Ui.lpm(this, 8, 8))
            val appearance = WebTvStyle.read(p.sp)
            fun refreshStyle() { TvOut.web?.setStyle(WebTvStyle.read(p.sp)) }
            c.addView(Ui.tv(this, "TÙY CHỈNH PHỤ ĐỀ LG / SAMSUNG", 13f, Ui.GOLD, true), Ui.lpm(this, 12, 8))
            c.addView(Ui.seekRow(this, "Cỡ chữ", { "$it px" }, 24, 80, appearance.size) {
                p.sp.edit().putInt("web_tv_size", it).apply(); refreshStyle()
            })
            c.addView(Ui.seekRow(this, "Độ đậm nền", { "$it%" }, 0, 100, appearance.background) {
                p.sp.edit().putInt("web_tv_background", it).apply(); refreshStyle()
            })
            val colors = listOf("#ffffff", "#ffff00", "#00ffff")
            c.addView(Ui.button(this, "Màu chữ: " + listOf("Trắng", "Vàng", "Xanh")[colors.indexOf(appearance.color).coerceAtLeast(0)]) {
                AlertDialog.Builder(this, R.style.DgDialog).setTitle("Màu phụ đề")
                    .setItems(arrayOf("Trắng", "Vàng", "Xanh")) { _, i ->
                        p.sp.edit().putString("web_tv_color", colors[i]).apply(); refreshStyle(); render()
                    }.show()
            }, Ui.lpm(this, 8, 8))
            c.addView(Ui.switchRow(this, "Phụ đề ở phía trên", "Tắt để đặt phía dưới.", appearance.top) {
                p.sp.edit().putBoolean("web_tv_top", it).apply(); refreshStyle()
            })
            c.addView(Ui.switchRow(this, "Đọc bằng giọng TV (thử nghiệm)", "Chỉ hoạt động nếu trình duyệt TV có Speech Synthesis tiếng Việt.", appearance.speak) {
                p.sp.edit().putBoolean("web_tv_speak", it).apply(); refreshStyle()
            })
            if (urls.isEmpty()) {
                c.addView(Ui.tv(this, "Chưa thấy địa chỉ Wi-Fi của điện thoại. Hãy kết nối Wi-Fi rồi mở lại trang này.", 13f, Ui.DANGER), Ui.lpm(this, 10, 0))
            } else for (u in urls) {
                val row = Ui.hbox(this)
                row.addView(Ui.tv(this, u, 16f, Ui.GOLD, true), Ui.lp(0, WRAP_CONTENT, 1f))
                row.addView(Ui.button(this, "Chép") { copy("url", u) })
                c.addView(row, Ui.lpm(this, 10, 0))
            }
            c.addView(Ui.tv(this, "Phụ đề chỉ hiện khi bạn bấm bắt đầu dịch từ màn hình chính. Trang tự nối lại nếu mất mạng.", 12.5f, Ui.SUB), Ui.lpm(this, 8, 0))
        }
    }

    // ---------------- Hướng dẫn chi tiết ----------------

    private fun showGuide() {
        val txt = when (brand) {
            0 -> "GOOGLE TV / ANDROID TV\n\n" +
                "1. Trên TV vào cửa hàng, cài app Downloader (hoặc dùng USB).\n" +
                "2. Trong Downloader nhập đường dẫn tải DichGame-TV.apk (mục Releases của dự án trên GitHub), cài đặt.\n" +
                "3. Lần đầu mở app TV, cấp quyền \"Hiển thị trên ứng dụng khác\". TV hiện mã 8 số đổi mỗi 60 giây.\n" +
                "4. Trên điện thoại bấm Tìm TV trong mạng, nhập mã, bấm Kết nối TV.\n\n" +
                "Không dùng được trên Android Box không có chế độ hiển thị đè lên ứng dụng khác."
            1 -> "TV LG (webOS)\n\n" +
                "A. Phụ đề bằng thông báo (không cần Developer Mode)\n" +
                "Bật LG Connect Apps trên TV, ghép ở trang này. Dịch game gửi lệnh tạo thông báo chữ qua cổng điều khiển của TV (3001, hoặc 3000 với TV đời cũ). Chưa thử trên TV LG thật; nếu TV báo lỗi, trang này hiện nguyên văn lỗi của TV.\n\n" +
                "B. Developer Mode (chỉ cần khi cài app webOS tự đóng gói)\n" +
                "1. Tạo tài khoản LG Developer (webostv.developer.lge.com), miễn phí.\n" +
                "2. Trên TV mở LG Content Store, cài app \"Developer Mode\".\n" +
                "3. Mở app, đăng nhập tài khoản đó, bật \"Dev Mode Status\" và \"Key Server\"; TV khởi động lại. Ghi lại địa chỉ IP và mã (passphrase) trên màn hình.\n" +
                "4. Chế độ này tự hết hạn sau một thời gian, phải mở app và gia hạn (Extend).\n" +
                "5. Cài bằng trình cài trên Android hoặc dùng ares-cli trên máy tính (ares-setup-device, ares-install).\n\n" +
                "Bản Android có trình cài IPK thử nghiệm: ở mục TV LG, nhập IP và passphrase, xác nhận khóa TV rồi bấm Cài đặt & mở ứng dụng. Chưa xác nhận trên TV thật.\n\n" +
                "iPhone: trình cài trực tiếp chưa được triển khai. Có thể xem trang web phụ đề từ điện thoại Android."
            else -> "TV SAMSUNG (Tizen)\n\n" +
                "A. Xem phụ đề\n" +
                "Chiếu màn hình điện thoại bằng Smart View, hoặc mở trang web phụ đề ở trình duyệt của TV. Ứng dụng Samsung thử nghiệm dùng cửa sổ HDMI bên trong app, chưa xác nhận trên TV thật.\n\n" +
                "B. Developer Mode (chỉ cần khi cài app Tizen tự ký)\n" +
                "1. Trên TV vào Apps, trên remote bấm lần lượt 1 2 3 4 5, cửa sổ Developer mode hiện ra.\n" +
                "2. Chọn On, nhập IP của máy bạn dùng để cài (Host PC IP), khởi động lại TV.\n" +
                "3. Cài gói .wgt bằng Tizen Studio hoặc công cụ sdb (cổng 26101).\n" +
                "4. App Tizen cần WGT ký bằng chứng chỉ phù hợp với DUID TV. Android có trình chọn và cài WGT đã ký qua SDB; chưa tự cấp chứng chỉ.\n\n" +
                "Dịch game có trình cài Android thử nghiệm cho WGT đã ký. Chưa có WGT ký chung hoặc xác nhận tương thích TV thật."
        }
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Hướng dẫn chi tiết")
            .setMessage(txt)
            .setPositiveButton("Đóng", null)
            .show()
    }

    private fun tvPost(path: String, body: String, token: String?, ip: String, onDone: (Int, String) -> Unit) {
        Thread {
            var code = -1
            var out = ""
            try {
                val c = URL("http://$ip:8765$path").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 3000; c.readTimeout = 3000; c.doOutput = true
                if (token != null) c.setRequestProperty("X-Token", token)
                c.outputStream.use { it.write(body.toByteArray()) }
                code = c.responseCode
                out = (if (code == 200) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            } catch (_: Exception) {}
            runOnUiThread { onDone(code, out.trim()) }
        }.start()
    }

    /**
     * Tìm TV tự động: gửi gói UDP hỏi tới cổng 8766, app TV trả lời kèm địa chỉ. Gửi qua đúng mạng Wi-Fi/LAN, theo ba cách
     * (255.255.255.255, broadcast của mạng, gửi thẳng từng IP) để vẫn thấy TV khi router chặn broadcast.
     */
    private fun findTv() {
        status.setTextColor(Ui.SUB)
        status.text = "Đang tìm TV..."
        Thread {
            var ip = ""
            val legs = try { PsFinder.openLegs(this) } catch (_: Throwable) { emptyList() }
            try {
                val q = "DICHGAME?".toByteArray()
                val found = java.util.concurrent.atomic.AtomicReference<String?>(null)
                val readers = legs.map { leg ->
                    Thread {
                        val buf = ByteArray(64)
                        val end = System.currentTimeMillis() + 6000
                        try { leg.socket.soTimeout = 300 } catch (_: Throwable) {}
                        while (System.currentTimeMillis() < end && found.get() == null) {
                            try {
                                val pk = java.net.DatagramPacket(buf, buf.size)
                                leg.socket.receive(pk)
                                if (String(pk.data, 0, pk.length).startsWith("DICHGAME:")) found.compareAndSet(null, pk.address.hostAddress.orEmpty())
                            } catch (_: java.net.SocketTimeoutException) {
                            } catch (_: Throwable) { break }
                        }
                    }.apply { isDaemon = true; start() }
                }
                fun send(leg: PsLeg, to: String) {
                    try { leg.socket.send(java.net.DatagramPacket(q, q.size, java.net.InetAddress.getByName(to), 8766)) } catch (_: Throwable) {}
                }
                for (round in 0 until 2) {
                    for (leg in legs) {
                        send(leg, "255.255.255.255")
                        for (bc in leg.broadcasts) send(leg, bc)
                        var n = 0
                        for (t in leg.sweep) {
                            if (found.get() != null) break
                            send(leg, t)
                            if (++n % 32 == 0) try { Thread.sleep(4) } catch (_: InterruptedException) {}
                        }
                    }
                    try { Thread.sleep(1500) } catch (_: InterruptedException) {}
                    if (found.get() != null) break
                }
                for (r in readers) try { r.join(6500) } catch (_: InterruptedException) {}
                ip = found.get().orEmpty()
            } catch (_: Exception) {
            } finally {
                for (l in legs) try { l.socket.close() } catch (_: Throwable) {}
            }
            if (ip.isEmpty() && legs.isEmpty()) {
                // Không đọc được thông tin mạng: dùng cách cũ (một gói quảng bá).
                try {
                    java.net.DatagramSocket().use { ds ->
                        ds.broadcast = true
                        ds.soTimeout = 2500
                        val q = "DICHGAME?".toByteArray()
                        ds.send(java.net.DatagramPacket(q, q.size, java.net.InetAddress.getByName("255.255.255.255"), 8766))
                        val pk = java.net.DatagramPacket(ByteArray(64), 64)
                        ds.receive(pk)
                        if (String(pk.data, 0, pk.length).startsWith("DICHGAME:")) ip = pk.address.hostAddress.orEmpty()
                    }
                } catch (_: Exception) {}
            }
            runOnUiThread {
                if (ip.isNotEmpty()) {
                    ipField.setText(ip)
                    status.setTextColor(Ui.SUB)
                    status.text = "Thấy TV ở $ip. Nhập mã 8 số đang hiện trên TV rồi bấm Ghép TV."
                } else {
                    status.setTextColor(Ui.DANGER)
                    status.text = "Không thấy TV. Mở app TV (màn hình hiện mã 8 số), kiểm tra TV và điện thoại cùng Wi-Fi/LAN, hoặc nhập IP của TV."
                }
            }
        }.start()
    }

    private fun pairTv() {
        val ip = ipField.text.toString().trim()
        if (!LgDeveloperKey.isPrivateIpv4(ip) || !codeField.text.toString().matches(Regex("[0-9]{8}"))) {
            status.setTextColor(Ui.DANGER); status.text = "Nhập IP nội bộ và mã đúng 8 số."; return
        }
        status.setTextColor(Ui.SUB)
        status.text = "Đang ghép TV..."
        tvPost("/pair", codeField.text.toString().trim(), null, ip) { code, out ->
            if (code == 200 && out.matches(Regex("[0-9a-f]{32}"))) {
                p.saveTv(ip, out)
                status.setTextColor(Ui.OK)
                status.text = "Đã ghép TV $ip. Bấm Gửi câu thử lên TV để kiểm tra."
            } else {
                status.setTextColor(Ui.DANGER)
                status.text = when (code) {
                    403 -> "Sai mã, hoặc mã đã đổi (đổi mỗi 60 giây). Nhập lại mã đang hiện trên TV."
                    429 -> "Nhập sai quá nhiều lần, chờ 1 phút rồi thử lại."
                    else -> "Không kết nối được TV. Kiểm tra cùng Wi-Fi và địa chỉ IP."
                }
            }
        }
    }

    private fun testTv() {
        val body = JSONObject().put("kind", "sub").put("text", "Đây là câu thử phụ đề trên TV.")
            .put("size", p.fontSize + 12).put("speak", false)
            .put("fg", when (p.style) { 1 -> 0xFFFFEB3B.toInt(); 2 -> Color.BLACK; else -> Color.WHITE })
            .put("bg", when (p.style) { 2 -> 0xE6FFFFFF.toInt(); 3 -> 0; else -> 0xB3000000.toInt() }).toString()
        tvPost("/sub", body, p.tvToken, p.tvIp) { code, _ ->
            status.setTextColor(if (code == 200) Ui.OK else Ui.DANGER)
            status.text = if (code == 200) "Đã gửi câu thử." else "Gửi không được (mã $code). Hãy ghép lại TV."
        }
    }
}
