package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Màn hình chính: chọn Dịch / Thuyết minh, bắt đầu, và vào các phần cài đặt. */
class MainActivity : Activity() {
    private lateinit var p: Prefs

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun open(c: Class<*>) = startActivity(Intent(this, c))

    private fun render() {
        p = Prefs(this)
        screen("", false) {
            put(header(), 4, 12)
            put(modeCard(), 0, 12)
            put(navCard("📺", "Phụ đề trên TV",
                if (p.tvPaired) "Đã ghép TV ${p.tvIp}" else "Bản dịch hiện ngay trên hình",
                if (p.tvPaired) "ĐÃ GHÉP" else null) { open(TvSetupActivity::class.java) }, 0, 12)
            put(navCard("▶", "Dịch video trên web", "Mở trang video, dịch phụ đề ngay khi xem", null) {
                open(WebTranslateActivity::class.java)
            }, 0, 12)
            val row1 = Ui.hbox(this@MainActivity)
            row1.addView(tile("🌐", "Cách dịch", engineLabel(), "AI") { open(TranslateActivity::class.java) }, Ui.lp(0, WRAP_CONTENT, 1f))
            row1.addView(tile("🎙", "Giọng đọc", voiceLabel(), null) { open(SettingsActivity::class.java) }, tileLp())
            put(row1, 0, 12)
            val row2 = Ui.hbox(this@MainActivity)
            row2.addView(tile("🕹", "Kho game", p.game.substringBefore(',').ifEmpty { "Chưa chọn game" }, "MỚI") {
                open(KhoGameActivity::class.java)
            }, Ui.lp(0, WRAP_CONTENT, 1f))
            row2.addView(tile("💡", "Hướng dẫn", "Cách dùng từng bước", "1-5") { open(GuideActivity::class.java) }, tileLp())
            put(row2, 0, 12)
            put(Ui.tv(this@MainActivity, "Dịch game ${versionName()} · Chạy hoàn toàn trên máy bạn, chỉ gửi chữ ra ngoài khi bạn bật dịch AI.", 12f, Ui.SUB), 8, 0)
        }
    }

    private fun versionName(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }

