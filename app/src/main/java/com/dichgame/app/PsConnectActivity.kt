package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.metallic.chiaki.lib.CreateError
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.DiscoveryService
import com.metallic.chiaki.lib.DiscoveryServiceOptions
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Màn "Kết nối": máy đã ghép nối, game đang chơi, người chơi, và hai cách dùng:
 * "Dịch khi chơi TV" (bạn cầm tay cầm chơi trên TV, điện thoại vào bằng người chơi phụ chỉ để xem hình và dịch)
 * và "Chơi trên điện thoại" (hình và tay cầm ảo ngay trong app).
 */
class PsConnectActivity : Activity() {
    private lateinit var p: Prefs
    private var c: PsConsole? = null
    private var discovery: DiscoveryService? = null
    private var hostState: DiscoveryHost.State? = null
    private var hostSeen = false
    private var statusView: TextView? = null
    private var pendingMode = ""
    private var pendingPlayer = 0
    private val ui = Handler(Looper.getMainLooper())
    private var waiting: AlertDialog? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        render()
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
        if (discovery != null || c == null || !PsNative.available) return
        hostState = null
        hostSeen = false
        try {
            discovery = DiscoveryService(DiscoveryServiceOptions(16UL, 3UL, 500UL, InetSocketAddress("255.255.255.255", 987))) { hosts ->
                runOnUiThread {
                    // Discovery có thể gửi callback muộn sau onPause()/onDestroy().
                    // Chỉ cập nhật UI khi màn hình còn sống và dịch vụ này vẫn là dịch vụ hiện tại.
                    if (isFinishing || isDestroyed || discovery == null) return@runOnUiThread
                    val h = hosts.firstOrNull { it.hostAddr == c?.host }
                    hostSeen = h != null
                    hostState = h?.state
                    drawStatus()
                }
            }
        } catch (_: CreateError) { discovery = null } catch (_: Throwable) { discovery = null }
    }

    private fun stopDiscovery() {
        val d = discovery ?: return
        discovery = null
        Thread { try { d.dispose() } catch (_: Throwable) {} }.start()
    }

    private fun drawStatus() {
        val v = statusView ?: return
        val con = c ?: return
        val (txt, col) = when {
            !PsNative.available -> "Máy này chưa hỗ trợ chơi PS5 trong app" to Ui.DANGER
            hostSeen && hostState == DiscoveryHost.State.READY -> "Sẵn sàng · ${con.host}" to Ui.OK
            hostSeen && hostState == DiscoveryHost.State.STANDBY -> "Đang nghỉ · sẽ tự bật khi bấm kết nối" to Ui.GOLD
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
                con.tvPlayer = it; PsStore.save(this@PsConnectActivity, con)
            })
            pc.addView(Ui.divider(this@PsConnectActivity), Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1))
            pc.addView(Ui.dropRow(this@PsConnectActivity, "Khi chơi trên điện thoại", { con.players.getOrNull(con.phonePlayer)?.label ?: "-" }, labels) {
                con.phonePlayer = it; PsStore.save(this@PsConnectActivity, con)
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
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Địa chỉ máy")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val h = et.text.toString().trim()
                if (Regex("^[0-9A-Za-z.:-]{3,80}$").matches(h)) {
                    con.host = h; PsStore.save(this, con)
                    stopDiscovery(); render(); startDiscovery()
                } else toast("Địa chỉ chưa đúng.")
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun manage() {
        val con = c ?: return
        val opts = mutableListOf<String>()
        con.players.forEach { opts.add("Xóa ${it.label}") }
        opts.add("Xóa máy này và mọi người chơi")
        Ui.pick(this, "Quản lý ghép nối", opts) { i ->
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setMessage("${opts[i]}? Muốn dùng lại phải ghép nối lại. Nên xóa cả ghép nối cũ trong Cài đặt › Hệ thống › Chơi từ xa trên máy chơi game.")
                .setPositiveButton("Xóa") { _, _ ->
                    if (i < con.players.size) {
                        con.players.removeAt(i)
                        con.tvPlayer = con.tvPlayer.coerceIn(0, (con.players.size - 1).coerceAtLeast(0))
                        con.phonePlayer = con.phonePlayer.coerceIn(0, (con.players.size - 1).coerceAtLeast(0))
                        PsStore.save(this, if (con.players.isEmpty()) null else con)
                    } else PsStore.save(this, null)
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
        if (mode != "plain" && !Settings.canDrawOverlays(this)) {
            toast("Hãy bật \"Hiển thị trên các ứng dụng khác\" cho Dịch game, rồi quay lại.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
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
        if (hostSeen && hostState == DiscoveryHost.State.STANDBY) {
            wake(con)
            val dlg = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
                    if (hostState == DiscoveryHost.State.READY) { dlg.dismiss(); waiting = null; proceed(); return }
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
        try { DiscoveryService.wakeup(discovery, con.host, cred, con.ps5) } catch (_: Throwable) {}
    }

    private fun proceed() {
        if (pendingMode == "plain" || CaptureService.active) { if (pendingMode != "plain") p.playOn = pendingMode; openPlay(); return }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), 100)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == RESULT_OK && data != null) {
            val q = Prefs(this)
            q.playOn = pendingMode
            startForegroundService(Intent(this, CaptureService::class.java)
                .putExtra("code", res).putExtra("data", data)
                .putExtra("speak", q.speak).putExtra("lang", q.lang)
                .putExtra("automenu", q.autoMenu).putExtra("slow", q.slow).putExtra("narrate", q.narrate))
            CaptureService.active = true
            openPlay()
        }
    }

    private fun openPlay() {
        startActivity(Intent(this, PsPlayActivity::class.java)
            .putExtra(PsPlayActivity.EXTRA_MODE, if (pendingMode == "tv") "tv" else "phone")
            .putExtra(PsPlayActivity.EXTRA_PLAYER, pendingPlayer))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

/** Bảng "Chất lượng hình": độ phân giải và tốc độ mạng tối đa cho luồng Remote Play. */
object PsQuality {
    private val RATES = listOf(0, 5, 10, 15, 20, 30)

    fun summary(a: Activity): String {
        val r = when (PsStore.res(a)) { 1 -> "720p"; 2 -> "1080p"; else -> "720p" }
        val m = PsStore.rate(a)
        return r + " · " + if (m == 0) "tự động" else "$m Mbps"
    }

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
                h1.addView(Ui.segmented(a, listOf("Tự động", "720p", "1080p"), PsStore.res(a)) { PsStore.setRes(a, it); drawRes() })
                desc1.text = when (PsStore.res(a)) {
                    2 -> "1080p: nét hơn, tốn mạng và nóng máy hơn. Hợp với máy mạnh, mạng 5 GHz tốt."
                    1 -> "720p: mượt, mát máy, hợp với hầu hết điện thoại."
                    else -> "720p: mượt, mát máy, hợp với hầu hết điện thoại. Tự động dùng 720p."
                }
            }
            drawRes()
            c1.addView(h1, Ui.lpm(a, 8, 0))
            c1.addView(desc1, Ui.lpm(a, 8, 0))
            addView(c1, Ui.lpm(a, 14, 0))

            val c2 = Ui.card(a)
            c2.addView(Ui.tv(a, "Tốc độ mạng (Mbps)", 16f, Ui.TEXT, true))
            val h2 = Ui.vbox(a)
            val desc2 = Ui.tv(a, "", 12.5f, Ui.SUB)
            fun drawRate() {
                h2.removeAllViews()
                val sel = RATES.indexOf(PsStore.rate(a)).coerceAtLeast(0)
                h2.addView(Ui.segmented(a, RATES.map { if (it == 0) "Tự động" else "$it" }, sel) { PsStore.setRate(a, RATES[it]); drawRate() })
                desc2.text = if (RATES[sel] == 0) "Tự động: 10 Mbps cho 720p, 15 Mbps cho 1080p. Nếu hình giật, thử hạ xuống 5 hoặc 10."
                else "Tối đa ${RATES[sel]} Mbps. Mạng yếu thì chọn thấp hơn để đỡ giật."
            }
            drawRate()
            c2.addView(h2, Ui.lpm(a, 8, 0))
            c2.addView(desc2, Ui.lpm(a, 8, 0))
            addView(c2, Ui.lpm(a, 12, 0))

            if (play != null) {
                addView(Ui.switchRow(a, "Không hỏi lại khi bấm Chơi", "Đổi lại ở nút ⚙ cạnh nút Chơi trên điện thoại.", PsStore.noAsk(a)) { PsStore.setNoAsk(a, it) }, Ui.lpm(a, 14, 0))
                addView(Ui.button(a, "🎮  Chơi", true) { d.dismiss(); play() }, Ui.lpm(a, 14, 0))
            } else {
                addView(Ui.button(a, "Xong", true) { d.dismiss(); onChanged() }, Ui.lpm(a, 14, 0))
            }
        }
    }
}
