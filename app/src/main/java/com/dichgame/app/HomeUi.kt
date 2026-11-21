package com.dichgame.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/** Nền màn hình chính: xanh đậm, quầng sáng xanh ở góc, vài đường vàng cong và vòng cung xanh quanh nút tay cầm. */
class GlowBackground(c: Context, private val calm: Boolean = false) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun streak(cv: Canvas, d: Float, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, color: Int, strong: Float) {
        path.reset()
        path.moveTo(x0, y0)
        path.quadTo(cx, cy, x1, y1)
        val clear = color and 0x00FFFFFF
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.shader = LinearGradient(x0, y0, x1, y1, intArrayOf(clear, color, clear), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        p.alpha = (255 * 0.16f * strong).toInt(); p.strokeWidth = 11f * d; cv.drawPath(path, p)
        p.alpha = (255 * 0.30f * strong).toInt(); p.strokeWidth = 5f * d; cv.drawPath(path, p)
        p.alpha = (255 * strong).toInt(); p.strokeWidth = 1.8f * d; cv.drawPath(path, p)
        p.alpha = 255
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        if (w <= 0f || h <= 0f) return

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.parseColor("#0C2A18"), Ui.BG, Ui.BG), floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)

        p.shader = RadialGradient(w * 0.98f, 0f, w * 0.85f, Color.parseColor("#4A2EE06B"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h * 0.5f, p)
        p.shader = RadialGradient(0f, h * 0.2f, w * 0.7f, Color.parseColor("#302EE06B"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h * 0.6f, p)

        if (calm) { p.shader = null; return }
        val gold = Color.parseColor("#FFD54A")
        streak(cv, d, w * 0.52f, 0f, w * 0.98f, h * 0.02f, w * 1.04f, h * 0.17f, gold, 0.75f)
        streak(cv, d, -0.04f * w, h * 0.19f, w * 0.10f, h * 0.10f, w * 0.36f, h * 0.10f, Color.parseColor("#9CFF6B"), 0.5f)

        p.shader = null
    }
}

/** Hình tay cầm vẽ bằng nét, dùng làm biểu tượng trong nút (không phụ thuộc phông emoji). */
class GamepadGlyph(c: Context, private val body: Int, private val detail: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        p.style = Paint.Style.FILL
        p.color = body
        r.set(0f, h * 0.10f, w, h * 0.74f)
        cv.drawRoundRect(r, h * 0.30f, h * 0.30f, p)
        cv.drawCircle(w * 0.19f, h * 0.68f, h * 0.30f, p)
        cv.drawCircle(w * 0.81f, h * 0.68f, h * 0.30f, p)
        p.color = detail
        val cx = w * 0.27f
        val cy = h * 0.41f
        val a = h * 0.17f
        val t = h * 0.075f
        r.set(cx - a, cy - t, cx + a, cy + t); cv.drawRoundRect(r, t, t, p)
        r.set(cx - t, cy - a, cx + t, cy + a); cv.drawRoundRect(r, t, t, p)
        cv.drawCircle(w * 0.68f, h * 0.30f, h * 0.07f, p)
        cv.drawCircle(w * 0.80f, h * 0.43f, h * 0.07f, p)
        cv.drawCircle(w * 0.68f, h * 0.56f, h * 0.07f, p)
        cv.drawCircle(w * 0.56f, h * 0.43f, h * 0.07f, p)
    }
}

