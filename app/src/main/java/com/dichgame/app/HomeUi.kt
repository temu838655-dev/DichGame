package com.dichgame.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** Nền màn hình chính: xanh đậm, quầng sáng xanh ở góc, vài đường vàng cong và vòng cung xanh quanh nút tay cầm. */
class GlowBackground(c: Context) : View(c) {
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
class DockWings(c: Context) : View(c) {
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
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(bgClear, (bg and 0x00FFFFFF) or 0xF2000000.toInt(), bg), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
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
        val lens = floatArrayOf(92f, 70f, 46f)
        val y0 = h * 0.43f
        for (side in intArrayOf(-1, 1)) {
            for (i in rows.indices) {
                val inner = cx + side * (52f + i * 6f) * d
                val outer = cx + side * (52f + i * 6f + lens[i]) * d
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