    private fun tileLp() = Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@MainActivity, 12) }

    private fun engineLabel() = when (p.engine) {
        Prefs.ENGINE_CLOUD -> "CloudAPI"
        Prefs.ENGINE_GEMINI -> "Google Gemini"
        else -> "Dịch trên máy"
    }

    private fun voiceLabel(): String {
        val v = p.voice.ifEmpty { "Mặc định" }
        return "$v · ${String.format("%.1f", p.rate10 / 10f).replace('.', ',')}x"
    }

    private fun header(): View {
        val row = Ui.hbox(this)
        val icon = TextView(this).apply {
            text = "🎮"; textSize = 26f; gravity = Gravity.CENTER
            background = Ui.goldBg(this@MainActivity, 14)
        }
        row.addView(icon, Ui.lp(Ui.dp(this, 52), Ui.dp(this, 52)))
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, "Dịch game", 22f, Ui.TEXT, true))
        col.addView(Ui.tv(this, "Dịch game, hiểu mọi lời thoại", 13f, Ui.SUB))
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@MainActivity, 12) })
        row.addView(roundBtn("★") { open(FeaturesActivity::class.java) })
        row.addView(roundBtn("⚙") { open(SettingsActivity::class.java) }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@MainActivity, 8) })
        return row
    }

    private fun roundBtn(s: String, onClick: () -> Unit): View = TextView(this).apply {
        text = s; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Ui.GOLD)
        background = Ui.bg(this@MainActivity, Ui.CARD2, 24, Ui.LINE, 1)
        minimumWidth = Ui.dp(this@MainActivity, 46); minimumHeight = Ui.dp(this@MainActivity, 46)
        setOnClickListener { onClick() }
    }

    private fun modeCard(): View {
        val running = CaptureService.active
        val c = Ui.card(this)
        val top = Ui.hbox(this)
        top.addView(Ui.tv(this, if (running) "● Đang chạy" else "● Sẵn sàng", 13f, if (running) Ui.OK else Ui.SUB, true).apply {
            background = Ui.bg(this@MainActivity, Ui.CARD2, 16)
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 6))
        })
        top.addView(View(this), Ui.lp(0, 1, 1f))
        top.addView(segment())
        top.addView(roundBtn("?") { help() }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@MainActivity, 8) })
        c.addView(top)

        val narrate = p.narrate
        val lang = Ui.hbox(this)
        lang.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14))
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, if (narrate) "Tiếng Việt" else "${Prefs.langName(p.lang)} → Tiếng Việt", 20f, Ui.TEXT, true))
        col.addView(Ui.tv(this,
            if (narrate) "Đọc to phụ đề có sẵn, không dịch. Hợp với phim Vietsub, game Việt hóa"
            else "Đọc chữ trên màn hình rồi dịch sang tiếng Việt. Chạm để đổi ngôn ngữ game.", 13f, Ui.SUB).apply {
            setPadding(0, Ui.dp(this@MainActivity, 4), 0, 0)
        })
        lang.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
        if (!narrate) {
            lang.addView(Ui.tv(this, "›", 26f, Ui.SUB))
            lang.setOnClickListener {
                Ui.pick(this, "Ngôn ngữ trong game", Prefs.LANGS.filter { it.first != "vi" }.map { Prefs.langLabel(it.first) }) { i ->
                    p.lang = Prefs.LANGS.filter { it.first != "vi" }[i].first
                    render()
                }
            }
        }
        c.addView(lang)

        val btn = if (running) {
            Ui.button(this, "■  Dừng") { stopService(); }
        } else {
            Ui.button(this, if (narrate) "🎮  Bắt đầu thuyết minh" else "🎮  Bắt đầu dịch", true) { begin() }
        }
        c.addView(btn)
        return c
    }

    private fun segment(): View {
        val seg = Ui.hbox(this)
        seg.background = Ui.bg(this, Ui.CARD2, 22)
        fun item(label: String, on: Boolean, click: () -> Unit) = TextView(this).apply {
            text = label; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(if (on) Color.parseColor("#2A1A08") else Ui.TEXT)
            if (on) background = Ui.goldBg(this@MainActivity, 20)
            setPadding(Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 8))
            setOnClickListener { click() }
        }
        seg.addView(item("Dịch", !p.narrate) { if (!CaptureService.active) { p.narrate = false; render() } else busyToast() })
        seg.addView(item("Thuyết minh", p.narrate) { if (!CaptureService.active) { p.narrate = true; render() } else busyToast() })
        return seg
    }

    private fun busyToast() = Toast.makeText(this, "Hãy dừng trước khi đổi chế độ.", Toast.LENGTH_SHORT).show()

    private fun help() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Hai chế độ")
            .setMessage("Dịch: đọc chữ trên màn hình (Anh, Nhật, Hàn, Trung, Pháp, Đức, Tây Ban Nha, Nga, Thái, Ả Rập... tổng cộng 28 thứ tiếng) rồi dịch sang tiếng Việt.\n\n" +
                "Thuyết minh: game hoặc phim đã có phụ đề tiếng Việt, app chỉ đọc to phụ đề, không dịch.\n\n" +
                "Sau khi bấm Bắt đầu, kéo khung xanh tới dòng phụ đề rồi bấm \"Chỉnh/Khóa khung\" trong thông báo.")
            .setPositiveButton("Đã hiểu", null).show()
    }

    private fun navCard(icon: String, title: String, sub: String, badge: String?, onClick: () -> Unit): View {
        val c = Ui.card(this, 14)
        val row = Ui.hbox(this)
        row.addView(Ui.tv(this, icon, 24f).apply { setPadding(0, 0, Ui.dp(this@MainActivity, 14), 0) })
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, title, 17f, Ui.TEXT, true))
        col.addView(Ui.tv(this, sub, 13f, Ui.SUB))
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
        if (badge != null) row.addView(Ui.badge(this, badge), Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = Ui.dp(this@MainActivity, 8) })
        row.addView(Ui.tv(this, "›", 26f, Ui.SUB))
        c.addView(row)
        c.setOnClickListener { onClick() }
        return c
    }

    private fun tile(icon: String, title: String, sub: String, badge: String?, onClick: () -> Unit): View {
        val c = Ui.card(this, 14)
        val top = Ui.hbox(this)
        top.addView(Ui.tv(this, icon, 22f))
        top.addView(View(this), Ui.lp(0, 1, 1f))
        if (badge != null) top.addView(Ui.badge(this, badge))
        c.addView(top)
        c.addView(Ui.tv(this, title, 17f, Ui.TEXT, true).apply { setPadding(0, Ui.dp(this@MainActivity, 18), 0, 0) })
        c.addView(Ui.tv(this, sub, 13f, Ui.SUB).apply { maxLines = 2 })
        c.setOnClickListener { onClick() }
        return c
    }

    // ---------------- Bắt đầu / dừng ----------------

    private fun stopService() {
        startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP))
        CaptureService.active = false
        render()
    }

    private fun begin() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy bật \"Hiển thị trên các ứng dụng khác\" cho Dịch game, rồi quay lại bấm Bắt đầu.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!p.narrate && Prefs.isCloudOcr(p.lang)) {
            if (p.geminiKey.isEmpty()) {
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Cần khóa Gemini")
                    .setMessage("Chữ ${Prefs.srcNameOf(p.lang)} không đọc được trên máy, nên app gửi ảnh phần màn hình cần đọc cho Google Gemini để lấy chữ. " +
                        "Hãy nhập khóa Gemini (lấy miễn phí ở aistudio.google.com) trong mục Cách dịch. Ảnh chỉ được gửi khi bạn chọn ngôn ngữ này.")
                    .setPositiveButton("Mở Cách dịch") { _, _ -> startActivity(Intent(this, TranslateActivity::class.java)) }
                    .setNegativeButton("Để sau", null).show()
                return
            }
            Toast.makeText(this, "Chữ ${Prefs.srcNameOf(p.lang)} được đọc bằng Gemini: ảnh khung phụ đề sẽ gửi cho Google.", Toast.LENGTH_LONG).show()
        }
        Ui.sheet(this) { d ->
            addView(Ui.tv(this@MainActivity, "Bạn chơi ở đâu?", 24f, Ui.TEXT, true))
            addView(choice("📺", "Chơi trên TV", "Game hiện trên TV như bình thường, điện thoại hiện bản dịch và đọc thoại.") {
                d.dismiss(); chooseTv()
            }, Ui.lpm(this@MainActivity, 16, 0))
            addView(choice("📱", "Chơi trên điện thoại", "Hình game hiện trên điện thoại, bản dịch hiện ngay trên hình.") {
                d.dismiss(); p.playOn = "phone"; remotePicker()
            }, Ui.lpm(this@MainActivity, 12, 0))
        }
    }

    private fun choice(icon: String, title: String, sub: String, onClick: () -> Unit): View {
        val c = Ui.card(this, 16)
        c.background = Ui.bg(this, Ui.CARD, 20, Ui.GOLD, 1)
        val row = Ui.hbox(this)
        row.addView(Ui.tv(this, icon, 26f).apply { setPadding(0, 0, Ui.dp(this@MainActivity, 14), 0) })
        val col = Ui.vbox(this)
        col.addView(Ui.tv(this, title, 18f, Ui.TEXT, true))
        col.addView(Ui.tv(this, sub, 13.5f, Ui.SUB).apply { setPadding(0, Ui.dp(this@MainActivity, 4), 0, 0) })
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
        row.addView(Ui.tv(this, "›", 26f, Ui.TEXT))
        c.addView(row)
        c.setOnClickListener { onClick() }
        return c
    }

    private fun chooseTv() {
        p.playOn = "tv"
        if (p.tvPaired) { capture(); return }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Chưa ghép TV")
            .setMessage("Ghép TV để phụ đề hiện ngay trên hình game ở TV. Bạn cũng có thể tiếp tục, bản dịch sẽ chỉ hiện trên điện thoại.")
            .setPositiveButton("Ghép TV") { _, _ -> open(TvSetupActivity::class.java) }
            .setNegativeButton("Tiếp tục không TV") { _, _ -> capture() }
            .show()
    }

    private var pendingApp: String? = null

    /** (gói, tên, mô tả) các app chơi từ xa / chơi đám mây thường dùng. */
    private val remoteApps = listOf(
        Triple("com.playstation.remoteplay", "PS Remote Play", "Chơi PS4/PS5 từ xa (app chính thức của Sony)"),
        Triple("com.metallic.chiaki", "Chiaki", "Chơi PS4/PS5 từ xa (mã nguồn mở)"),
        Triple("com.limelight", "Moonlight", "Chơi game từ máy tính qua Sunshine hoặc GeForce"),
        Triple("com.valvesoftware.steamlink", "Steam Link", "Chơi game Steam trên điện thoại"),
        Triple("com.nvidia.geforcenow", "GeForce NOW", "Chơi game đám mây"),
        Triple("com.microsoft.xboxone.smartglass", "Xbox", "Chơi Xbox từ xa, Game Pass")
    )

    /** Chơi trên điện thoại: mở sẵn app chơi từ xa, bản dịch hiện đè lên hình game. Dịch game không tự điều khiển máy chơi game. */
    private fun remotePicker() {
        val installed = remoteApps.filter { packageManager.getLaunchIntentForPackage(it.first) != null }
        Ui.sheet(this) { d ->
            addView(Ui.tv(this@MainActivity, "Mở app chơi từ xa?", 24f, Ui.TEXT, true))
            addView(Ui.tv(this@MainActivity,
                "Dịch game mở app chơi từ xa bạn đã cài rồi hiện bản dịch đè lên hình game. Việc điều khiển máy chơi game do app đó làm.",
                13f, Ui.SUB), Ui.lpm(this@MainActivity, 6, 0))
            for (a in installed) {
                addView(choice("🎮", a.second, a.third) { d.dismiss(); pendingApp = a.first; capture() }, Ui.lpm(this@MainActivity, 12, 0))
            }
            if (installed.isEmpty()) {
                addView(choice("📥", "Cài PS Remote Play", "Chưa thấy app chơi từ xa nào trên máy. Mở trang cài đặt.") {
                    d.dismiss(); openStore("com.playstation.remoteplay")
                }, Ui.lpm(this@MainActivity, 12, 0))
            }
            addView(choice("📱", "Mình tự mở app", "Chỉ bật dịch, bạn tự mở app hoặc hình game.") {
                d.dismiss(); pendingApp = null; capture()
            }, Ui.lpm(this@MainActivity, 12, 0))
        }
    }

    private fun openStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) } catch (_: Exception) {}
        }
    }

    private fun capture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), 100)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == RESULT_OK && data != null) {
            val q = Prefs(this)
            val i = Intent(this, CaptureService::class.java)
                .putExtra("code", res).putExtra("data", data)
                .putExtra("speak", q.speak)
                .putExtra("lang", q.lang)
                .putExtra("automenu", q.autoMenu).putExtra("slow", q.slow).putExtra("narrate", q.narrate)
            startForegroundService(i)
            CaptureService.active = true
            Toast.makeText(this, "Đang chạy. Quay lại game, kéo khung xanh tới dòng phụ đề.", Toast.LENGTH_LONG).show()
            val app = pendingApp
            pendingApp = null
            val launch = if (app != null) packageManager.getLaunchIntentForPackage(app) else null
            if (launch != null) startActivity(launch) else moveTaskToBack(true)
        }
    }
}
