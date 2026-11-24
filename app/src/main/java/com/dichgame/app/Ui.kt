package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Nền "kính": lớp trắng mờ chuyển màu từ trên xuống, viền sáng chéo và một vệt sáng mảnh ở mép trên.
 * Đặt trên nền tối có quầng sáng ([GlowBackground]) thì trông như tấm kính mờ.
 * [topOnly]: chỉ bo hai góc trên (bảng trượt từ đáy). [base]: lớp nền đặc vẽ dưới kính (cho hộp thoại cần che nội dung phía sau).
 */
class GlassDrawable(
    private val radius: Float,
    private val strong: Boolean = false,
    private val topOnly: Boolean = false,
    private val base: Int = 0,
    private val edge: Int = 0,
    private val tint: Int = 0,
    private val edgePx: Float = 1f,
) : Drawable() {
    private val baseP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()
    private val path = Path()
    private var sheen = false
    private var sx0 = 0f
    private var sx1 = 0f
    private var sy = 0f

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 1f || h <= 1f) { rect.setEmpty(); return }
        val sw = edgePx.coerceAtLeast(1f)
        edgeP.strokeWidth = sw
        sheenP.strokeWidth = sw
        val half = sw / 2f
        val extra = if (topOnly) radius * 2f + sw else 0f
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half + extra)
        val r = minOf(radius, minOf(rect.width(), rect.height()) / 2f)
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)

        if (base != 0) baseP.color = base
        val top = if (strong) 0xED2E2828.toInt() else 0xE7252226.toInt()
        val bottom = if (strong) 0xED151316.toInt() else 0xED0C0D11.toInt()
        fillP.shader = LinearGradient(0f, rect.top, 0f, minOf(rect.bottom, b.bottom.toFloat()), top, bottom, Shader.TileMode.CLAMP)
        if (tint != 0) tintP.color = tint
        if (edge != 0) {
            edgeP.shader = null
            edgeP.color = edge
        } else {
            edgeP.shader = LinearGradient(rect.left, rect.top, rect.right, minOf(rect.bottom, b.bottom.toFloat()), intArrayOf(0xFFFBE2A4.toInt(), 0xFF93602D.toInt(), 0xA86B4928.toInt(), 0xFFFBC66B.toInt()), floatArrayOf(0f, 0.26f, 0.68f, 1f), Shader.TileMode.CLAMP)
        }
        sheen = rect.width() > r * 3f && rect.height() > r * 1.1f
        if (sheen) {
            sx0 = rect.left + r * 0.9f
            sx1 = rect.right - r * 0.9f
            sy = rect.top + sw * 1.8f
            sheenP.shader = LinearGradient(sx0, 0f, sx1, 0f, intArrayOf(0x00FFCF73, 0xB0FFE6AA.toInt(), 0x00FFCF73), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
    }

    override fun draw(c: Canvas) {
        if (rect.isEmpty) return
        if (base != 0) c.drawPath(path, baseP)
        c.drawPath(path, fillP)
        if (tint != 0) c.drawPath(path, tintP)
        if (edge == 0) {
            // Inner luminous amber rim; no bitmap, blur or software rendering needed.
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = edgePx * 4f
                color = 0x25FFAF45
            }
            c.save()
            c.clipPath(path)
            c.drawPath(path, glow)
            glow.strokeWidth = edgePx * 2.2f
            glow.color = 0x43FFD17D
            c.drawPath(path, glow)
            c.restore()
        }
        if (sheen) c.drawLine(sx0, sy, sx1, sy, sheenP)
        c.drawPath(path, edgeP)
    }

    override fun setAlpha(alpha: Int) {
        baseP.alpha = alpha; fillP.alpha = alpha; tintP.alpha = alpha; edgeP.alpha = alpha; sheenP.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(cf: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Bộ dựng giao diện kính tối/vàng bằng View thuần, không cần thư viện ngoài. */
object Ui {
    val BG = Color.parseColor("#08080C")
    val CARD = Color.parseColor("#17151A")
    val CARD2 = Color.parseColor("#272125")
    val LINE = Color.parseColor("#55D6A65A")
    val TEXT = Color.WHITE
    val SUB = Color.parseColor("#D2CAC3")
    val GOLD = Color.parseColor("#FFD786")
    val GOLD2 = Color.parseColor("#E79A2F")
    val DANGER = Color.parseColor("#FF6B6B")
    val OK = Color.parseColor("#4CD964")

    fun dp(c: Context, n: Number): Int = (n.toFloat() * c.resources.displayMetrics.density).toInt()

    fun glass(c: Context, radius: Number = 26, strong: Boolean = false, edge: Int = 0, tint: Int = 0): GlassDrawable =
        GlassDrawable(dp(c, radius).toFloat(), strong, false, 0, edge, tint, dp(c, 1).toFloat())

    /**
     * Nền bo góc. Hai màu thẻ [CARD] và [CARD2] được vẽ thành kính nên mọi màn hình đổi kiểu cùng lúc;
     * màu khác vẫn là nền đặc như trước. [stroke] khác [LINE] sẽ thành màu viền của kính (ví dụ viền vàng).
     */
    fun bg(c: Context, color: Int, radius: Number = 16, stroke: Int = 0, strokeDp: Number = 1): Drawable {
        if (color == CARD || color == CARD2) {
            val edge = if (stroke == 0 || stroke == LINE) 0 else stroke
            val r = radius.toFloat() + if (color == CARD) 6f else 0f
            return glass(c, r.coerceAtLeast(12f), color == CARD2, edge)
        }
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radius).toFloat()
            if (stroke != 0) setStroke(dp(c, strokeDp).coerceAtLeast(1), stroke)
        }
    }

    fun goldBg(c: Context, radius: Number = 28): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFFFF1B7.toInt(), GOLD, 0xFFF5B14C.toInt(), GOLD2)).apply {
            cornerRadius = dp(c, radius).toFloat()
            setStroke(dp(c, 1).coerceAtLeast(1), Color.parseColor("#FFFFE4A4"))
        }

    /** Hiệu ứng gợn khi bấm, bo theo [radius] dp. */
    fun ripple(v: View, radius: Number = 20) {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(v.context, radius).toFloat()
        }
        v.foreground = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, mask)
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

    fun divider(c: Context): View = View(c).apply { setBackgroundColor(Color.parseColor("#26FFFFFF")) }

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
        ripple(this, 28)
        setOnClickListener { onClick() }
    }

    fun field(c: Context, hint: String, value: String, password: Boolean = false): android.widget.EditText = android.widget.EditText(c).apply {
        this.hint = hint
        setText(value)
        setTextColor(TEXT)
        setHintTextColor(Color.parseColor("#77FFFFFF"))
        textSize = 15f
        background = bg(c, CARD2, 16)
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

    /** Thanh trượt có biểu tượng hai đầu (ví dụ 🐢 … 🐇 cho tốc độ đọc). */
    fun seekIcons(c: Context, left: String, right: String, lo: Int, hi: Int, value: Int, onChange: (Int) -> Unit): View {
        val row = hbox(c)
        row.addView(tv(c, left, 20f))
        val sb = SeekBar(c)
        sb.max = hi - lo
        sb.progress = value - lo
        sb.progressTintList = ColorStateList.valueOf(GOLD)
        sb.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sb.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p + lo) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb, lp(0, WRAP_CONTENT, 1f))
        row.addView(tv(c, right, 20f))
        return row
    }

    fun pick(a: Activity, title: String, options: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(a, R.style.DgDialog)
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

    /** Thanh chọn một trong vài nút nằm ngang (kiểu "Giọng cơ bản | Giọng AI"). */
    fun segmented(a: Activity, labels: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val seg = hbox(a)
        seg.background = bg(a, CARD2, 22)
        val pad = dp(a, 3)
        seg.setPadding(pad, pad, pad, pad)
        labels.forEachIndexed { i, l ->
            seg.addView(TextView(a).apply {
                text = l; textSize = if (labels.size >= 3) 12.5f else 14f; gravity = Gravity.CENTER
                setTextColor(if (i == selected) Color.parseColor("#2A1A08") else TEXT)
                if (i == selected) { background = goldBg(a, 20); setTypeface(typeface, Typeface.BOLD) }
                val horizontalPad = if (labels.size >= 3) 5 else 10
                setPadding(dp(a, horizontalPad), dp(a, 10), dp(a, horizontalPad), dp(a, 10))
                maxLines = 1
                setOnClickListener { onPick(i) }
            }, lp(0, WRAP_CONTENT, 1f))
        }
        return seg
    }

    /** Ô biểu tượng vuông bo tròn, nền chuyển màu từ [c1] sang [c2], biểu tượng trắng ở giữa. */
    fun iconTile(c: Context, kind: ToolIcon.Kind, c1: Int, c2: Int, size: Number = 40): View {
        val box = FrameLayout(c)
        box.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(c, size.toFloat() * 0.30f).toFloat()
            setStroke(dp(c, 1).coerceAtLeast(1), Color.parseColor("#44FFFFFF"))
        }
        val s = dp(c, size.toFloat() * 0.56f)
        box.addView(ToolIcon(c, kind, Color.WHITE, c2), FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        return box
    }

    /** Hàng điều hướng: ô biểu tượng, tiêu đề, giá trị hiện tại bên dưới và mũi tên ›. */
    fun navRow(c: Context, tile: View?, title: String, value: String?, onClick: () -> Unit): View {
        val row = hbox(c)
        row.setPadding(dp(c, 14), dp(c, 12), dp(c, 12), dp(c, 12))
        if (tile != null) row.addView(tile, lp(dp(c, 40), dp(c, 40)))
        val col = vbox(c)
        col.addView(tv(c, title, 16f, TEXT, true).apply { maxLines = 1 })
        if (!value.isNullOrEmpty()) col.addView(tv(c, value, 13f, SUB).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(col, lp(0, WRAP_CONTENT, 1f).apply { leftMargin = if (tile != null) dp(c, 12) else 0 })
        row.addView(tv(c, "›", 24f, SUB))
        row.isClickable = true
        row.isFocusable = true
        ripple(row, 22)
        row.setOnClickListener { onClick() }
        return row
    }

    /** Nút tròn bằng kính với biểu tượng (nút quay lại, nút đóng). */
    fun circleBtn(c: Context, kind: ToolIcon.Kind, size: Number = 44, onClick: () -> Unit): View {
        val f = FrameLayout(c)
        f.background = glass(c, size, true)
        val s = dp(c, 22)
        f.addView(ToolIcon(c, kind, TEXT), FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        f.isClickable = true
        f.isFocusable = true
        ripple(f, size)
        f.setOnClickListener { onClick() }
        return f
    }

    /** Bảng trượt từ dưới lên (bottom sheet) bằng kính; từ Android 12 làm mờ cả màn hình phía sau. */
    fun sheet(a: Activity, build: LinearLayout.(Dialog) -> Unit) {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = vbox(a)
        box.background = GlassDrawable(dp(a, 28).toFloat(), true, true, Color.parseColor("#F0131115"), 0, 0, dp(a, 1).toFloat())
        val p = dp(a, 20)
        box.setPadding(p, dp(a, 10), p, p * 2)
        box.addView(View(a).apply { background = bg(a, Color.parseColor("#55FFFFFF"), 3) },
            lp(dp(a, 40), dp(a, 5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(a, 14) })
        box.build(d)
        val sv = ScrollView(a)
        sv.addView(box)
        d.setContentView(sv)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(MATCH_PARENT, WRAP_CONTENT)
            setDimAmount(0.55f)
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val lp = attributes
                    lp.blurBehindRadius = 36
                    attributes = lp
                } catch (_: Throwable) {
                }
            }
        }
        d.show()
    }
}

