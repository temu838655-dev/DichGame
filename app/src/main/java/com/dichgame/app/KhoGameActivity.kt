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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Kho game Việt hoá: chọn game đang chơi, lọc theo nền tảng (PS5, PS4, Xbox, PC, Steam, Switch), xem gói Việt hoá của game
 * (thuật ngữ, nhân vật, câu thông báo chuẩn) và cách chơi trên từng nền tảng. Chọn một game sẽ chuyển sang (hoặc tạo) hồ sơ riêng
 * tên game đó, nhớ khung phụ đề riêng, và gói Việt hoá của game được áp dụng khi dịch.
 */
class KhoGameActivity : Activity() {
    private lateinit var p: Prefs
    private var tab = 0
    private var query = ""
    private var plat: Plat? = null
    private lateinit var chips: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var statusRow: LinearLayout
    private var status = 0 // 0 tất cả, 1 có gói Việt hoá riêng, 2 dịch AI theo thể loại
    private val vietMemo = HashMap<String, Boolean>()

    /** Game có gói Việt hoá riêng không (nhớ kết quả vì tra khá nặng). */
    private fun isViet(name: String): Boolean = vietMemo.getOrPut(name) { VietHoa.hasSpecific(name) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        tab = if (p.played.isEmpty()) 1 else 0
        // chỉ dùng cho bản debug: mở thẳng bộ lọc / chi tiết để chụp ảnh kiểm tra
        if (BuildConfig.DEBUG) {
            intent.getStringExtra("plat")?.let { k -> plat = Plat.values().firstOrNull { it.name.equals(k, true) }; tab = 1 }
        }
        screen("Kho game Việt hoá") {
            put(Ui.tv(this@KhoGameActivity, "Chọn game để dịch đúng tên riêng, xưng hô và giọng nhân vật. Mỗi game có hồ sơ riêng, nhớ khung phụ đề riêng.", 13f, Ui.SUB), 0, 10)
            val search = Ui.field(this@KhoGameActivity, "Tìm game", "")
            search.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty().trim(); fill() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
            put(search, 0, 10)
            chips = Ui.hbox(this@KhoGameActivity)
            val hs = HorizontalScrollView(this@KhoGameActivity).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
            put(hs, 0, 10)
            tabs = Ui.hbox(this@KhoGameActivity)
            put(tabs, 0, 10)
            statusRow = Ui.hbox(this@KhoGameActivity)
            put(statusRow, 0, 10)
            val row = Ui.hbox(this@KhoGameActivity)
            row.addView(Ui.button(this@KhoGameActivity, "＋  Game khác") { custom() }, Ui.lp(0, WRAP_CONTENT, 1f))
            row.addView(Ui.button(this@KhoGameActivity, "📦  Gói Việt hoá") {
                startActivity(Intent(this@KhoGameActivity, PackActivity::class.java))
            }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@KhoGameActivity, 10) })
            put(row, 0, 14)
            grid = Ui.vbox(this@KhoGameActivity)
            put(grid, 0, 0)
            put(Ui.switchRow(this@KhoGameActivity, "Tải ảnh bìa từ Steam",
                "Chỉ gửi tên game tới Steam để lấy ảnh bìa, rồi lưu trên máy. Game không có trên Steam dùng ảnh vẽ sẵn. Ảnh bìa thuộc về nhà phát hành.", p.covers) { p.covers = it; fill() }, 14, 0)
            drawChips()
            drawTabs()
            fill()
        }
        if (BuildConfig.DEBUG) intent.getStringExtra("detail")?.let { n -> Catalog.GAMES.firstOrNull { it.first.contains(n, true) }?.let { detail(it.first) } }
    }

    private fun drawChips() {
        chips.removeAllViews()
        val all = listOf<Plat?>(null) + Plat.values().toList()
        for (pl in all) {
            val on = pl == plat
            chips.addView(TextView(this).apply {
                text = pl?.label ?: "Tất cả"
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(if (on) Color.parseColor("#2A1A08") else Ui.TEXT)
                background = if (on) Ui.goldBg(this@KhoGameActivity, 18) else Ui.bg(this@KhoGameActivity, Ui.CARD2, 18, Ui.LINE, 1)
                setPadding(Ui.dp(this@KhoGameActivity, 16), Ui.dp(this@KhoGameActivity, 8), Ui.dp(this@KhoGameActivity, 16), Ui.dp(this@KhoGameActivity, 8))
                setOnClickListener { plat = pl; drawChips(); fill() }
            }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = Ui.dp(this@KhoGameActivity, 8) })
        }
    }

    private fun drawTabs() {
        tabs.removeAllViews()
        tabs.addView(Ui.segmented(this, listOf("Đã chơi", "Tất cả"), tab) { tab = it; drawTabs(); fill() }, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
    }

    /** Ba tab trạng thái có số đếm: Tất cả / Việt hoá (gói riêng của game) / Dịch AI (theo thể loại). */
    private fun drawStatus(all: Int, viet: Int, ai: Int) {
        statusRow.removeAllViews()
        statusRow.addView(Ui.segmented(this, listOf("Tất cả $all", "Việt hoá $viet", "Dịch AI $ai"), status) { status = it; fill() }, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun fill() {
        grid.removeAllViews()
        val q = query.lowercase()
        val known = Catalog.GAMES.map { it.first }.toSet()
        val base = if (tab == 0) p.played else Catalog.GAMES.map { it.first }
        val pool = base.filter { n ->
            (q.isEmpty() || n.lowercase().contains(q)) &&
                (plat == null || n !in known || Catalog.runsOn(n, plat!!)) // game tự thêm luôn hiện
        }
        val viet = pool.count { isViet(it) }
        drawStatus(pool.size, viet, pool.size - viet)
        val list = when (status) {
            1 -> pool.filter { isViet(it) }
            2 -> pool.filter { !isViet(it) }
            else -> pool
        }
        if (list.isEmpty()) {
            grid.addView(Ui.tv(this,
                if (tab == 0 && q.isEmpty() && plat == null) "Chưa có game nào. Chọn ở tab Tất cả, hoặc bấm Game khác để nhập tên game bạn đang chơi."
                else "Không thấy game này. Thử bỏ bộ lọc nền tảng, hoặc bấm Game khác để nhập tên.", 14f, Ui.SUB), Ui.lpm(this, 12, 0))
            return
        }
        val cur = p.game
        for (row in list.chunked(3)) {
            val r = Ui.hbox(this)
            r.gravity = Gravity.TOP
            for (k in 0 until 3) {
                val name = row.getOrNull(k)
                val cell: View = if (name == null) View(this) else cellOf(name, cur.startsWith(name))
                r.addView(cell, Ui.lp(0, WRAP_CONTENT, 1f).apply { if (k > 0) leftMargin = Ui.dp(this@KhoGameActivity, 10) })
            }
            grid.addView(r, Ui.lpm(this, 0, 14))
        }
    }

    private fun platLine(name: String): String {
        val ps = Catalog.platformsOf(name)
        val s = if (ps.isEmpty()) "" else ps.joinToString(" · ") { it.label }
        return s
    }

    private fun cellOf(name: String, selected: Boolean): View {
        val col = Ui.vbox(this)
        val cover = CoverView(this, name).apply { chosen = selected; vietHoa = isViet(name) }
        col.addView(cover, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
        Covers.get(this, name) { b -> cover.bmp = b }
        col.addView(Ui.tv(this, name, 12.5f, if (selected) Ui.GOLD else Ui.TEXT).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, Ui.dp(this@KhoGameActivity, 6), 0, 0)
        })
        val pl = platLine(name)
        if (pl.isNotEmpty()) col.addView(Ui.tv(this, pl, 10.5f, Ui.SUB).apply { gravity = Gravity.CENTER; maxLines = 2 })
        col.setOnClickListener { detail(name) }
        return col
    }

    private fun genreOf(name: String) = Catalog.genreOf(name)

    private fun detail(name: String) {
        val g = genreOf(name)
        val (sp, ge, co) = VietHoa.counts(name, g)
        val cast = VietHoa.castFor(name)
        val lines = VietHoa.linesFor(name).size
        Ui.sheet(this) { d ->
            val head = Ui.hbox(this@KhoGameActivity)
            head.gravity = Gravity.TOP
            val big = CoverView(this@KhoGameActivity, name)
            Covers.get(this@KhoGameActivity, name) { b -> big.bmp = b }
            head.addView(big, Ui.lp(Ui.dp(this@KhoGameActivity, 108), WRAP_CONTENT))
            val info = Ui.vbox(this@KhoGameActivity)
            info.addView(Ui.tv(this@KhoGameActivity, name, 21f, Ui.TEXT, true))
            if (g.isNotEmpty()) info.addView(Ui.tv(this@KhoGameActivity, g.replaceFirstChar { it.uppercase() }, 13.5f, Ui.SUB), Ui.lpm(this@KhoGameActivity, 2, 0))
            val ps = Catalog.platformsOf(name)
            if (ps.isNotEmpty()) {
                val r = Ui.hbox(this@KhoGameActivity)
                for (x in ps) r.addView(Ui.badge(this@KhoGameActivity, x.label), Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = Ui.dp(this@KhoGameActivity, 6) })
                info.addView(r, Ui.lpm(this@KhoGameActivity, 10, 0))
            }
            info.addView(Ui.tv(this@KhoGameActivity,
                if (isViet(name)) "Việt hoá riêng của game" else "Dịch AI theo thể loại",
                13f, if (isViet(name)) Ui.GOLD else Ui.OK, true), Ui.lpm(this@KhoGameActivity, 10, 0))
            head.addView(info, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@KhoGameActivity, 14) })
            addView(head)
            // Gói Việt hoá
            val c = Ui.card(this@KhoGameActivity, 14)
            c.addView(Ui.tv(this@KhoGameActivity, "👑  Gói Việt hoá", 16f, Ui.GOLD, true))
            c.addView(Ui.tv(this@KhoGameActivity,
                "Thuật ngữ: ${sp + ge + co}" + (if (sp > 0) " ($sp riêng game, $ge theo thể loại, $co chung)" else " ($ge theo thể loại, $co chung)") +
                    (if (cast.isNotEmpty()) "\nThẻ nhân vật: ${cast.size} người (xưng hô, giọng, nhịp nói)" else "\nChưa có thẻ nhân vật, bạn tự thêm được trong Gói Việt hoá") +
                    "\nCâu thông báo chuẩn: $lines (YOU DIED, Quest complete...)",
                13.5f, Ui.TEXT), Ui.lpm(this@KhoGameActivity, 6, 0))
            c.addView(Ui.tv(this@KhoGameActivity,
                "Áp dụng khi dịch bằng AI lẫn dịch trên máy: tên riêng, chiêu thức, địa danh luôn đúng một cách gọi. Gói do người làm app soạn theo cách gọi phổ biến của cộng đồng, " +
                    "không phải bản dịch chính thức của nhà phát hành. Sửa hoặc nhập thêm gói trong mục Gói Việt hoá.", 12f, Ui.SUB), Ui.lpm(this@KhoGameActivity, 6, 0))
            addView(c, Ui.lpm(this@KhoGameActivity, 12, 0))
            // Cách chơi theo nền tảng
            val start = ps.firstOrNull() ?: Plat.PS5
            val guide = Ui.tv(this@KhoGameActivity, RemotePlay.guide(start), 13f, Ui.TEXT)
            val appsBox = Ui.vbox(this@KhoGameActivity)
            val nguonBox = Ui.vbox(this@KhoGameActivity)
            fun showNguon(pl: Plat, game: String) {
                nguonBox.removeAllViews()
                for (n in NguonVietHoa.forPlatform(pl)) {
                    val row = Ui.vbox(this@KhoGameActivity)
                    row.background = Ui.bg(this@KhoGameActivity, Ui.CARD2, 14, Ui.LINE, 1)
                    val pd = Ui.dp(this@KhoGameActivity, 12)
                    row.setPadding(pd, pd, pd, pd)
                    row.addView(Ui.tv(this@KhoGameActivity, n.name, 14.5f, Ui.TEXT, true))
                    row.addView(Ui.tv(this@KhoGameActivity, n.note, 12f, Ui.SUB), Ui.lpm(this@KhoGameActivity, 2, 0))
                    val br = Ui.hbox(this@KhoGameActivity)
                    br.addView(Ui.button(this@KhoGameActivity, "Mở trang") { web(n.url) }, Ui.lp(0, WRAP_CONTENT, 1f))
                    val su = NguonVietHoa.searchUrl(n, game)
                    if (su != null) br.addView(Ui.button(this@KhoGameActivity, "Tìm game này") { web(su) },
                        Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@KhoGameActivity, 8) })
                    row.addView(br, Ui.lpm(this@KhoGameActivity, 8, 0))
                    nguonBox.addView(row, Ui.lpm(this@KhoGameActivity, 6, 0))
                }
            }
            fun showApps(pl: Plat) {
                guide.text = RemotePlay.guide(pl)
                appsBox.removeAllViews()
                for (a in RemotePlay.forPlatform(pl)) appsBox.addView(Ui.button(this@KhoGameActivity, "${a.name}  ·  ${a.desc}") { openApp(a) }, Ui.lpm(this@KhoGameActivity, 6, 0))
                showNguon(pl, name)
            }
            addView(Ui.tv(this@KhoGameActivity, "Cách chơi và dịch", 16f, Ui.TEXT, true), Ui.lpm(this@KhoGameActivity, 14, 6))
            val pr = Ui.hbox(this@KhoGameActivity)
            val hs = HorizontalScrollView(this@KhoGameActivity).apply { isHorizontalScrollBarEnabled = false; addView(pr) }
            fun drawPlat(sel: Plat) {
                pr.removeAllViews()
                for (x in Plat.values()) {
                    val on = x == sel
                    pr.addView(TextView(this@KhoGameActivity).apply {
                        text = x.label; textSize = 13f; gravity = Gravity.CENTER
                        setTextColor(if (on) Color.parseColor("#2A1A08") else Ui.TEXT)
                        background = if (on) Ui.goldBg(this@KhoGameActivity, 16) else Ui.bg(this@KhoGameActivity, Ui.CARD2, 16, Ui.LINE, 1)
                        setPadding(Ui.dp(this@KhoGameActivity, 14), Ui.dp(this@KhoGameActivity, 6), Ui.dp(this@KhoGameActivity, 14), Ui.dp(this@KhoGameActivity, 6))
                        setOnClickListener { drawPlat(x); showApps(x) }
                    }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = Ui.dp(this@KhoGameActivity, 6) })
                }
            }
            drawPlat(start)
            addView(hs)
            addView(guide, Ui.lpm(this@KhoGameActivity, 8, 0))
            addView(appsBox)
            showApps(start)
            addView(Ui.tv(this@KhoGameActivity,
                "Thông tin nền tảng chỉ để tham khảo, có thể đã thay đổi. Game PS4 chạy được trên PS5 và game Xbox One chạy được trên Xbox Series nhờ tương thích ngược.", 11.5f, Ui.SUB), Ui.lpm(this@KhoGameActivity, 10, 0))
            // Bản Việt hoá (mod) của cộng đồng: chỉ liên kết ra trang gốc, theo nền tảng đang chọn ở trên
            addView(Ui.tv(this@KhoGameActivity, "Bản Việt hoá của cộng đồng", 16f, Ui.TEXT, true), Ui.lpm(this@KhoGameActivity, 14, 4))
            addView(Ui.tv(this@KhoGameActivity,
                "Các nhóm Việt hoá làm bản vá (mod) sửa chữ trong chính file game, cài vào game bạn đã có. Đó là việc khác với dịch phụ đề của app này; " +
                    "dùng được cả hai cùng lúc. App chỉ mở trang gốc của họ, không tải hộ hay chép lại nội dung.", 12f, Ui.SUB))
            addView(nguonBox, Ui.lpm(this@KhoGameActivity, 4, 0))
            addView(Ui.tv(this@KhoGameActivity, NguonVietHoa.SAFETY, 11.5f, Ui.SUB), Ui.lpm(this@KhoGameActivity, 8, 0))
            // Chọn
            addView(Ui.button(this@KhoGameActivity, "Chọn game này", true) { d.dismiss(); choose(name, false) }, Ui.lpm(this@KhoGameActivity, 16, 0))
            addView(Ui.button(this@KhoGameActivity, "Chọn và dịch ngay") { d.dismiss(); choose(name, true) }, Ui.lpm(this@KhoGameActivity, 8, 0))
            addView(Ui.button(this@KhoGameActivity, "Sao chép tên game") {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("game", name))
                Toast.makeText(this@KhoGameActivity, "Đã sao chép: $name", Toast.LENGTH_SHORT).show()
            }, Ui.lpm(this@KhoGameActivity, 8, 0))
        }
    }

    private fun web(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {
            Toast.makeText(this, "Không mở được trình duyệt.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openApp(a: RemoteApp) {
        val li = packageManager.getLaunchIntentForPackage(a.pkg)
        if (li != null) { startActivity(li); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${a.pkg}"))) } catch (_: Exception) {
            web("https://play.google.com/store/apps/details?id=${a.pkg}")
        }
    }

    private fun choose(name: String, go: Boolean) {
        val g = genreOf(name)
        if (p.profiles.contains(name)) p.profile = name else p.addProfile(name)
        p.game = if (g.isEmpty()) name else "$name, $g"
        p.addPlayed(name)
        Toast.makeText(this, "Đã chọn $name. Gói Việt hoá của game đang được dùng.", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        if (go) startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("go", true))
        finish()
    }

    private fun custom() {
        val et = Ui.field(this, "Tên game bạn đang chơi", "")
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Game khác")
            .setView(box)
            .setPositiveButton("Chọn") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isEmpty()) Toast.makeText(this, "Hãy nhập tên game.", Toast.LENGTH_SHORT).show() else choose(n, false)
            }
            .setNegativeButton("Hủy", null).show()
    }
}
