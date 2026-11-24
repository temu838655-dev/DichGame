package com.dichgame.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
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
    private val orange = PlayTheme.ORANGE
    private val ice = PlayTheme.ICON
    private val fill = Color.parseColor("#1D1B2A")
    private val fillDown = Color.parseColor("#5A3412")
    private val back = Color.parseColor("#090A15")

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#B3FF8A24"); strokeWidth = 3f }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#50FFFFFF"); strokeWidth = 2f }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ice; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val sym = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ice; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    // Shader được tạo khi kích thước đổi, không tạo đối tượng mới mỗi khung hình cảm ứng.
    private val bgGradient = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rightHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val psHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    // Nền sóng cam (chỉ chế độ dọc)
    private val waveLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val waveFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waves = ArrayList<Path>()
    private val waveFillPath = Path()
    private val waveAlpha = intArrayOf(255, 170, 105)
    private var ringW = 3f

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
        val portrait = w <= h * 1.25f
        if (!portrait) layoutLandscape(w.toFloat(), h.toFloat()) else layoutPortrait(w.toFloat(), h.toFloat())
        if (portrait && w > 0 && h > 0) {
            bgGradient.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.parseColor("#13121F"), Color.parseColor("#0A0B16"), Color.parseColor("#1A0F14")),
                null, Shader.TileMode.CLAMP)
            buildWaves(w.toFloat(), h.toFloat())
            psHalo.shader = rounds.firstOrNull { it.key == "PS" }?.let { r ->
                RadialGradient(r.cx, r.cy, r.rad * 2.3f,
                    intArrayOf(Color.parseColor("#AAFF7A1A"), Color.parseColor("#00FF7A1A")),
                    null, Shader.TileMode.CLAMP)
            }
            fun halo(cx: Float, cy: Float, radius: Float): RadialGradient =
                RadialGradient(cx, cy, radius * 1.5f,
                    intArrayOf(Color.parseColor("#5AFF8A24"), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
            leftHalo.shader = halo(lsCx, lsCy, lsR)
            rightHalo.shader = halo(rsCx, rsCy, rsR)
        }
        stroke.strokeWidth = (min(w, h) / 200f).coerceAtLeast(2f)
        soft.strokeWidth = stroke.strokeWidth * 0.7f
        sym.strokeWidth = stroke.strokeWidth * 1.3f
        ringW = stroke.strokeWidth
        ring.strokeWidth = ringW
        glow.strokeWidth = ringW * 3f
        waveLine.strokeWidth = stroke.strokeWidth * 1.1f
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

    /** Ba đường sóng cam chạy ngang phần dưới của bảng tay cầm (nền kiểu ảnh mẫu), cộng một dải sáng mờ dưới sóng đầu. */
    private fun buildWaves(w: Float, h: Float) {
        waves.clear()
        waveFillPath.reset()
        val bases = floatArrayOf(0.60f, 0.72f, 0.86f)
        for (i in bases.indices) {
            val y0 = h * bases[i]
            val amp = h * (0.045f - i * 0.01f)
            val p = Path()
            p.moveTo(0f, y0)
            p.cubicTo(w * 0.20f, y0 - amp * 2f, w * 0.35f, y0 + amp * 2f, w * 0.55f, y0)
            p.cubicTo(w * 0.72f, y0 - amp * 2f, w * 0.88f, y0 + amp * 2f, w, y0 - amp)
            waves.add(p)
            if (i == 0) {
                waveFillPath.addPath(p)
                waveFillPath.lineTo(w, h); waveFillPath.lineTo(0f, h); waveFillPath.close()
            }
        }
        waveFill.shader = LinearGradient(0f, h * 0.55f, 0f, h,
            intArrayOf(Color.parseColor("#00FF7A1A"), Color.parseColor("#26FF6A1F"), Color.parseColor("#55FF5A1F")),
            null, Shader.TileMode.CLAMP)
        waveLine.shader = LinearGradient(0f, 0f, w, 0f,
            intArrayOf(Color.parseColor("#00FF8A24"), Color.parseColor("#E6FF8A24"), Color.parseColor("#55FF4D5E")),
            null, Shader.TileMode.CLAMP)
    }

    private fun isFace(key: String) = key == "TRI" || key == "SQU" || key == "CIR" || key == "CRO"

    override fun onDraw(c: Canvas) {
        val portrait = width <= height * 1.25f
        if (portrait) {
            // Kính đen pha tím, sóng cam ở nửa dưới như bảng tay cầm mẫu; ngang luôn trong suốt để không che game.
            c.drawColor(back)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgGradient)
            c.drawPath(waveFillPath, waveFill)
            for (i in waves.indices) { waveLine.alpha = waveAlpha[i]; c.drawPath(waves[i], waveLine) }
        }
        for (b in boxes) {
            val on = b.key in pressed
            fillP.color = if (on) fillDown else fill
            val rr = b.r.height() / 2.6f
            c.drawRoundRect(b.r, rr, rr, fillP)
            c.drawRoundRect(b.r, rr, rr, if (on) stroke else soft)
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
        val portrait = width <= height * 1.25f
        val ps = r.key == "PS"
        val col = when (r.key) {
            "TRI" -> PlayTheme.TRI
            "SQU" -> PlayTheme.SQU
            "CIR" -> PlayTheme.CIR
            "CRO" -> PlayTheme.CRO
            else -> ice
        }
        if (ps && portrait) c.drawCircle(r.cx, r.cy, r.rad * 2.3f, psHalo)
        fillP.color = if (ps) (if (on) Color.parseColor("#FFB066") else orange) else if (on) fillDown else fill
        c.drawCircle(r.cx, r.cy, r.rad, fillP)
        when {
            isFace(r.key) -> {
                // vòng màu có vầng sáng mờ bên ngoài, giống nút tay cầm PlayStation
                glow.color = Color.argb(70, Color.red(col), Color.green(col), Color.blue(col))
                c.drawCircle(r.cx, r.cy, r.rad, glow)
                ring.color = col
                c.drawCircle(r.cx, r.cy, r.rad, ring)
            }
            ps -> { ring.color = Color.parseColor("#FFD2A0"); c.drawCircle(r.cx, r.cy, r.rad, ring) }
            else -> c.drawCircle(r.cx, r.cy, r.rad, soft)
        }
        val s = r.rad * 0.42f
        sym.color = if (ps) Color.WHITE else col
        when (r.key) {
            "TRI" -> { val p = Path(); p.moveTo(r.cx, r.cy - s * 1.1f); p.lineTo(r.cx + s * 1.05f, r.cy + s * 0.8f); p.lineTo(r.cx - s * 1.05f, r.cy + s * 0.8f); p.close(); c.drawPath(p, sym) }
            "SQU" -> c.drawRect(r.cx - s, r.cy - s, r.cx + s, r.cy + s, sym)
            "CIR" -> c.drawCircle(r.cx, r.cy, s * 1.1f, sym)
            "CRO" -> { c.drawLine(r.cx - s, r.cy - s, r.cx + s, r.cy + s, sym); c.drawLine(r.cx + s, r.cy - s, r.cx - s, r.cy + s, sym) }
            "PS" -> {
                // Nút Home: hình ngôi nhà trắng trên nền cam phát sáng
                val q = r.rad * 0.5f
                val h = Path()
                h.moveTo(r.cx - q * 1.15f, r.cy - q * 0.05f); h.lineTo(r.cx, r.cy - q * 1.0f); h.lineTo(r.cx + q * 1.15f, r.cy - q * 0.05f)
                h.moveTo(r.cx - q * 0.8f, r.cy - q * 0.15f); h.lineTo(r.cx - q * 0.8f, r.cy + q * 0.95f)
                h.lineTo(r.cx + q * 0.8f, r.cy + q * 0.95f); h.lineTo(r.cx + q * 0.8f, r.cy - q * 0.15f)
                c.drawPath(h, sym)
            }
            "SHARE" -> { c.drawRect(r.cx - s * 0.9f, r.cy - s * 0.5f, r.cx + s * 0.5f, r.cy + s * 0.9f, sym); c.drawRect(r.cx - s * 0.5f, r.cy - s * 0.9f, r.cx + s * 0.9f, r.cy + s * 0.5f, sym) }
            "TP" -> { for (i in -1..1) for (j in -1..1) c.drawCircle(r.cx + i * s * 0.75f, r.cy + j * s * 0.75f, s * 0.14f, sym) }
            "OPT" -> { for (j in -1..1) c.drawLine(r.cx - s, r.cy + j * s * 0.7f, r.cx + s, r.cy + j * s * 0.7f, sym) }
        }
        sym.color = ice
    }

    private fun drawDpad(c: Canvas) {
        fillP.color = Color.parseColor("#161524")
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
            c.drawRoundRect(rect, half / 3f, half / 3f, if (on) stroke else soft)
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
        if (width <= height * 1.25f) {
            c.drawCircle(cx, cy, r * 1.5f, if (cx < width / 2f) leftHalo else rightHalo)
        }
        fillP.color = Color.parseColor("#13121E")
        c.drawCircle(cx, cy, r, fillP)
        ring.color = Color.parseColor("#66FF8A24")
        c.drawCircle(cx, cy, r, ring)
        val kx = cx + nx * r * 0.7f
        val ky = cy + ny * r * 0.7f
        fillP.color = if (on) fillDown else Color.parseColor("#1E1B2A")
        c.drawCircle(kx, ky, r * 0.5f, fillP)
        // Núm cần: vòng cam dày phát sáng như ảnh mẫu
        glow.color = Color.parseColor("#55FF8A24")
        c.drawCircle(kx, ky, r * 0.46f, glow)
        ring.strokeWidth = ringW * 2.4f
        ring.color = if (on) Color.parseColor("#FFB066") else orange
        c.drawCircle(kx, ky, r * 0.46f, ring)
        ring.strokeWidth = ringW
        fillP.color = if (on) Color.parseColor("#FFD2A0") else orange
        c.drawCircle(kx, ky, r * 0.1f, fillP)
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                // ACTION_OUTSIDE có thể xuất hiện khi cửa sổ nhận chạm ngoài vùng view;
                // xóa toàn bộ owner để không giữ nút nếu hệ thống không gửi UP tương ứng.
                if (act == MotionEvent.ACTION_CANCEL || act == MotionEvent.ACTION_OUTSIDE) owner.clear()
                else owner.remove(e.getPointerId(e.actionIndex))
            }
        }
        // Tính lại điều khiển nào đang được giữ từ các ngón còn lại.
        val held = HashSet<String>(owner.values)
        pressed.clear()
        pressed.addAll(held)
        dpadBits = 0u; lx = 0f; ly = 0f; rx = 0f; ry = 0f
        for (i in 0 until e.pointerCount) {
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL || act == MotionEvent.ACTION_OUTSIDE) break
            if ((act == MotionEvent.ACTION_POINTER_UP) && i == e.actionIndex) continue
            when (owner[e.getPointerId(i)]) {
                // Nhiều ngón chạm D-pad cùng lúc phải hợp nhất hướng, không ghi đè nhau.
                "DPAD" -> dpadBits = dpadBits or dpadFrom(e.getX(i) - dpadCx, e.getY(i) - dpadCy)
                "LS" -> { val v = axis(e.getX(i) - lsCx, e.getY(i) - lsCy, lsR); lx = v.first; ly = v.second }
                "RS" -> { val v = axis(e.getX(i) - rsCx, e.getY(i) - rsCy, rsR); rx = v.first; ry = v.second }
            }
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL || act == MotionEvent.ACTION_OUTSIDE) { pressed.clear(); dpadBits = 0u }
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
