package com.dichgame.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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

/** Phụ đề trên TV: chọn hãng TV, hướng dẫn cài app TV, ghép nối qua Wi-Fi bằng mã 8 số. */
class TvSetupActivity : Activity() {
    private lateinit var p: Prefs
    private var brand = 0
    private lateinit var status: TextView
    private lateinit var ipField: EditText
    private lateinit var codeField: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        render()
    }

    private fun render() {
        screen("Phụ đề trên TV") {
            put(Ui.tv(this@TvSetupActivity, "Bản dịch hiện ngay trên màn hình TV, phía trên hình game. Giọng đọc cũng phát ra loa TV được.", 14f, Ui.SUB), 0, 12)

            val warn = Ui.card(this@TvSetupActivity)
            warn.background = Ui.bg(this@TvSetupActivity, Color.parseColor("#2A1F14"), 20, Ui.GOLD2, 1)
            warn.addView(Ui.tv(this@TvSetupActivity, "⬇  Phải cài thêm app lên TV", 17f, Ui.TEXT, true))
            warn.addView(Ui.tv(this@TvSetupActivity,
                "Phụ đề chỉ hiện trên TV khi TV có app Dịch game TV (miễn phí). Hãy chọn hãng TV bên dưới rồi làm theo hướng dẫn cài.", 14f, Ui.SUB).apply {
                setPadding(0, Ui.dp(this@TvSetupActivity, 6), 0, 0)
            })
            put(warn, 0, 12)

            val brands = Ui.card(this@TvSetupActivity)
            brands.addView(Ui.section(this@TvSetupActivity, "📺", "TV của bạn hãng gì?"))
            val row = Ui.hbox(this@TvSetupActivity)
            row.addView(brandTile("Android TV", "Bằng remote TV", 0), Ui.lp(0, WRAP_CONTENT, 1f))
            row.addView(brandTile("TV LG", "Bằng điện thoại", 1), tileLp())
            row.addView(brandTile("TV Samsung", "Bằng điện thoại", 2), tileLp())
            brands.addView(row)
            brands.addView(Ui.tv(this@TvSetupActivity,
                if (brand == 0) "TV Sony, TCL, Xiaomi... chạy Android TV hoặc Google TV, Android 8 trở lên. Cài app ngay trên TV bằng remote, không cần máy tính."
                else "Bản này chưa hỗ trợ cài app cho TV LG và TV Samsung từ điện thoại. Hiện dùng được với TV chạy Android TV / Google TV.",
                13.5f, if (brand == 0) Ui.SUB else Ui.GOLD2).apply {
                setPadding(0, Ui.dp(this@TvSetupActivity, 12), 0, Ui.dp(this@TvSetupActivity, 4))
            })
            brands.addView(Ui.tv(this@TvSetupActivity, "📶  TV và điện thoại phải dùng chung một mạng Wi-Fi", 13.5f, Ui.SUB).apply {
                setPadding(0, Ui.dp(this@TvSetupActivity, 6), 0, 0)
            })
            put(brands, 0, 12)

            put(stepsCard(), 0, 12)
            put(pairCard(), 0, 12)
        }
    }

    private fun tileLp() = Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@TvSetupActivity, 8) }

    private fun brandTile(name: String, how: String, idx: Int): View {
        val on = brand == idx
        val v = Ui.vbox(this)
        v.gravity = Gravity.CENTER
        v.background = if (on) Ui.goldBg(this, 18) else Ui.bg(this, Ui.CARD2, 18, Ui.LINE, 1)
        val pad = Ui.dp(this, 12)
        v.setPadding(pad / 2, pad, pad / 2, pad)
        val fg = if (on) Color.parseColor("#2A1A08") else Ui.TEXT
        v.addView(Ui.tv(this, name, 14f, fg, true).apply { gravity = Gravity.CENTER })
        v.addView(Ui.tv(this, if (idx == 0) "🎛" else "📱", 24f).apply { gravity = Gravity.CENTER })
        v.addView(Ui.tv(this, how, 12f, if (on) fg else Ui.SUB).apply { gravity = Gravity.CENTER })
        v.setOnClickListener { brand = idx; render() }
        return v
    }

    private fun stepsCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "📋", "Cách dùng"))
        c.addView(step(1, "Trên TV, mở app Downloader (hoặc cài bằng USB), tải file DichGame-TV.apk trong mục Releases của dự án này trên GitHub để cài app Dịch game TV."))
        val urlRow = Ui.hbox(this)
        val url = Ui.field(this, "Dán đường dẫn tải APK TV để lưu lại", p.sp.getString("tvapkurl", "").orEmpty())
        urlRow.addView(url, Ui.lp(0, WRAP_CONTENT, 1f))
        urlRow.addView(Ui.button(this, "Chép") {
            val s = url.text.toString().trim()
            p.sp.edit().putString("tvapkurl", s).apply()
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("apk", s))
            Toast.makeText(this, if (s.isEmpty()) "Chưa có đường dẫn." else "Đã chép đường dẫn.", Toast.LENGTH_SHORT).show()
        }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@TvSetupActivity, 8) })
        c.addView(urlRow, Ui.lpm(this, 0, 6))
        c.addView(step(2, "Mở app trên TV, cấp quyền hiển thị trên ứng dụng khác. TV hiện mã 8 số."))
        c.addView(step(3, "Trên điện thoại, bấm Tìm TV rồi nhập mã 8 số bên dưới. Chỉ cần làm một lần."))
        c.addView(step(4, "Chuyển TV sang cổng HDMI của máy chơi game. Phụ đề hiện ngay trên hình game."))
        c.addView(Ui.tv(this, "TV và điện thoại cùng Wi-Fi. Máy chơi game vẫn cắm thẳng vào TV nên hình không trễ. Không hỗ trợ Android Box.", 12.5f, Ui.SUB), Ui.lpm(this, 8, 0))
        return c
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

    // ---------------- Ghép nối ----------------

    private fun pairCard(): View {
        val c = Ui.card(this)
        c.addView(Ui.section(this, "🔗", "Ghép TV"))
        status = Ui.tv(this, if (p.tvPaired) "Đã ghép TV ${p.tvIp}." else "Chưa ghép TV.", 14f, if (p.tvPaired) Ui.OK else Ui.SUB)
        c.addView(status, Ui.lpm(this, 0, 10))
        c.addView(Ui.button(this, "Tìm TV trong mạng") { findTv() }, Ui.lpm(this, 0, 8))
        ipField = Ui.field(this, "Địa chỉ IP của TV (vd 192.168.1.40)", p.tvIp)
        codeField = Ui.field(this, "Mã 8 số đang hiện trên TV", "")
        codeField.inputType = InputType.TYPE_CLASS_NUMBER
        c.addView(ipField, Ui.lpm(this, 0, 8))
        c.addView(codeField, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Ghép TV", true) { pairTv() }, Ui.lpm(this, 0, 8))
        c.addView(Ui.button(this, "Gửi câu thử lên TV") { testTv() }, Ui.lpm(this, 0, 8))
        if (p.tvPaired) c.addView(Ui.button(this, "Bỏ ghép TV") {
            p.clearTv(); render()
        })
        return c
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

    /** Tìm TV tự động: gửi gói UDP quảng bá, app TV trả lời kèm địa chỉ. */
    private fun findTv() {
        status.setTextColor(Ui.SUB)
        status.text = "Đang tìm TV..."
        Thread {
            var ip = ""
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
            runOnUiThread {
                if (ip.isNotEmpty()) {
                    ipField.setText(ip)
                    status.text = "Thấy TV ở $ip. Nhập mã 8 số đang hiện trên TV rồi bấm Ghép TV."
                } else {
                    status.setTextColor(Ui.DANGER)
                    status.text = "Không thấy TV. Mở app TV, kiểm tra cùng Wi-Fi, hoặc nhập IP thủ công."
                }
            }
        }.start()
    }

    private fun pairTv() {
        val ip = ipField.text.toString().trim()
        status.setTextColor(Ui.SUB)
        status.text = "Đang ghép TV..."
        tvPost("/pair", codeField.text.toString().trim(), null, ip) { code, out ->
            if (code == 200) {
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
