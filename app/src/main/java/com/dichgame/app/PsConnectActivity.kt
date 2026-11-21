package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.metallic.chiaki.lib.DiscoveryService
import java.nio.charset.StandardCharsets

/**
 * Màn "Kết nối": máy đã ghép nối, game đang chơi, người chơi, và hai cách dùng:
 * "Dịch khi chơi TV" (bạn cầm tay cầm chơi trên TV, điện thoại vào bằng người chơi phụ chỉ để xem hình và dịch)
 * và "Chơi trên điện thoại" (hình và tay cầm ảo ngay trong app).
 */
class PsConnectActivity : Activity() {
    private lateinit var p: Prefs
    private var c: PsConsole? = null
    private var finder: PsFinder? = null
    // Mỗi lượt dò có một thế hệ riêng để loại callback đến muộn từ dịch vụ cũ.
    private var discoveryGeneration = 0
    private var hostReady = false
    private var hostSeen = false
    private var discoveryStartFailed = false
    private var statusView: TextView? = null
    private var pendingMode = ""
    private var pendingPlayer = 0
    private val ui = Handler(Looper.getMainLooper())
    private var waiting: AlertDialog? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        render()
        // Bản debug: mở thẳng bảng chất lượng hình để CI chụp ảnh (adb ... --es dg_demo quality)
        if (BuildConfig.DEBUG && intent.getStringExtra("dg_demo") == "quality")
            window.decorView.postDelayed({ if (!isFinishing && !isDestroyed) PsQuality.sheet(this, null) {} }, 1200)
    }

    override fun onResume() {
        super.onResume()
        render()
        startDiscovery()
    }

    override fun onPause() {
        super.onPause()
        stopDiscovery()
        waiting?.dismiss()
        waiting = null
    }

    // ---------------- Tìm trạng thái máy ----------------

    private fun startDiscovery() {
        if (finder != null || c == null || !PsNative.available) return
        val generation = ++discoveryGeneration
        hostReady = false
        hostSeen = false
        discoveryStartFailed = false
        val target = c?.host ?: ""
        // Bộ dò riêng của app: gửi thẳng qua Wi-Fi/LAN, broadcast của mạng, quét từng IP và gửi thẳng tới IP đã lưu.
        val f = PsFinder(this, listOf(target)) { hosts ->
            runOnUiThread {
                // Không nhận kết quả của lượt dò trước sau pause/resume hoặc đổi địa chỉ máy.
                if (isFinishing || isDestroyed || generation != discoveryGeneration || finder == null) return@runOnUiThread
                val h = hosts.firstOrNull { it.addr == c?.host }
                hostSeen = h != null
                hostReady = h?.ready == true
                drawStatus()
            }
        }
        finder = f
        try { f.start() } catch (_: Throwable) { discoveryStartFailed = true }
        if (f.failed) discoveryStartFailed = true
        drawStatus()
    }

    private fun stopDiscovery() {
        // Vô hiệu hóa kết quả trước khi dừng bất đồng bộ bộ dò hiện tại.
        discoveryGeneration++
        val f = finder ?: return
        finder = null
        Thread { try { f.stop() } catch (_: Throwable) {} }.start()
    }

    private fun drawStatus() {
        val v = statusView ?: return
        val con = c ?: return
        val (txt, col) = when {
            !PsNative.available -> "Máy này chưa hỗ trợ chơi PS5 trong app" to Ui.DANGER
            hostSeen && hostReady -> "Sẵn sàng · ${con.host}" to Ui.OK
            hostSeen && !hostReady -> "Đang nghỉ · sẽ tự bật khi bấm kết nối" to Ui.GOLD
            discoveryStartFailed -> (finder?.info ?: "Không thể khởi động dò mạng") + " · vẫn có thể bấm kết nối bằng địa chỉ đã lưu" to Ui.GOLD
            else -> "Đang tìm kiếm ${if (con.ps5) "PS5" else "PS4"} tại ${con.host}…" to Ui.SUB
        }
        v.text = "●  $txt"
        v.setTextColor(col)
    }

    // ---------------- Giao diện ----------------

    private fun render() {
        p = Prefs(this)
        c = PsStore.load(this)
        val con = c
        screen("Kết nối") {
            if (con == null) { empty(this); return@screen }
            val head = Ui.vbox(this@PsConnectActivity)
            head.gravity = Gravity.CENTER_HORIZONTAL
            head.addView(ImageView(this@PsConnectActivity).apply {
                setImageResource(R.drawable.ic_logo)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(Ui.dp(this@PsConnectActivity, 120), Ui.dp(this@PsConnectActivity, 120)))
            head.addView(Ui.tv(this@PsConnectActivity, con.name, 28f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }, Ui.lpm(this@PsConnectActivity, 10, 0))
            val ip = Ui.hbox(this@PsConnectActivity)
            ip.gravity = Gravity.CENTER
            ip.addView(Ui.tv(this@PsConnectActivity, con.host, 16f, Ui.SUB))
            ip.addView(Ui.tv(this@PsConnectActivity, "✎", 18f, Ui.GOLD).apply {
                setPadding(Ui.dp(this@PsConnectActivity, 12), 0, 0, 0)
                setOnClickListener { editHost() }
            })
            head.addView(ip, Ui.lpm(this@PsConnectActivity, 2, 0))
            val st = Ui.tv(this@PsConnectActivity, "", 13.5f, Ui.SUB).apply {
                gravity = Gravity.CENTER
                background = Ui.bg(this@PsConnectActivity, Ui.CARD2, 20)
                setPadding(Ui.dp(this@PsConnectActivity, 16), Ui.dp(this@PsConnectActivity, 8), Ui.dp(this@PsConnectActivity, 16), Ui.dp(this@PsConnectActivity, 8))
            }
            statusView = st
            head.addView(st, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = Ui.dp(this@PsConnectActivity, 10) })
            put(head, 0, 16)
            drawStatus()

            // Game đang chơi
            val g = Ui.card(this@PsConnectActivity, 14)
            g.addView(Ui.tv(this@PsConnectActivity, "Game đang chơi", 15f, Ui.TEXT, true))
            val name = PackStore.gameName(p)
            g.addView(Ui.tv(this@PsConnectActivity, if (name.isEmpty()) "Chưa chọn game (bấm để chọn trong Kho game)" else name, 14f, Ui.GOLD), Ui.lpm(this@PsConnectActivity, 4, 0))
            if (name.isNotEmpty() && p.packOn) {
                val (t, k, l) = PackStore.builtinCounts(name, Catalog.genreOf(name).ifEmpty { p.game.substringAfter(',', "").trim() })
                g.addView(Ui.tv(this@PsConnectActivity, "👑 Việt hoá: $t thuật ngữ · $k nhân vật · $l câu chuẩn", 12.5f, Ui.SUB), Ui.lpm(this@PsConnectActivity, 2, 0))
            }
            g.setOnClickListener { startActivity(Intent(this@PsConnectActivity, KhoGameActivity::class.java)) }
            put(g, 0, 12)

            // Người chơi
            val pc = Ui.card(this@PsConnectActivity, 14)
            val top = Ui.hbox(this@PsConnectActivity)
            top.addView(Ui.tv(this@PsConnectActivity, "Người chơi đã ghép nối", 15f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
            top.addView(Ui.tv(this@PsConnectActivity, "＋ Thêm", 15f, Ui.GOLD, true).apply {
                setOnClickListener { startActivity(Intent(this@PsConnectActivity, PsPairActivity::class.java)) }
            })
            pc.addView(top)
            val labels = { con.players.map { it.label } }
            pc.addView(Ui.dropRow(this@PsConnectActivity, "Khi dịch (chơi trên TV)", { con.players.getOrNull(con.tvPlayer)?.label ?: "-" }, labels) {
                con.tvPlayer = it; saveConsoleOrRestore(con)
            })
            pc.addView(Ui.divider(this@PsConnectActivity), Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1))
            pc.addView(Ui.dropRow(this@PsConnectActivity, "Khi chơi trên điện thoại", { con.players.getOrNull(con.phonePlayer)?.label ?: "-" }, labels) {
                con.phonePlayer = it; saveConsoleOrRestore(con)
            })
            pc.addView(Ui.tv(this@PsConnectActivity,
                "Nên chọn người chơi phụ khi dịch, để tay cầm của bạn trên TV không bị ngắt. Muốn điều khiển nhân vật chính của game 1 người từ điện thoại thì chọn người chơi chính (người đang chơi trên TV sẽ bị ngắt).",
                12f, Ui.SUB), Ui.lpm(this@PsConnectActivity, 6, 0))
            put(pc, 0, 12)

            // Nút chính
            val r1 = Ui.hbox(this@PsConnectActivity)
            r1.addView(Ui.button(this@PsConnectActivity, "💬  Dịch khi chơi TV", true) { start("tv") }, Ui.lp(0, WRAP_CONTENT, 1f))
            r1.addView(Ui.button(this@PsConnectActivity, "📺  Kết nối TV") { startActivity(Intent(this@PsConnectActivity, TvSetupActivity::class.java)) },
                Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@PsConnectActivity, 10) })
            put(r1, 0, 10)
            val r2 = Ui.hbox(this@PsConnectActivity)
            r2.addView(Ui.button(this@PsConnectActivity, "🎮  Chơi trên điện thoại") { start("phone") }, Ui.lp(0, WRAP_CONTENT, 1f))
            r2.addView(Ui.button(this@PsConnectActivity, "⚙") { PsQuality.sheet(this@PsConnectActivity, null) {} },
                Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@PsConnectActivity, 10) })
            put(r2, 0, 10)
            put(Ui.button(this@PsConnectActivity, "Chơi không dịch") { start("plain") }, 0, 16)

            put(Ui.tv(this@PsConnectActivity, "Quản lý ghép nối", 13f, Ui.SUB).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@PsConnectActivity, 8), 0, Ui.dp(this@PsConnectActivity, 8))
                setOnClickListener { manage() }
            }, 0, 0)
        }
    }

    private fun empty(root: LinearLayout) {
        val k = Ui.card(this)
        val unreadable = PsStore.hasStoredConsole(this)
        k.addView(Ui.tv(this,
            if (unreadable) "Không đọc được dữ liệu ghép nối đã lưu" else "Chưa ghép nối máy chơi game",
            18f, if (unreadable) Ui.DANGER else Ui.TEXT, true))
        k.addView(Ui.tv(this,
            if (unreadable) "App phát hiện vẫn còn dữ liệu ghép nối nhưng không giải mã được bằng khóa bảo mật trên thiết bị này. Dữ liệu cũ chưa bị xóa hoặc ghi đè. Hãy mở lại trên đúng điện thoại đã ghép nối; nếu đã đổi máy/cài lại app, bạn có thể cần ghép nối lại sau khi chủ động xử lý dữ liệu cũ."
            else "Ghép nối một lần để chơi và dịch ngay trong app, không cần mở app Remote Play khác. Bạn cần máy PS5 hoặc PS4 cùng mạng Wi-Fi, và nên có một người chơi phụ trên máy.",
            13.5f, Ui.SUB), Ui.lpm(this, 6, 12))
        k.addView(Ui.button(this, "Ghép nối PS5 / PS4", true) { startActivity(Intent(this, PsPairActivity::class.java)) })
        root.put(k, 0, 0)
    }

    private fun editHost() {
        val con = c ?: return
        val et = Ui.field(this, "192.168.1.20", con.host)
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Địa chỉ máy")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val h = et.text.toString().trim()
                if (Regex("^[0-9A-Za-z.:-]{3,80}$").matches(h)) {
                    con.host = h
                    if (saveConsoleOrRestore(con)) { stopDiscovery(); render(); startDiscovery() } else render()
                } else toast("Địa chỉ chưa đúng.")
            }
            .setNegativeButton("Hủy", null).show()
    }

    /** Khôi phục cấu hình đã lưu nếu Keystore hoặc ghi đĩa gặp lỗi. */
    private fun saveConsoleOrRestore(value: PsConsole?): Boolean {
        val saved = runCatching { PsStore.save(this, value) }.getOrDefault(false)
        if (!saved) {
            c = runCatching { PsStore.load(this) }.getOrNull()
            toast("Không thể lưu thay đổi ghép nối an toàn. Đã tải lại cấu hình đã lưu.")
        } else c = value
        return saved
    }

    private fun manage() {
        val con = c ?: return
        val opts = mutableListOf<String>()
        con.players.forEach { opts.add("Xóa ${it.label}") }
        opts.add("Xóa máy này và mọi người chơi")
        Ui.pick(this, "Quản lý ghép nối", opts) { i ->
            AlertDialog.Builder(this, R.style.DgDialog)
                .setMessage("${opts[i]}? Muốn dùng lại phải ghép nối lại. Nên xóa cả ghép nối cũ trong Cài đặt › Hệ thống › Chơi từ xa trên máy chơi game.")
                .setPositiveButton("Xóa") { _, _ ->
                    if (i < con.players.size) {
                        PsStore.removePlayer(con, i)
                        saveConsoleOrRestore(if (con.players.isEmpty()) null else con)
                    } else saveConsoleOrRestore(null)
                    render()
                }
                .setNegativeButton("Hủy", null).show()
        }
    }

    // ---------------- Bắt đầu chơi / dịch ----------------

    /** mode: "tv" (dịch khi chơi TV), "phone" (chơi trên điện thoại, có dịch), "plain" (chơi, không dịch). */
    private fun start(mode: String) {
        val con = c ?: return
        if (!PsNative.available) { toast("Máy này chưa hỗ trợ chơi PS5 trong app (cần điện thoại chip arm64)."); return }
        if (con.players.isEmpty()) { startActivity(Intent(this, PsPairActivity::class.java)); return }
        if (mode != "plain" && Prefs.isCloudOcr(p.lang) && p.geminiKey.isEmpty()) {
            toast("Chữ ${Prefs.srcNameOf(p.lang)} cần khóa Gemini. Nhập trong mục Cách dịch.")
            startActivity(Intent(this, TranslateActivity::class.java))
            return
        }
        pendingMode = mode
        pendingPlayer = if (mode == "tv") con.tvPlayer else con.phonePlayer
        if (PsStore.noAsk(this)) go() else PsQuality.sheet(this, { go() }) {}
    }

    private fun go() {
        val con = c ?: return
        if (hostSeen && !hostReady) {
            wake(con)
            val dlg = AlertDialog.Builder(this, R.style.DgDialog)
                .setTitle("Đang bật máy")
                .setMessage("Máy đang nghỉ, đợi khoảng 30 giây…")
                .setNegativeButton("Hủy") { _, _ -> waiting = null }
                .show()
            waiting = dlg
            var tries = 0
            val tick = object : Runnable {
                override fun run() {
                    if (waiting !== dlg) return
                    tries++
                    if (hostSeen && hostReady) { dlg.dismiss(); waiting = null; proceed(); return }
                    if (tries > 75) { dlg.dismiss(); waiting = null; toast("Máy chưa bật. Kiểm tra \"Giữ kết nối Internet\" và \"Cho phép bật máy qua mạng\", hoặc bật máy bằng tay."); return }
                    ui.postDelayed(this, 1000)
                }
            }
            ui.postDelayed(tick, 1000)
        } else proceed()
    }

    private fun wake(con: PsConsole) {
        val pl = con.players.getOrNull(pendingPlayer) ?: return
        val key = pl.registKey
        val end = key.indexOfFirst { it == 0.toByte() }
        val s = key.copyOfRange(0, if (end >= 0) end else key.size).toString(StandardCharsets.UTF_8)
        val cred = s.toULongOrNull(16) ?: return
        try { DiscoveryService.wakeup(null, con.host, cred, con.ps5) } catch (_: Throwable) {}
    }

    /**
     * Chơi trong app: dịch phụ đề chạy ngay trong màn chơi (lấy hình thẳng từ video Remote Play), nên không cần quyền
     * "Hiển thị trên các ứng dụng khác" và không cần ghi màn hình. Nếu dịch nền đang chạy sẵn (bật từ màn hình chính) thì dùng luôn cái đó.
     */
    private fun proceed() {
        if (pendingMode != "plain") p.playOn = pendingMode
        openPlay()
    }

    private fun openPlay() {
        val translate = pendingMode != "plain" && !CaptureService.active
        startActivity(Intent(this, PsPlayActivity::class.java)
            .putExtra(PsPlayActivity.EXTRA_MODE, if (pendingMode == "tv") "tv" else "phone")
            .putExtra(PsPlayActivity.EXTRA_PLAYER, pendingPlayer)
            .putExtra(PsPlayActivity.EXTRA_TRANSLATE, translate))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

/** Bảng "Chất lượng hình": độ phân giải và tốc độ mạng tối đa cho luồng Remote Play. */
object PsQuality {
    // Thêm mức 2–3 Mbps để ưu tiên giữ hình khi đường truyền rất yếu.
    private val RATES = listOf(0, 2, 3, 5, 8, 10, 15, 20, 30)

    /** Cấu hình đang yêu cầu máy chơi game (engine có thể tự hạ, ví dụ PS4 thường không chạy 1080p). */
    fun summary(a: Activity): String =
        StreamPolicy.summary(PsStore.effectiveRes(a), PsStore.fps(a), PsStore.codec(a), PsStore.rate(a))

    /** [play] khác null thì có nút "Chơi" (và công tắc không hỏi lại); null thì chỉ lưu cài đặt. [onChanged] gọi khi bấm nút cuối. */
    fun sheet(a: Activity, play: (() -> Unit)?, onChanged: () -> Unit) {
        Ui.sheet(a) { d ->
            addView(Ui.tv(a, "Chất lượng hình", 26f, Ui.TEXT, true))

            val c1 = Ui.card(a)
            c1.addView(Ui.tv(a, "Độ phân giải", 16f, Ui.TEXT, true))
            val h1 = Ui.vbox(a)
            val desc1 = Ui.tv(a, "", 12.5f, Ui.SUB)
            fun drawRes() {
                h1.removeAllViews()
                // Thứ tự giữ nguyên ba lựa chọn cũ; 540p/360p dùng các chỉ số mới để không đổi cài đặt đã lưu.
                h1.addView(Ui.segmented(a, listOf("Tự động", "720p", "1080p", "540p", "360p"), PsStore.res(a)) { PsStore.setRes(a, it); drawRes() })
                desc1.text = when (PsStore.res(a)) {
                    2 -> "1080p: hình nét nhất trong các mức hiện có, cần mạng ổn định và thiết bị giải mã tốt." +
                        (if (DecoderCaps.avc1080p60) "" else " Máy này không báo giải mã phần cứng 1080p 60 FPS, có thể giật; thử 720p nếu vậy.") +
                        " PS4 thường chỉ chạy tới 720p nên engine tự hạ xuống."
                    3 -> "540p: giảm đáng kể lượng dữ liệu hình, phù hợp khi mạng yếu hoặc độ trễ dao động."
                    4 -> "360p: ưu tiên khả năng duy trì luồng trên mạng rất yếu; hình sẽ ít chi tiết hơn."
                    1 -> "720p: cân bằng độ nét và tải mạng, phù hợp với đa số thiết bị."
                    else -> "Tự động: chọn ${StreamPolicy.height(PsStore.effectiveRes(a))}p cho máy này (1080p khi máy giải mã được 1080p 60 FPS bằng phần cứng và màn hình đủ lớn, không thì 720p). Chưa tự đổi độ phân giải theo tốc độ mạng giữa chừng; mạng yếu thì chọn tay mức thấp hơn."
                }
            }
            drawRes()
            c1.addView(h1, Ui.lpm(a, 8, 0))
            c1.addView(desc1, Ui.lpm(a, 8, 0))
            c1.addView(Ui.tv(a, "Giới hạn engine Remote Play hiện tại: stream gốc tối đa 1080p. Màn hình 4K chỉ có thể phóng to hình, không tạo video 4K thật.", 12f, Ui.SUB), Ui.lpm(a, 8, 4))
            addView(c1, Ui.lpm(a, 14, 0))

            val c2 = Ui.card(a)
            c2.addView(Ui.tv(a, "Tốc độ mạng (Mbps)", 16f, Ui.TEXT, true))
            val h2 = Ui.vbox(a)
            val desc2 = Ui.tv(a, "", 12.5f, Ui.SUB)
            fun drawRate() {
                h2.removeAllViews()
                val sel = RATES.indexOf(PsStore.rate(a)).coerceAtLeast(0)
                h2.addView(Ui.segmented(a, RATES.map { if (it == 0) "Tự động" else "$it" }, sel) { PsStore.setRate(a, RATES[it]); drawRate() })
                desc2.text = if (RATES[sel] == 0) "Dùng bitrate mặc định của engine. Nếu hình giật hoặc hay đứng, thử 2–5 Mbps để giảm tải đường truyền."
                else if (RATES[sel] <= 3) "Mức rất thấp ${RATES[sel]} Mbps: ưu tiên giảm tải đường truyền, hình có thể mềm và giảm chi tiết."
                else "Tối đa ${RATES[sel]} Mbps. Mạng yếu thì chọn thấp hơn để giảm nghẽn và rớt khung hình."
            }
            drawRate()
            c2.addView(h2, Ui.lpm(a, 8, 0))
            c2.addView(desc2, Ui.lpm(a, 8, 0))
            addView(c2, Ui.lpm(a, 12, 0))

            val c4 = Ui.card(a)
            c4.addView(Ui.tv(a, "Tốc độ khung hình", 16f, Ui.TEXT, true))
            val h4 = Ui.vbox(a)
            fun drawFps() {
                h4.removeAllViews()
                h4.addView(Ui.segmented(a, listOf("30 FPS", "60 FPS"), if (PsStore.fps(a) == 30) 0 else 1) { PsStore.setFps(a, if (it == 0) 30 else 60); drawFps() })
            }
            drawFps()
            c4.addView(h4, Ui.lpm(a, 8, 0))
            c4.addView(Ui.tv(a, "60 FPS mượt hơn (mặc định). 30 FPS giảm tải mạng và máy. Engine Remote Play không có mức cao hơn 60 FPS.", 12.5f, Ui.SUB), Ui.lpm(a, 8, 0))
            addView(c4, Ui.lpm(a, 12, 0))

            val c5 = Ui.card(a)
            c5.addView(Ui.tv(a, "Chuẩn nén hình", 16f, Ui.TEXT, true))
            val h5 = Ui.vbox(a)
            val desc5 = Ui.tv(a, "", 12.5f, Ui.SUB)
            fun drawCodec() {
                h5.removeAllViews()
                h5.addView(Ui.segmented(a, listOf("H.264", "H.265 (HEVC)"), PsStore.codec(a)) { PsStore.setCodec(a, it); drawCodec() })
                desc5.text = if (PsStore.codec(a) == StreamPolicy.CODEC_H265)
                    "H.265: ít vỡ hạt, giữ chi tiết tốt hơn khi cùng bitrate. Chỉ PS5 hỗ trợ (PS4 tự dùng H.264)." +
                        (if (DecoderCaps.hevc1080p60) "" else " Máy này không báo có bộ giải mã HEVC phần cứng 1080p, nên dùng H.264.") +
                        " Chưa thử trên mọi máy: nếu hình đen hoặc lỗi, quay lại H.264."
                else "H.264: ổn định nhất, chạy trên mọi máy (mặc định)."
            }
            drawCodec()
            c5.addView(h5, Ui.lpm(a, 8, 0))
            c5.addView(desc5, Ui.lpm(a, 8, 0))
            addView(c5, Ui.lpm(a, 12, 0))

            val c3 = Ui.card(a)
            c3.addView(Ui.tv(a, "Hướng màn hình khi chơi", 16f, Ui.TEXT, true))
            val h3 = Ui.vbox(a)
            val desc3 = Ui.tv(a, "", 12.5f, Ui.SUB)
            fun drawOrient() {
                h3.removeAllViews()
                val sel = PsStore.orient(a)
                h3.addView(Ui.segmented(a, listOf("Ngang", "Tự động", "Dọc"), sel) {
                    PsStore.setOrient(a, it)
                    // Đang ở màn chơi thì đổi hướng ngay; ở màn khác thì lần chơi sau mới áp dụng.
                    if (a is PsPlayActivity) PsStore.applyOrient(a)
                    drawOrient()
                })
                desc3.text = when (sel) {
                    PsStore.ORIENT_AUTO -> "Theo cảm biến xoay của máy (tôn trọng khoá xoay màn hình). Xoay thoải mái, phiên chơi không bị ngắt."
                    PsStore.ORIENT_PORTRAIT -> "Luôn dọc: hình ở trên, tay cầm ảo ở dưới."
                    else -> "Ưu tiên ngang (khuyên dùng): hình lớn, tay cầm trong suốt phủ lên hình, lật được cả hai chiều ngang."
                }
            }
            drawOrient()
            c3.addView(h3, Ui.lpm(a, 8, 0))
            c3.addView(desc3, Ui.lpm(a, 8, 0))
            addView(c3, Ui.lpm(a, 12, 0))

            if (play != null) {
                addView(Ui.switchRow(a, "Không hỏi lại khi bấm Chơi", "Đổi lại ở nút ⚙ cạnh nút Chơi trên điện thoại.", PsStore.noAsk(a)) { PsStore.setNoAsk(a, it) }, Ui.lpm(a, 14, 0))
                addView(Ui.button(a, "🎮  Chơi", true) { d.dismiss(); play() }, Ui.lpm(a, 14, 0))
            } else {
                addView(Ui.button(a, "Xong", true) { d.dismiss(); onChanged() }, Ui.lpm(a, 14, 0))
            }
        }
    }
}
