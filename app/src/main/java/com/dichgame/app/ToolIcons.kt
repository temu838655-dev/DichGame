package com.dichgame.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Biểu tượng vẽ bằng nét cho thanh công cụ dịch video trên web (không phụ thuộc phông emoji).
 * [color] là màu nét, [knock] là màu nền để khoét chữ trên biểu tượng dịch.
 */
class ToolIcon(c: Context, var kind: Kind, var color: Int, private val knock: Int = 0) : View(c) {
    enum class Kind { TRANSLATE, SCAN, CC, CROP, TEXT_SIZE, SPEAKER_ON, SPEAKER_OFF, PAUSE, PLAY, SEARCH, GEAR }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    fun set(k: Kind, col: Int) { kind = k; color = col; invalidate() }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = minOf(w, h)
        val ox = (w - s) / 2f
        val oy = (h - s) / 2f
        cv.save()
        cv.translate(ox, oy)
        val d = resources.displayMetrics.density
        val sw = maxOf(1.6f * d, s * 0.075f)
        p.color = color
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE
        p.typeface = Typeface.DEFAULT_BOLD
        when (kind) {
            Kind.TRANSLATE -> {
                p.style = Paint.Style.FILL
                r.set(s * 0.36f, s * 0.36f, s * 0.98f, s * 0.96f)
                cv.drawRoundRect(r, s * 0.12f, s * 0.12f, p)
                p.color = knock
                p.textAlign = Paint.Align.CENTER
                p.textSize = s * 0.40f
                cv.drawText("文", s * 0.67f, s * 0.80f, p)
                p.color = color
                r.set(s * 0.02f, s * 0.04f, s * 0.64f, s * 0.64f)
                cv.drawRoundRect(r, s * 0.12f, s * 0.12f, p)
                p.color = knock
                p.style = Paint.Style.STROKE
                p.strokeWidth = sw * 0.9f
                cv.drawRoundRect(r, s * 0.12f, s * 0.12f, p)
                p.style = Paint.Style.FILL
                p.textSize = s * 0.46f
                cv.drawText("A", s * 0.33f, s * 0.50f, p)
            }
            Kind.SCAN -> {
                val a = s * 0.10f
                val b = s * 0.90f
                val q = s * 0.26f
                path.reset()
                path.moveTo(a, a + q); path.lineTo(a, a); path.lineTo(a + q, a)
                path.moveTo(b - q, a); path.lineTo(b, a); path.lineTo(b, a + q)
                path.moveTo(b, b - q); path.lineTo(b, b); path.lineTo(b - q, b)
                path.moveTo(a + q, b); path.lineTo(a, b); path.lineTo(a, b - q)
                cv.drawPath(path, p)
                cv.drawLine(s * 0.28f, s * 0.36f, s * 0.72f, s * 0.36f, p)
                cv.drawLine(s * 0.28f, s * 0.50f, s * 0.72f, s * 0.50f, p)
                cv.drawLine(s * 0.28f, s * 0.64f, s * 0.56f, s * 0.64f, p)
            }
            Kind.CC -> {
                r.set(s * 0.06f, s * 0.18f, s * 0.94f, s * 0.82f)
                cv.drawRoundRect(r, s * 0.14f, s * 0.14f, p)
                p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER
                p.textSize = s * 0.36f
                cv.drawText("CC", s * 0.5f, s * 0.63f, p)
            }
            Kind.CROP -> {
                path.reset()
                path.moveTo(s * 0.28f, s * 0.04f); path.lineTo(s * 0.28f, s * 0.72f); path.lineTo(s * 0.96f, s * 0.72f)
                path.moveTo(s * 0.04f, s * 0.28f); path.lineTo(s * 0.72f, s * 0.28f); path.lineTo(s * 0.72f, s * 0.96f)
                cv.drawPath(path, p)
            }
            Kind.TEXT_SIZE -> {
                p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER
                p.textSize = s * 0.52f
                cv.drawText("A", s * 0.22f, s * 0.84f, p)
                p.textSize = s * 0.86f
                cv.drawText("A", s * 0.66f, s * 0.84f, p)
            }
            Kind.SPEAKER_ON, Kind.SPEAKER_OFF -> {
                p.style = Paint.Style.STROKE
                path.reset()
                path.moveTo(s * 0.08f, s * 0.38f); path.lineTo(s * 0.26f, s * 0.38f); path.lineTo(s * 0.50f, s * 0.16f)
                path.lineTo(s * 0.50f, s * 0.84f); path.lineTo(s * 0.26f, s * 0.62f); path.lineTo(s * 0.08f, s * 0.62f); path.close()
                cv.drawPath(path, p)
                if (kind == Kind.SPEAKER_ON) {
                    r.set(s * 0.46f, s * 0.32f, s * 0.78f, s * 0.68f); cv.drawArc(r, -55f, 110f, false, p)
                    r.set(s * 0.42f, s * 0.14f, s * 0.98f, s * 0.86f); cv.drawArc(r, -55f, 110f, false, p)
                } else {
                    cv.drawLine(s * 0.64f, s * 0.38f, s * 0.92f, s * 0.62f, p)
                    cv.drawLine(s * 0.92f, s * 0.38f, s * 0.64f, s * 0.62f, p)
                }
            }
            Kind.PAUSE -> {
                p.style = Paint.Style.FILL
                r.set(s * 0.22f, s * 0.16f, s * 0.42f, s * 0.84f); cv.drawRoundRect(r, s * 0.05f, s * 0.05f, p)
                r.set(s * 0.58f, s * 0.16f, s * 0.78f, s * 0.84f); cv.drawRoundRect(r, s * 0.05f, s * 0.05f, p)
            }
            Kind.PLAY -> {
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(s * 0.26f, s * 0.14f); path.lineTo(s * 0.86f, s * 0.50f); path.lineTo(s * 0.26f, s * 0.86f); path.close()
                cv.drawPath(path, p)
            }
            Kind.SEARCH -> {
                cv.drawCircle(s * 0.42f, s * 0.42f, s * 0.28f, p)
                cv.drawLine(s * 0.63f, s * 0.63f, s * 0.90f, s * 0.90f, p)
            }
            Kind.GEAR -> {
                cv.drawCircle(s * 0.5f, s * 0.5f, s * 0.17f, p)
                for (i in 0 until 8) {
                    cv.save()
                    cv.rotate(i * 45f, s * 0.5f, s * 0.5f)
                    cv.drawLine(s * 0.5f, s * 0.04f, s * 0.5f, s * 0.20f, p)
                    cv.restore()
                }
                cv.drawCircle(s * 0.5f, s * 0.5f, s * 0.31f, p)
            }
        }
        cv.restore()
    }
}