fun LinearLayout.put(v: View, top: Number = 0, bottom: Number = 0): View {
    addView(v, Ui.lpm(context, top, bottom))
    return v
}

/** Nhóm: dòng tiêu đề nhỏ phía trên rồi một thẻ kính chứa nội dung [build]. */
fun LinearLayout.group(title: String?, pad: Number = 16, top: Number = 14, build: LinearLayout.() -> Unit): LinearLayout {
    if (!title.isNullOrEmpty()) {
        addView(Ui.tv(context, title, 12.5f, Ui.SUB, true).apply { setPadding(Ui.dp(context, 10), 0, 0, 0) }, Ui.lpm(context, top, 6))
    }
    val card = Ui.card(context, pad)
    card.build()
    addView(card, Ui.lpm(context, if (title.isNullOrEmpty()) top else 0, 0))
    return card
}

/** Đường kẻ mảnh giữa các hàng trong thẻ; [start] dp chừa lề trái cho ô biểu tượng. */
fun LinearLayout.divide(start: Number = 0) {
    addView(Ui.divider(context), Ui.lp(MATCH_PARENT, 1).apply { leftMargin = Ui.dp(context, start) })
}

/**
 * Màn hình: nền tối có quầng sáng, thanh trên có nút quay lại tròn (kính) bên trái và tiêu đề ở giữa
 * (bỏ trống tiêu đề và back=false thì không có thanh), nội dung cuộn được.
 */
