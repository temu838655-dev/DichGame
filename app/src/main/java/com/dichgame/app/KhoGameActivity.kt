package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Kho game: chọn nhanh game đang chơi. Có ô tìm kiếm, tab "Đã chơi" / "Tất cả" và nút thêm game khác.
 * Chọn một game sẽ chuyển sang (hoặc tạo) hồ sơ riêng tên game đó, để mỗi game nhớ khung phụ đề riêng,
 * và ghi tên game cùng thể loại để bộ dịch AI dịch đúng giọng.
 */
class KhoGameActivity : Activity() {
    private lateinit var p: Prefs
    private var tab = 0
    private var query = ""
    private lateinit var tabs: LinearLayout
    private lateinit var grid: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        tab = if (p.played.isEmpty()) 1 else 0
        screen("Kho game") {
            put(Ui.tv(this@KhoGameActivity, "Chọn game để bộ dịch AI dịch đúng giọng và xưng hô. Mỗi game có hồ sơ riêng, nhớ khung phụ đề riêng.", 13f, Ui.SUB), 0, 10)
            val search = Ui.field(this@KhoGameActivity, "Tìm game", "")
            search.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty().trim(); fill() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
            put(search, 0, 10)
            tabs = Ui.hbox(this@KhoGameActivity)
            put(tabs, 0, 10)
            put(Ui.button(this@KhoGameActivity, "＋  Game khác") { custom() }, 0, 14)
            grid = Ui.vbox(this@KhoGameActivity)
            put(grid, 0, 0)
            drawTabs()
            fill()
        }
    }

    private fun drawTabs() {
        tabs.removeAllViews()
        tabs.addView(Ui.segmented(this, listOf("Đã chơi", "Tất cả"), tab) { tab = it; drawTabs(); fill() }, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun fill() {
        grid.removeAllViews()
        val q = query.lowercase()
        val base = if (tab == 0) p.played else Catalog.GAMES.map { it.first }
        val list = base.filter { q.isEmpty() || it.lowercase().contains(q) }
        if (list.isEmpty()) {
            grid.addView(Ui.tv(this,
                if (tab == 0 && q.isEmpty()) "Chưa có game nào. Chọn ở tab Tất cả, hoặc bấm Game khác để nhập tên game bạn đang chơi."
                else "Không thấy game này. Bấm Game khác để nhập tên.", 14f, Ui.SUB), Ui.lpm(this, 12, 0))
            return
        }
        val cur = p.game
        for (row in list.chunked(3)) {
            val r = Ui.hbox(this)
            for (k in 0 until 3) {
                val name = row.getOrNull(k)
                val cell: View = if (name == null) View(this) else cellOf(name, cur.startsWith(name))
                r.addView(cell, Ui.lp(0, WRAP_CONTENT, 1f).apply { if (k > 0) leftMargin = Ui.dp(this@KhoGameActivity, 10) })
            }
            grid.addView(r, Ui.lpm(this, 0, 14))
        }
    }

    private fun cellOf(name: String, selected: Boolean): View {
        val col = Ui.vbox(this)
        val hue = ((name.hashCode() % 360) + 360) % 360
        val tile = TextView(this).apply {
            text = initials(name)
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = Ui.bg(this@KhoGameActivity, Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.42f)), 18,
                if (selected) Ui.GOLD else 0, if (selected) 3 else 0)
        }
        col.addView(tile, Ui.lp(MATCH_PARENT, Ui.dp(this, 96)))
        col.addView(Ui.tv(this, name, 12.5f, if (selected) Ui.GOLD else Ui.TEXT).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, Ui.dp(this@KhoGameActivity, 6), 0, 0)
        })
        col.setOnClickListener { choose(name) }
        return col
    }

    private fun initials(name: String): String {
        val skip = setOf("the", "of", "a", "and", "&")
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it.lowercase() !in skip }
        val s = words.take(3).joinToString("") { it.take(1) }.uppercase()
        return s.ifEmpty { name.take(2).uppercase() }
    }

    private fun choose(name: String) {
        val g = Catalog.genreOf(name)
        if (p.profiles.contains(name)) p.profile = name else p.addProfile(name)
        p.game = if (g.isEmpty()) name else "$name, $g"
        p.addPlayed(name)
        Toast.makeText(this, "Đã chọn $name. Đang dùng hồ sơ \"$name\".", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
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
                if (n.isEmpty()) Toast.makeText(this, "Hãy nhập tên game.", Toast.LENGTH_SHORT).show() else choose(n)
            }
            .setNegativeButton("Hủy", null).show()
    }
}
