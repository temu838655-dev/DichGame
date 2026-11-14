package com.dichgame.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.metallic.chiaki.lib.ControllerState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Tay cầm ảo vẽ bằng Canvas: L2 L1 R1 R2, Create, bàn cảm ứng, Options, D-pad, bốn nút △ ○ ✕ □, nút PS và hai cần analog.
 * Nhiều ngón cùng lúc. Mỗi lần trạng thái đổi thì gọi [onState] với trạng thái đầy đủ.
 */
class PadView(context: Context, private val onState: (ControllerState) -> Unit) : View(context) {
    private val gold = Color.parseColor("#F2B84B")
    private val fill = Color.parseColor("#26201A")
    private val fillDown = Color.parseColor("#6B4F1D")
    private val back = Color.parseColor("#14100C")

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = gold; strokeWidth = 3f }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#66F2B84B"); strokeWidth = 2f }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val sym = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = gold; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    private class Box(val key: String, val r: RectF)
    private class Round(val key: String, var cx: Float, var cy: Float, var rad: Float)

    private val boxes = ArrayList<Box>()      // nút chữ nhật: L1 L2 R1 R2
    private val rounds = ArrayList<Round>()   // nút tròn: Create, bàn cảm ứng, Options, PS, bốn nút mặt
    private var dpadCx = 0f; private var dpadCy = 0f; private var dpadR = 0f
    private var lsCx = 0f; private var lsCy = 0f; private var lsR = 0f
    private var rsCx = 0f; private var rsCy = 0f; private var rsR = 0f
    private var face = 0f

    private val owner = HashMap<Int, String>()   // mã ngón tay -> tên điều khiển đang giữ
    private val pressed = HashSet<String>()
    private var dpadBits = 0u
    private var lx = 0f; private var ly = 0f; private var rx = 0f; private var ry = 0f
    private var last = ControllerState()

    private fun masks(key: String): UInt = when (key) {
        "L1" -> ControllerState.BUTTON_L1
        "R1" -> ControllerState.BUTTON_R1
        "SHARE" -> ControllerState.BUTTON_SHARE
        "TP" -> ControllerState.BUTTON_TOUCHPAD
        "OPT" -> ControllerState.BUTTON_OPTIONS
        "PS" -> ControllerState.BUTTON_PS
        "TRI" -> ControllerState.BUTTON_PYRAMID
        "SQU" -> ControllerState.BUTTON_BOX
        "CIR" -> ControllerState.BUTTON_MOON
        "CRO" -> ControllerState.BUTTON_CROSS
        else -> 0u
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        boxes.clear(); rounds.clear()
        if (w > h * 1.25f) layoutLandscape(w.toFloat(), h.toFloat()) else layoutPortrait(w.toFloat(), h.toFloat())
        stroke.strokeWidth = (min(w, h) / 200f).coerceAtLeast(2f)
        soft.strokeWidth = stroke.strokeWidth * 0.7f
        sym.strokeWidth = stroke.strokeWidth * 1.3f
        // Khi xoay màn hình/đổi kích thước, tọa độ điều khiển cũ không còn hợp lệ.
        if (ow > 0 && oh > 0 && (ow != w || oh != h)) releaseAll()
    }

    /** Dọc: cả bộ nằm trọn trong chiều rộng, đơn vị u = 1/100 chiều rộng. */
    private fun layoutPortrait(w: Float, h: Float) {
        val u = w / 100f
        boxes.add(Box("L2", RectF(2 * u, 2 * u, 19 * u, 13 * u)))
        boxes.add(Box("L1", RectF(20.5f * u, 2 * u, 37.5f * u, 13 * u)))
        boxes.add(Box("R1", RectF(62.5f * u, 2 * u, 79.5f * u, 13 * u)))
        boxes.add(Box("R2", RectF(81 * u, 2 * u, 98 * u, 13 * u)))
        rounds.add(Round("SHARE", 43 * u, 7.5f * u, 3.6f * u))
        rounds.add(Round("TP", 50 * u, 7.5f * u, 3.6f * u))
        rounds.add(Round("OPT", 57 * u, 7.5f * u, 3.6f * u))
        dpadCx = 23 * u; dpadCy = 36 * u; dpadR = 20 * u
        val fcx = 77 * u; val fcy = 36 * u
        face = 7.3f * u
        rounds.add(Round("TRI", fcx, fcy - 13.5f * u, face))
        rounds.add(Round("SQU", fcx - 13.5f * u, fcy, face))
        rounds.add(Round("CIR", fcx + 13.5f * u, fcy, face))
        rounds.add(Round("CRO", fcx, fcy + 13.5f * u, face))
        rounds.add(Round("PS", 50 * u, 52 * u, 7.5f * u))
        lsCx = 24 * u; lsCy = 80 * u; lsR = 16 * u
        rsCx = 76 * u; rsCy = 80 * u; rsR = 16 * u
    }

    /** Ngang: phủ lên hình game, hai cụm ở hai bên, đơn vị u = 1/100 chiều cao. */
    private fun layoutLandscape(w: Float, h: Float) {
        val u = h / 100f
        boxes.add(Box("L2", RectF(3 * u, 3 * u, 21 * u, 14 * u)))
        boxes.add(Box("L1", RectF(3 * u, 16 * u, 21 * u, 27 * u)))
        boxes.add(Box("R2", RectF(w - 21 * u, 3 * u, w - 3 * u, 14 * u)))
        boxes.add(Box("R1", RectF(w - 21 * u, 16 * u, w - 3 * u, 27 * u)))
        dpadCx = 17 * u; dpadCy = 46 * u; dpadR = 15 * u
        val fcx = w - 17 * u; val fcy = 46 * u
        face = 5.6f * u
        rounds.add(Round("TRI", fcx, fcy - 10 * u, face))
        rounds.add(Round("SQU", fcx - 10 * u, fcy, face))
        rounds.add(Round("CIR", fcx + 10 * u, fcy, face))
        rounds.add(Round("CRO", fcx, fcy + 10 * u, face))
        val cx = w / 2f
        rounds.add(Round("SHARE", cx - 9 * u, 92 * u, 3.4f * u))
        rounds.add(Round("TP", cx, 92 * u, 3.4f * u))
        rounds.add(Round("OPT", cx + 9 * u, 92 * u, 3.4f * u))
        rounds.add(Round("PS", cx, 76 * u, 6 * u))
        lsCx = 30 * u; lsCy = 80 * u; lsR = 13 * u
        rsCx = w - 30 * u; rsCy = 80 * u; rsR = 13 * u
    }

    // ---------------- Vẽ ----------------

    override fun onDraw(c: Canvas) {
        c.drawColor(if (width > height * 1.25f) Color.TRANSPARENT else back)
        for (b in boxes) {
            val on = b.key in pressed
            fillP.color = if (on) fillDown else fill
            val rr = b.r.height() / 3f
            c.drawRoundRect(b.r, rr, rr, fillP)
            c.drawRoundRect(b.r, rr, rr, stroke)
            text.textSize = b.r.height() * 0.5f
            c.drawText(b.key, b.r.centerX(), b.r.centerY() + text.textSize * 0.35f, text)
        }
        for (r in rounds) drawRound(c, r)
        drawDpad(c)
        drawStick(c, lsCx, lsCy, lsR, lx, ly, "LS" in pressed)
        drawStick(c, rsCx, rsCy, rsR, rx, ry, "RS" in pressed)
    }

    private fun drawRound(c: Canvas, r: Round) {
        val on = r.key in pressed
        fillP.color = if (r.key == "PS") (if (on) Color.parseColor("#FFD28A") else gold) else if (on) fillDown else fill
        c.drawCircle(r.cx, r.cy, r.rad, fillP)
        c.drawCircle(r.cx, r.cy, r.rad, if (r.key == "PS") soft else stroke)
        val s = r.rad * 0.42f
        when (r.key) {
            "TRI" -> { val p = Path(); p.moveTo(r.cx, r.cy - s * 1.1f); p.lineTo(r.cx + s * 1.05f, r.cy + s * 0.8f); p.lineTo(r.cx - s * 1.05f, r.cy + s * 0.8f); p.close(); c.drawPath(p, sym) }
            "SQU" -> c.drawRect(r.cx - s, r.cy - s, r.cx + s, r.cy + s, sym)
            "CIR" -> c.drawCircle(r.cx, r.cy, s * 1.1f, sym)
            "CRO" -> { c.drawLine(r.cx - s, r.cy - s, r.cx + s, r.cy + s, sym); c.drawLine(r.cx + s, r.cy - s, r.cx - s, r.cy + s, sym) }
            "PS" -> { text.color = Color.parseColor("#2A1A08"); text.textSize = r.rad * 0.95f; c.drawText("PS", r.cx, r.cy + text.textSize * 0.35f, text); text.color = gold }
            "SHARE" -> { c.drawRect(r.cx - s * 0.9f, r.cy - s * 0.5f, r.cx + s * 0.5f, r.cy + s * 0.9f, sym); c.drawRect(r.cx - s * 0.5f, r.cy - s * 0.9f, r.cx + s * 0.9f, r.cy + s * 0.5f, sym) }
            "TP" -> { for (i in -1..1) for (j in -1..1) c.drawCircle(r.cx + i * s * 0.75f, r.cy + j * s * 0.75f, s * 0.14f, sym) }
            "OPT" -> { for (j in -1..1) c.drawLine(r.cx - s, r.cy + j * s * 0.7f, r.cx + s, r.cy + j * s * 0.7f, sym) }
        }
    }

    private fun drawDpad(c: Canvas) {
        fillP.color = Color.parseColor("#1B1611")
        c.drawCircle(dpadCx, dpadCy, dpadR, fillP)
        c.drawCircle(dpadCx, dpadCy, dpadR, soft)
        val a = dpadR * 0.56f
        val half = dpadR * 0.32f
        val items = listOf(
            Triple(ControllerState.BUTTON_DPAD_UP, 0f, -a), Triple(ControllerState.BUTTON_DPAD_DOWN, 0f, a),
            Triple(ControllerState.BUTTON_DPAD_LEFT, -a, 0f), Triple(ControllerState.BUTTON_DPAD_RIGHT, a, 0f),
        )
        for ((m, dx, dy) in items) {
            val on = (dpadBits and m) != 0u
            fillP.color = if (on) fillDown else fill
            val rect = RectF(dpadCx + dx - half, dpadCy + dy - half, dpadCx + dx + half, dpadCy + dy + half)
            c.drawRoundRect(rect, half / 3f, half / 3f, fillP)
            c.drawRoundRect(rect, half / 3f, half / 3f, soft)
            val s = half * 0.45f
            val x = rect.centerX(); val y = rect.centerY()
            val p = Path()
            when (m) {
                ControllerState.BUTTON_DPAD_UP -> { p.moveTo(x - s, y + s * 0.5f); p.lineTo(x, y - s * 0.5f); p.lineTo(x + s, y + s * 0.5f) }
                ControllerState.BUTTON_DPAD_DOWN -> { p.moveTo(x - s, y - s * 0.5f); p.lineTo(x, y + s * 0.5f); p.lineTo(x + s, y - s * 0.5f) }
                ControllerState.BUTTON_DPAD_LEFT -> { p.moveTo(x + s * 0.5f, y - s); p.lineTo(x - s * 0.5f, y); p.lineTo(x + s * 0.5f, y + s) }
                else -> { p.moveTo(x - s * 0.5f, y - s); p.lineTo(x + s * 0.5f, y); p.lineTo(x - s * 0.5f, y + s) }
            }
            c.drawPath(p, sym)
        }
    }

    private fun drawStick(c: Canvas, cx: Float, cy: Float, r: Float, nx: Float, ny: Float, on: Boolean) {
        fillP.color = Color.parseColor("#1B1611")
        c.drawCircle(cx, cy, r, fillP)
        c.drawCircle(cx, cy, r, soft)
        fillP.color = if (on) Color.parseColor("#F7C766") else Color.parseColor("#2E251B")
        val kx = cx + nx * r * 0.7f
        val ky = cy + ny * r * 0.7f
        c.drawCircle(kx, ky, r * 0.5f, fillP)
        c.drawCircle(kx, ky, r * 0.5f, stroke)
    }

    // ---------------- Cảm ứng ----------------

    private fun hit(x: Float, y: Float): String? {
        for (b in boxes) if (b.r.contains(x, y)) return b.key
        for (r in rounds) if (hypot(x - r.cx, y - r.cy) <= r.rad * 1.2f) return r.key
        if (hypot(x - dpadCx, y - dpadCy) <= dpadR * 1.1f) return "DPAD"
        if (hypot(x - lsCx, y - lsCy) <= lsR * 1.25f) return "LS"
        if (hypot(x - rsCx, y - rsCy) <= rsR * 1.25f) return "RS"
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = hit(e.getX(i), e.getY(i))
                if (k != null) {
                    owner[e.getPointerId(i)] = k
                    if (pressed.add(k)) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (act == MotionEvent.ACTION_CANCEL) { owner.clear() } else owner.remove(e.getPointerId(e.actionIndex))
            }
        }
        // Tính lại điều khiển nào đang được giữ từ các ngón còn lại.
        val held = HashSet<String>(owner.values)
        pressed.clear()
        pressed.addAll(held)
        dpadBits = 0u; lx = 0f; ly = 0f; rx = 0f; ry = 0f
        for (i in 0 until e.pointerCount) {
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) break
            if ((act == MotionEvent.ACTION_POINTER_UP) && i == e.actionIndex) continue
            when (owner[e.getPointerId(i)]) {
                // Nhiều ngón chạm D-pad cùng lúc phải hợp nhất hướng, không ghi đè nhau.
                "DPAD" -> dpadBits = dpadBits or dpadFrom(e.getX(i) - dpadCx, e.getY(i) - dpadCy)
                "LS" -> { val v = axis(e.getX(i) - lsCx, e.getY(i) - lsCy, lsR); lx = v.first; ly = v.second }
                "RS" -> { val v = axis(e.getX(i) - rsCx, e.getY(i) - rsCy, rsR); rx = v.first; ry = v.second }
            }
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) { pressed.clear(); dpadBits = 0u }
        publish()
        invalidate()
        return true
    }

    private fun axis(dx: Float, dy: Float, r: Float): Pair<Float, Float> {
        if (!r.isFinite() || r <= 0f || !dx.isFinite() || !dy.isFinite()) return 0f to 0f
        // Dùng cùng radial dead zone với tay cầm thật: loại rung quanh tâm,
        // sau đó mở rộng phần hành trình còn lại để cần ảo vẫn đạt đủ biên độ.
        return AnalogAxis.normalizePair(dx / r, dy / r, 0.12f, 0.12f)
    }

    private fun dpadFrom(dx: Float, dy: Float): UInt =
        VirtualDpad.direction(dx, dy, dpadR * 0.22f)

    private fun publish() {
        val st = ControllerState()
        var b = dpadBits
        for (k in pressed) b = b or masks(k)
        st.buttons = b
        st.l2State = if ("L2" in pressed) UByte.MAX_VALUE else 0u.toUByte()
        st.r2State = if ("R2" in pressed) UByte.MAX_VALUE else 0u.toUByte()
        st.leftX = (lx * Short.MAX_VALUE).toInt().toShort()
        st.leftY = (ly * Short.MAX_VALUE).toInt().toShort()
        st.rightX = (rx * Short.MAX_VALUE).toInt().toShort()
        st.rightY = (ry * Short.MAX_VALUE).toInt().toShort()
        if (st != last) { last = st; onState(st) }
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    /** Thả hết mọi nút (khi ẩn tay cầm, đổi kích thước, tháo view hoặc mất kết nối). */
    fun releaseAll() {
        owner.clear(); pressed.clear(); dpadBits = 0u; lx = 0f; ly = 0f; rx = 0f; ry = 0f
        publish(); invalidate()
    }
}