fun Activity.screen(title: String, back: Boolean = true, build: LinearLayout.() -> Unit) {
    window.statusBarColor = Ui.BG
    window.navigationBarColor = Ui.BG
    val frame = FrameLayout(this)
    frame.addView(GlowBackground(this, true), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    val root = Ui.vbox(this)
    val d44 = Ui.dp(this, 44)
    val bar = FrameLayout(this)
    bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 6))
    bar.addView(Ui.tv(this, title, 18f, Ui.TEXT, true).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }, FrameLayout.LayoutParams(MATCH_PARENT, d44, Gravity.CENTER).apply {
        leftMargin = Ui.dp(this@screen, 58); rightMargin = Ui.dp(this@screen, 58)
    })
    if (back) bar.addView(Ui.circleBtn(this, ToolIcon.Kind.BACK) { finish() },
        FrameLayout.LayoutParams(d44, d44, Gravity.START or Gravity.CENTER_VERTICAL))
    val content = Ui.vbox(this)
    val p = Ui.dp(this, 16)
    content.setPadding(p, p / 2, p, p * 2)
    content.build()
    if (back || title.isNotEmpty()) root.addView(bar, Ui.lp(MATCH_PARENT, WRAP_CONTENT))
    val sv = ScrollView(this)
    sv.isVerticalScrollBarEnabled = false
    sv.addView(content)
    root.addView(sv, Ui.lp(MATCH_PARENT, 0, 1f))
    frame.addView(root, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    setContentView(frame)
}
