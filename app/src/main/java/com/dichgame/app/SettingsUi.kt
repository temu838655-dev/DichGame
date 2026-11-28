package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Các hàng dùng chung cho trang Cài đặt và các trang con: hàng chọn, hàng giá trị, hàng chữ, hộp nhập. */
object SUi {
    /** Hai mũi tên nhỏ chồng nhau báo hàng bấm để chọn trong danh sách. */
    class UpDown(c: Context, private val tint: Int) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(cv: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.color = tint
            paint.strokeWidth = w * 0.13f
            val cx = w / 2f
            val a = w * 0.30f
            val path = Path()
            path.moveTo(cx - a, h * 0.40f); path.lineTo(cx, h * 0.22f); path.lineTo(cx + a, h * 0.40f)
            path.moveTo(cx - a, h * 0.60f); path.lineTo(cx, h * 0.78f); path.lineTo(cx + a, h * 0.60f)
            cv.drawPath(path, paint)
        }
    }

    private fun dp(c: Context, n: Number) = Ui.dp(c, n)

    /** Tiêu đề nhóm (chữ to, xám) đặt phía trên một thẻ. */
    fun heading(c: LinearLayout, t: String, top: Number = 22) {
        c.addView(Ui.tv(c.context, t, 17f, Ui.SUB, true).apply { setPadding(dp(c.context, 8), 0, 0, 0) }, Ui.lpm(c.context, top, 8))
    }

    /** Chú thích nhỏ dưới thẻ. */
    fun note(c: LinearLayout, t: String, top: Number = 8) {
        c.addView(Ui.tv(c.context, t, 13f, Ui.SUB).apply {
            setPadding(dp(c.context, 8), 0, dp(c.context, 8), 0)
            setLineSpacing(0f, 1.1f)
        }, Ui.lpm(c.context, top, 0))
    }

    /** Đệm hàng công tắc / thanh kéo cho vừa lề trong thẻ không có đệm. */
    fun padded(v: View, top: Number = 12, bottom: Number = 12): View {
        val c = v.context
        v.setPadding(dp(c, 16), dp(c, top), dp(c, 16), dp(c, bottom))
        return v
    }

    private fun tappable(v: View, onClick: () -> Unit) {
        v.isClickable = true
        v.isFocusable = true
        Ui.ripple(v, 22)
        v.setOnClickListener { onClick() }
    }

    /** "Nhãn ... giá trị ⌃⌄": bấm để chọn trong danh sách. */
    fun choiceRow(a: Activity, title: String, current: () -> String, options: () -> List<String>, onPick: (Int) -> Unit): View {
        val row = Ui.hbox(a)
        row.setPadding(dp(a, 16), dp(a, 14), dp(a, 12), dp(a, 14))
        row.addView(Ui.tv(a, title, 16f, Ui.TEXT, true))
        val value = Ui.tv(a, current(), 16f, Ui.GOLD).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }
        row.addView(value, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(a, 16) })
        row.addView(UpDown(a, Ui.GOLD), Ui.lp(dp(a, 18), dp(a, 24)).apply { leftMargin = dp(a, 8) })
        tappable(row) { Ui.pick(a, title, options()) { i -> onPick(i); value.text = current() } }
        return row
    }

    /** "Nhãn ... giá trị ›": bấm để sửa giá trị hoặc mở trang con. [tile] là ô biểu tượng bên trái (có thể không có). */
    fun valueRow(a: Activity, tile: View?, title: String, value: String, placeholder: Boolean = false, onClick: () -> Unit): View {
        val row = Ui.hbox(a)
        row.setPadding(dp(a, if (tile != null) 14 else 16), dp(a, if (tile != null) 12 else 14), dp(a, 12), dp(a, if (tile != null) 12 else 14))
        if (tile != null) row.addView(tile, Ui.lp(dp(a, 42), dp(a, 42)).apply { rightMargin = dp(a, 12) })
        row.addView(Ui.tv(a, title, 16f, Ui.TEXT, true).apply { maxLines = 1 })
        row.addView(Ui.tv(a, value, 15f, if (placeholder) Ui.SUB else Ui.GOLD).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            gravity = Gravity.END
        }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(a, 16) })
        row.addView(Ui.tv(a, "›", 22f, Ui.SUB).apply { setPadding(dp(a, 8), 0, 0, 0) })
        tappable(row, onClick)
        return row
    }

    /** Hàng chỉ có một dòng chữ màu (vàng: hành động, đỏ: xóa). */
    fun textRow(a: Activity, text: String, color: Int = Ui.GOLD, onClick: () -> Unit): View {
        val row = Ui.hbox(a)
        row.setPadding(dp(a, 16), dp(a, 15), dp(a, 16), dp(a, 15))
        row.addView(Ui.tv(a, text, 16f, color, true), Ui.lp(0, WRAP_CONTENT, 1f))
        tappable(row, onClick)
        return row
    }

    /** Hộp nhập một giá trị; [reset] có thì hiện thêm nút "Mặc định". */
    fun fieldDialog(
        a: Activity, title: String, hint: String, value: String, secret: Boolean = false,
        reset: (() -> Unit)? = null, ok: String = "Lưu", message: String? = null, onSave: (String) -> Unit,
    ) {
        val edit = Ui.field(a, hint, value, secret)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 20), dp(a, 8), dp(a, 20), 0)
            if (message != null) addView(Ui.tv(a, message, 12.5f, Ui.SUB), Ui.lpm(a, 0, 8))
            addView(edit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val b = AlertDialog.Builder(a, R.style.DgDialog)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("Hủy", null)
            .setPositiveButton(ok) { _, _ -> onSave(edit.text.toString().trim()) }
        if (reset != null) b.setNeutralButton("Mặc định") { _, _ -> reset() }
        val d = b.create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }

    /** Hàng menu có ô biểu tượng: tiêu đề bên trái, giá trị xám bên phải rồi mũi tên ›. */
    fun hubRow(a: Activity, tile: View, title: String, value: String?, onClick: () -> Unit): View {
        val row = Ui.hbox(a)
        row.setPadding(dp(a, 14), dp(a, 12), dp(a, 12), dp(a, 12))
        row.addView(tile, Ui.lp(dp(a, 42), dp(a, 42)).apply { rightMargin = dp(a, 14) })
        row.addView(Ui.tv(a, title, 17f, Ui.TEXT).apply { maxLines = 1 })
        row.addView(Ui.tv(a, value.orEmpty(), 15f, Ui.SUB).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(a, 12) })
        row.addView(Ui.tv(a, "›", 24f, Ui.SUB).apply { setPadding(dp(a, 6), 0, 0, 0) })
        tappable(row, onClick)
        return row
    }

    /** Hàng công tắc có ô biểu tượng bên trái. */
    fun tileSwitchRow(a: Activity, tile: View, title: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = Ui.hbox(a)
        row.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12))
        row.addView(tile, Ui.lp(dp(a, 42), dp(a, 42)).apply { rightMargin = dp(a, 14) })
        row.addView(Ui.tv(a, title, 17f, Ui.TEXT), Ui.lp(0, WRAP_CONTENT, 1f))
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        row.addView(Switch(a).apply {
            isChecked = checked
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.parseColor("#DDDDDD")))
            trackTintList = ColorStateList(states, intArrayOf(Ui.GOLD2, Color.parseColor("#55FFFFFF")))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        })
        return row
    }

    /** Hàng chỉ để xem: nhãn bên trái, giá trị bên phải, không bấm được. */
    fun infoRow(a: Activity, title: String, value: String, color: Int = Ui.SUB): View {
        val row = Ui.hbox(a)
        row.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14))
        row.addView(Ui.tv(a, title, 16f, Ui.TEXT, true).apply { maxLines = 1 })
        row.addView(Ui.tv(a, value, 15f, color).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(a, 16) })
        return row
    }

    /** Nút trắng bo tròn có biểu tượng bốn ô (vào Kho game). */
    fun whiteButton(a: Activity, text: String, onClick: () -> Unit): View {
        val row = Ui.hbox(a)
        row.gravity = Gravity.CENTER
        row.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F6F6F8"))
            cornerRadius = dp(a, 26).toFloat()
        }
        row.setPadding(dp(a, 20), dp(a, 13), dp(a, 20), dp(a, 13))
        row.addView(ToolIcon(a, ToolIcon.Kind.GRID, Color.parseColor("#1B1B1F")), Ui.lp(dp(a, 24), dp(a, 24)).apply { rightMargin = dp(a, 12) })
        row.addView(Ui.tv(a, text, 16f, Color.parseColor("#14141A"), true))
        tappable(row, onClick)
        return row
    }

    private fun seek(a: Activity, lo: Int, hi: Int, value: Int, onProgress: (Int, Boolean) -> Unit): SeekBar = SeekBar(a).apply {
        max = hi - lo
        progress = value - lo
        progressTintList = ColorStateList.valueOf(Ui.GOLD)
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = onProgress(p + lo, fromUser)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    /** "Nhãn ... giá trị" rồi thanh trượt có chữ A nhỏ ở đầu trái và chữ A lớn ở đầu phải (cỡ chữ phụ đề). */
    fun sizeSlider(a: Activity, title: String, label: (Int) -> String, lo: Int, hi: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = Ui.vbox(a)
        val head = Ui.hbox(a)
        val vt = Ui.tv(a, label(value), 15f, Ui.SUB)
        head.addView(Ui.tv(a, title, 16f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        head.addView(vt)
        val bar = Ui.hbox(a)
        bar.addView(Ui.tv(a, "A", 13f, Ui.SUB, true))
        bar.addView(seek(a, lo, hi, value) { v, user -> vt.text = label(v); if (user) onChange(v) }, Ui.lp(0, WRAP_CONTENT, 1f))
        bar.addView(Ui.tv(a, "A", 24f, Ui.SUB, true))
        box.addView(head)
        box.addView(bar, Ui.lpm(a, 8, 0))
        return box
    }

    /** "Nhãn ... giá trị" rồi thanh trượt có hai biểu tượng ở hai đầu (tốc độ đọc: rùa và thỏ). */
    fun iconSlider(a: Activity, title: String, label: (Int) -> String, left: String, right: String, lo: Int, hi: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = Ui.vbox(a)
        val head = Ui.hbox(a)
        val vt = Ui.tv(a, label(value), 15f, Ui.SUB)
        head.addView(Ui.tv(a, title, 16f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        head.addView(vt)
        val bar = Ui.hbox(a)
        bar.addView(Ui.tv(a, left, 20f))
        bar.addView(seek(a, lo, hi, value) { v, user -> vt.text = label(v); if (user) onChange(v) }, Ui.lp(0, WRAP_CONTENT, 1f))
        bar.addView(Ui.tv(a, right, 20f))
        box.addView(head)
        box.addView(bar, Ui.lpm(a, 8, 0))
        return box
    }

    /** Biểu ngữ đầu trang Cài đặt: logo app, tên, lời giới thiệu và mũi tên ›. */
    fun banner(a: Activity, onClick: () -> Unit): View {
        val row = Ui.hbox(a)
        row.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#2C1F09"), Color.parseColor("#130D05"))).apply {
            cornerRadius = dp(a, 26).toFloat()
            setStroke(dp(a, 2), Color.parseColor("#C9A24A"))
        }
        row.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12))
        row.addView(ImageView(a).apply {
            setImageResource(R.drawable.ic_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, Ui.lp(dp(a, 78), dp(a, 78)))
        val col = Ui.vbox(a)
        val title = Ui.tv(a, "DỊCH GAME", 27f, Ui.GOLD, true).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
            setShadowLayer(dp(a, 3).toFloat(), 0f, dp(a, 2).toFloat(), Color.parseColor("#AA000000"))
            maxLines = 1
        }
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, 0f, title.height.toFloat(),
                intArrayOf(Color.parseColor("#FFF3B0"), Color.parseColor("#FFD54A"), Color.parseColor("#E39A00")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            title.invalidate()
        }
        col.addView(title)
        col.addView(Ui.tv(a, "by Huy Đầyy", 18f, Color.parseColor("#F3D98A")).apply {
            typeface = Typeface.create("cursive", Typeface.ITALIC)
        })
        col.addView(Ui.tv(a, "Dịch game mọi lúc mọi nơi", 13.5f, Ui.TEXT).apply { setPadding(0, dp(a, 2), 0, 0) })
        row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(a, 14) })
        row.addView(Ui.tv(a, "›", 28f, Ui.TEXT))
        tappable(row, onClick)
        return row
    }

    private fun findScroll(v: View): ScrollView? {
        if (v is ScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findScroll(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Vị trí cuộn hiện tại của trang, để vẽ lại trang mà không bị nhảy về đầu. */
    fun scrollY(a: Activity): Int = findScroll(a.window.decorView)?.scrollY ?: 0

    fun restoreScroll(a: Activity, y: Int) {
        if (y <= 0) return
        val sv = findScroll(a.window.decorView) ?: return
        sv.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                sv.viewTreeObserver.removeOnPreDrawListener(this)
                sv.scrollTo(0, y)
                return true
            }
        })
    }

    fun toast(a: Activity, s: String) = Toast.makeText(a, s, Toast.LENGTH_SHORT).show()
}
