package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Bộ dựng giao diện tối/vàng bằng View thuần, không cần thư viện ngoài. */
object Ui {
    val BG = Color.parseColor("#111113")
    val CARD = Color.parseColor("#1E1E21")
    val CARD2 = Color.parseColor("#2A2A2E")
    val LINE = Color.parseColor("#33FFFFFF")
    val TEXT = Color.WHITE
    val SUB = Color.parseColor("#B4B4BA")
    val GOLD = Color.parseColor("#F4C38C")
    val GOLD2 = Color.parseColor("#E8995A")
    val DANGER = Color.parseColor("#FF6B6B")
    val OK = Color.parseColor("#6FD08C")

    fun dp(c: Context, n: Number): Int = (n.toFloat() * c.resources.displayMetrics.density).toInt()

    fun bg(c: Context, color: Int, radius: Number = 16, stroke: Int = 0, strokeDp: Number = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radius).toFloat()
            if (stroke != 0) setStroke(dp(c, strokeDp).coerceAtLeast(1), stroke)
        }

    fun goldBg(c: Context, radius: Number = 28): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(GOLD, GOLD2)).apply {
            cornerRadius = dp(c, radius).toFloat()
        }

    fun tv(c: Context, s: CharSequence, sp: Float = 15f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun vbox(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    fun hbox(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun lp(w: Int = MATCH_PARENT, h: Int = WRAP_CONTENT, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    fun lpm(c: Context, top: Number = 0, bottom: Number = 0, w: Int = MATCH_PARENT, h: Int = WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dp(c, top); bottomMargin = dp(c, bottom) }

    fun card(c: Context, pad: Number = 16): LinearLayout = vbox(c).apply {
        background = bg(c, CARD, 20, LINE, 1)
        val p = dp(c, pad)
        setPadding(p, p, p, p)
    }

    fun divider(c: Context): View = View(c).apply { setBackgroundColor(LINE) }

    fun section(c: Context, icon: String, title: String): View = hbox(c).apply {
        addView(tv(c, icon, 20f))
        addView(tv(c, title, 18f, TEXT, true).apply { setPadding(dp(c, 10), 0, 0, 0) })
        setPadding(0, 0, 0, dp(c, 8))
    }

    fun badge(c: Context, s: String, color: Int = GOLD): TextView = tv(c, s, 11f, Color.parseColor("#2A1A08"), true).apply {
        background = bg(c, color, 12)
        setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3))
    }

    fun button(c: Context, text: String, primary: Boolean = false, onClick: () -> Unit): TextView = TextView(c).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(if (primary) Color.parseColor("#2A1A08") else TEXT)
        setTypeface(typeface, Typeface.BOLD)
        background = if (primary) goldBg(c) else bg(c, CARD2, 28, LINE, 1)
        val v = dp(c, 14)
        setPadding(dp(c, 20), v, dp(c, 20), v)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun field(c: Context, hint: String, value: String, password: Boolean = false): android.widget.EditText = android.widget.EditText(c).apply {
        this.hint = hint
        setText(value)
        setTextColor(TEXT)
        setHintTextColor(Color.parseColor("#77FFFFFF"))
        textSize = 15f
        background = bg(c, CARD2, 14)
        val p = dp(c, 14)
        setPadding(p, p, p, p)
        isSingleLine = true
        inputType = if (password) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        else android.text.InputType.TYPE_CLASS_TEXT
    }

    private fun tint(on: Int, off: Int) =
        ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(on, off))

    fun switchRow(c: Context, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = hbox(c)
        val col = vbox(c)
        col.addView(tv(c, title, 15f, TEXT, true))
        if (sub != null) col.addView(tv(c, sub, 12.5f, SUB).apply { setPadding(0, dp(c, 2), 0, 0) })
        row.addView(col, lp(0, WRAP_CONTENT, 1f))
        val sw = Switch(c).apply {
            isChecked = checked
            thumbTintList = tint(Color.WHITE, Color.parseColor("#DDDDDD"))
            trackTintList = tint(GOLD2, Color.parseColor("#55FFFFFF"))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(sw, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(c, 12) })
        return row
    }

    fun seekRow(c: Context, title: String, label: (Int) -> String, lo: Int, hi: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = vbox(c)
        val head = hbox(c)
        val vt = tv(c, label(value), 14f, SUB)
        head.addView(tv(c, title, 15f, TEXT, true), lp(0, WRAP_CONTENT, 1f))
        head.addView(vt)
        val sb = SeekBar(c)
        sb.max = hi - lo
        sb.progress = value - lo
        sb.progressTintList = ColorStateList.valueOf(GOLD)
        sb.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sb.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                vt.text = label(p + lo)
                if (fromUser) onChange(p + lo)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(head)
        box.addView(sb, lpm(c, 6))
        return box
    }

    fun pick(a: Activity, title: String, options: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, i -> onPick(i) }
            .show()
    }

    /** Hàng "nhãn ... giá trị ▾", bấm vào để chọn trong danh sách. */
    fun dropRow(a: Activity, title: String, current: () -> String, options: () -> List<String>, onPick: (Int) -> Unit): View {
        val row = hbox(a)
        row.setPadding(0, dp(a, 10), 0, dp(a, 10))
        row.addView(tv(a, title, 15f, TEXT, true), lp(0, WRAP_CONTENT, 1f))
        val v = tv(a, current() + " ▾", 15f, GOLD)
        row.addView(v)
        row.setOnClickListener {
            pick(a, title, options()) { i -> onPick(i); v.text = current() + " ▾" }
        }
        return row
    }

    /** Bảng trượt từ dưới lên (bottom sheet). */
    fun sheet(a: Activity, build: LinearLayout.(Dialog) -> Unit) {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = vbox(a)
        box.background = GradientDrawable().apply {
            setColor(Color.parseColor("#17171A"))
            val r = dp(a, 24).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        val p = dp(a, 20)
        box.setPadding(p, p, p, p * 2)
        box.build(d)
        val sv = ScrollView(a)
        sv.addView(box)
        d.setContentView(sv)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(MATCH_PARENT, WRAP_CONTENT)
        }
        d.show()
    }
}

fun LinearLayout.put(v: View, top: Number = 0, bottom: Number = 0): View {
    addView(v, Ui.lpm(context, top, bottom))
    return v
}

/** Màn hình: thanh tiêu đề có nút quay lại (bỏ trống tiêu đề và back=false thì không có thanh), nội dung cuộn được. */
fun Activity.screen(title: String, back: Boolean = true, build: LinearLayout.() -> Unit) {
    window.statusBarColor = Ui.BG
    window.navigationBarColor = Ui.BG
    val root = Ui.vbox(this)
    root.setBackgroundColor(Ui.BG)
    val bar = Ui.hbox(this)
    bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 8))
    if (back) {
        val b = Ui.tv(this, "←", 26f)
        b.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 14), 0)
        b.setOnClickListener { finish() }
        bar.addView(b)
    } else bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 8))
    bar.addView(Ui.tv(this, title, 20f, Ui.TEXT, true))
    val content = Ui.vbox(this)
    val p = Ui.dp(this, 16)
    content.setPadding(p, p / 2, p, p * 2)
    content.build()
    if (back || title.isNotEmpty()) root.addView(bar)
    val sv = ScrollView(this)
    sv.addView(content)
    root.addView(sv, Ui.lp(MATCH_PARENT, 0, 1f))
    setContentView(root)
}