/** Đáy màn hình chính: phủ mờ nội dung phía dưới, vòng cung xanh và cánh vàng hai bên nút tay cầm. */
class DockWings(c: Context, private val gapDp: Float = 52f, private val lenScale: Float = 1f) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val gold = Color.parseColor("#FFD54A")
        val gold2 = Color.parseColor("#F2A900")
        // lớp phủ mờ để chữ của thẻ cuộn bên dưới không đè lên nút
        val bg = Ui.BG
        val bgClear = bg and 0x00FFFFFF
        p.style = Paint.Style.FILL
        // đặc dần từ trên xuống: tới khoảng 1/3 chiều cao là che hẳn chữ phía sau, logo nằm trên nền đặc
        p.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(bgClear, bgClear or 0xE6000000.toInt(), bgClear or 0xFF000000.toInt(), bgClear or 0xFF000000.toInt()),
            floatArrayOf(0f, 0.17f, 0.32f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        // vòng cung xanh ôm quanh nút
        path.reset()
        path.moveTo(-0.15f * w, h)
        path.quadTo(cx, 12f * d - h, 1.15f * w, h)
        p.shader = LinearGradient(0f, 6f * d, 0f, h, Color.parseColor("#402EE06B"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(Color.parseColor("#002EE06B"), Color.parseColor("#FF4CF07B"), Color.parseColor("#002EE06B")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        p.strokeWidth = 12f * d; p.alpha = 40; cv.drawPath(path, p)
        p.strokeWidth = 2.2f * d; p.alpha = 255; cv.drawPath(path, p)
        p.style = Paint.Style.FILL
        // ba thanh vàng mỗi bên, ngắn dần ra xa nút, như cánh
        val rows = floatArrayOf(0f, 9f, 18f)
        val lens = floatArrayOf(92f * lenScale, 70f * lenScale, 46f * lenScale)
        val y0 = h * (if (gapDp > 60f) 0.385f else 0.43f)
        for (side in intArrayOf(-1, 1)) {
            for (i in rows.indices) {
                val inner = cx + side * (gapDp + i * 6f) * d
                val outer = cx + side * (gapDp + i * 6f + lens[i]) * d
                val l = minOf(inner, outer)
                val rr = maxOf(inner, outer)
                p.shader = LinearGradient(inner, 0f, outer, 0f, gold, gold2, Shader.TileMode.CLAMP)
                p.alpha = (255 * (1f - i * 0.22f)).toInt()
                val y = y0 + rows[i] * d
                r.set(l, y, rr, y + 5f * d)
                cv.drawRoundRect(r, 3f * d, 3f * d, p)
            }
        }
        p.shader = null
        p.alpha = 255
    }
}

/**
 * Logo "Xin chào các anh em" ở giữa đáy màn hình chính: huy hiệu kính tối viền vàng có vương miện, chữ vàng có viền và bóng đổ,
 * ánh sáng chạy qua chữ mỗi vài giây, quầng sáng thở nhẹ. Đang dịch thì viền và quầng chuyển xanh lá.
 * Chỉ để trang trí: không có nút nào, chạm vào không làm gì.
 */
class HelloLogo(c: Context, private val running: Boolean) : View(c) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; strokeJoin = Paint.Join.ROUND }
    private val pill = RectF()
    private val r = RectF()
    private val path = Path()
    private var phase = 0.7f
    private var anim: ValueAnimator? = null
    private val titleFace: Typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
    private val subFace: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

    private val gold1 = Color.parseColor("#FFF3B0")
    private val gold2 = Color.parseColor("#FFD54A")
    private val gold3 = Color.parseColor("#F2A900")
    private val gold4 = Color.parseColor("#B97800")
    private val green1 = Color.parseColor("#B8FFD0")
    private val green2 = Color.parseColor("#2EE06B")
    private val green3 = Color.parseColor("#0E8A3A")
    private val brown = Color.parseColor("#3A2200")

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    private fun alpha(a: Int, c: Int) = (a.coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val rim1 = if (running) green1 else gold1
        val rim2 = if (running) green2 else gold2
        val rim3 = if (running) green3 else gold3
        pill.set(10f * d, 17f * d, w - 10f * d, h - 10f * d)
        val rad = 22f * d
        val pulse = 0.75f + 0.25f * sin(phase * 2f * PI.toFloat())

        // quầng sáng quanh huy hiệu
        p.style = Paint.Style.STROKE
        p.shader = null
        for (i in 1..5) {
            p.strokeWidth = (3f + i * 3.2f) * d
            p.color = alpha((70f * pulse / (i * 0.9f + 0.4f)).toInt(), rim2)
            cv.drawRoundRect(pill, rad, rad, p)
        }
        // nền kính tối ám vàng
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, pill.top, 0f, pill.bottom,
            intArrayOf(Color.parseColor("#3D2B08"), Color.parseColor("#1B1307"), Color.parseColor("#0C0903")), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(pill, rad, rad, p)
        // ánh kính nửa trên
        p.shader = LinearGradient(0f, pill.top, 0f, pill.centerY(), 0x3CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        r.set(pill.left + 3f * d, pill.top + 3f * d, pill.right - 3f * d, pill.centerY())
        cv.drawRoundRect(r, rad - 3f * d, rad - 3f * d, p)
        // viền chính (chuyển màu chéo) và viền trong mảnh
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.6f * d
        p.shader = LinearGradient(pill.left, pill.top, pill.right, pill.bottom,
            intArrayOf(rim1, rim2, rim3, rim2, rim1), floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(pill, rad, rad, p)
        p.shader = null
        p.strokeWidth = 1f * d
        p.color = alpha(70, rim1)
        r.set(pill.left + 4f * d, pill.top + 4f * d, pill.right - 4f * d, pill.bottom - 4f * d)
        cv.drawRoundRect(r, rad - 4f * d, rad - 4f * d, p)

        // dòng lớn "Xin chào"
        val s1 = "Xin chào"
        tp.typeface = titleFace
        tp.letterSpacing = 0f
        var sz = 31f * d
        tp.textSize = sz
        val maxW = pill.width() - 30f * d
        val tw = tp.measureText(s1)
        if (tw > maxW) { sz *= maxW / tw; tp.textSize = sz }
        val base1 = pill.top + pill.height() * 0.57f
        tp.style = Paint.Style.FILL
        tp.shader = null
        tp.color = 0xAA000000.toInt()
        cv.drawText(s1, cx, base1 + 2.2f * d, tp)
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = 4.2f * d
        tp.color = 0xFF000000.toInt() or brown
        cv.drawText(s1, cx, base1, tp)
        tp.style = Paint.Style.FILL
        tp.shader = LinearGradient(0f, base1 - sz * 0.78f, 0f, base1 + 0.05f * sz,
            intArrayOf(Color.parseColor("#FFFBD6"), gold2, Color.parseColor("#F59A00")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawText(s1, cx, base1, tp)
        // ánh sáng quét qua chữ (chỉ nửa đầu chu kỳ, nghỉ nửa sau)
        if (phase < 0.45f) {
            val t = phase / 0.45f
            val x = pill.left - 20f * d + t * (pill.width() + 40f * d)
            tp.shader = LinearGradient(x - 22f * d, base1 - sz * 0.8f, x + 22f * d, base1 + 0.1f * sz,
                intArrayOf(0x00FFFFFF, 0xDDFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            cv.drawText(s1, cx, base1, tp)
        }
        tp.shader = null

        // dòng nhỏ "CÁC ANH EM" giữa hai gạch vàng
        val s2 = "CÁC ANH EM"
        tp.typeface = subFace
        tp.textSize = 11.5f * d
        tp.letterSpacing = 0.14f
        tp.style = Paint.Style.FILL
        tp.color = Color.parseColor("#FFF1C2")
        val base2 = pill.bottom - 13f * d
        cv.drawText(s2, cx, base2, tp)
        val tw2 = tp.measureText(s2)
        val ly = base2 - 4f * d
        p.style = Paint.Style.FILL
        p.color = rim2
        for (side in intArrayOf(-1, 1)) {
            val near = cx + side * (tw2 / 2f + 6f * d)
            val far = near + side * 12f * d
            r.set(minOf(near, far), ly - 0.8f * d, maxOf(near, far), ly + 0.8f * d)
            cv.drawRoundRect(r, 0.8f * d, 0.8f * d, p)
            cv.drawCircle(far + side * 3f * d, ly, 1.8f * d, p)
        }

        // vương miện đặt lên viền trên
        val cw = 40f * d
        val ch = 24f * d
        val cl = cx - cw / 2f
        val ct = 1f * d
        path.reset()
        path.moveTo(cl + 0.10f * cw, ct + ch)
        path.lineTo(cl, ct + 0.22f * ch)
        path.lineTo(cl + 0.27f * cw, ct + 0.58f * ch)
        path.lineTo(cl + 0.5f * cw, ct)
        path.lineTo(cl + 0.73f * cw, ct + 0.58f * ch)
        path.lineTo(cl + cw, ct + 0.22f * ch)
        path.lineTo(cl + 0.90f * cw, ct + ch)
        path.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, ct, 0f, ct + ch, intArrayOf(gold1, gold2, gold3), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        cv.drawPath(path, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f * d
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF000000.toInt() or brown
        cv.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = 0x99FFFFFF.toInt()
        r.set(cl + 0.14f * cw, ct + 0.80f * ch, cl + 0.86f * cw, ct + 0.80f * ch + 1.2f * d)
        cv.drawRoundRect(r, 0.6f * d, 0.6f * d, p)
        p.color = Color.parseColor("#FFFBE3")
        cv.drawCircle(cl, ct + 0.22f * ch, 2.4f * d, p)
        cv.drawCircle(cl + 0.5f * cw, ct, 2.6f * d, p)
        cv.drawCircle(cl + cw, ct + 0.22f * ch, 2.4f * d, p)
    }
}
