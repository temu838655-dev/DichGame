package com.dichgame.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Lớp phủ bản dịch lên chữ giao diện của game (menu, vật phẩm, kỹ năng, nhiệm vụ, mô tả, thông báo...): mỗi khối chữ đọc được trên
 * hình được dịch rồi vẽ đè đúng chỗ chữ gốc, nền lấy theo màu quanh khối chữ. Lớp này chỉ vẽ, không nhận chạm, tay cầm ảo bên dưới
 * vẫn bấm bình thường.
 */
class UiOverlay(ctx: Context) : View(ctx) {
    class Draw(val rect: RectF, val text: String, val bg: Int, val fg: Int, val unsure: Boolean, val corners: FloatArray? = null, val lines: Int = 1)

    private var draws: List<Draw> = emptyList()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val prepared = java.util.IdentityHashMap<Draw, Prepared?>()
    private data class Prepared(val matrix: Matrix, val w: Float, val h: Float, val pad: Float, val layout: StaticLayout)
    private val dm = ctx.resources.displayMetrics

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var logDraw = false

    fun set(list: List<Draw>) {
        prepared.keys.retainAll(list.toSet())
        draws = list
        logDraw = BuildConfig.DEBUG && list.isNotEmpty()
        invalidate()
    }

    fun clear() = set(emptyList())

    private fun dp(v: Float) = v * dm.density
    private fun sp(v: Float) = v * dm.scaledDensity

    override fun onDraw(cv: Canvas) {
        if (logDraw) { logDraw = false; Log.i("DG_UITR", "ve ${draws.size} khoi, khung ${width}x${height}, vi tri ${(parent as? ViewGroup)?.indexOfChild(this)}/${(parent as? ViewGroup)?.childCount}") }
        for (d in draws) drawOne(cv, d)
    }

    private fun drawOne(cv: Canvas, d: Draw) {
        val p = if (prepared.containsKey(d)) prepared[d] else prepare(d).also { prepared[d] = it }
        if (p == null) return
        cv.save()
        cv.concat(p.matrix)
        cv.clipRect(0f,0f,p.w,p.h)
        bgPaint.color = d.bg
        cv.drawRect(0f,0f,p.w,p.h,bgPaint)
        cv.translate(p.pad, (p.h-p.layout.height)/2f)
        p.layout.draw(cv)
        cv.restore()
    }

    private fun prepare(d: Draw): Prepared? {
        if (d.unsure || d.text.isBlank()) return null
        val quad = d.corners?.takeIf { it.size == 8 && it.all { n -> n.isFinite() } }
            ?: floatArrayOf(d.rect.left,d.rect.top,d.rect.right,d.rect.top,d.rect.right,d.rect.bottom,d.rect.left,d.rect.bottom)
        fun edge(a: Int, b: Int): Float = kotlin.math.hypot(quad[a]-quad[b],quad[a+1]-quad[b+1])
        val w = (edge(0,2)+edge(6,4))/2f
        val h = (edge(0,6)+edge(2,4))/2f
        if (w < 8f || h < 6f || !w.isFinite() || !h.isFinite()) return null
        val transform = Matrix()
        if (!transform.setPolyToPoly(floatArrayOf(0f,0f,w,0f,w,h,0f,h),0,quad,0,4)) return null
        val pad = minOf(dp(1.5f), h * 0.06f)
        val tw = (w - pad * 2).toInt().coerceAtLeast(1)
        val available = h - pad * 2
        // Fit complete Vietnamese text inside the original block. Never ellipsize or cover adjacent icons.
        val originalSize = (h / d.lines.coerceAtLeast(1) * 0.82f).coerceAtMost(sp(28f))
        if (originalSize < 7f) return null
        val minSize = minOf(originalSize, maxOf(7f, originalSize * 0.60f))
        val paint = TextPaint(tp).apply { color = d.fg }
        var size = originalSize
        var lay = layout(d.text, tw, size, d.lines > 1, paint)
        while ((lay.height > available || lay.getLineEnd(lay.lineCount - 1) < d.text.length) && size > minSize) {
            size = maxOf(minSize, size - 0.6f)
            lay = layout(d.text, tw, size, d.lines > 1, paint)
        }
        // A modest horizontal condensation helps longer Vietnamese labels fit
        // without sacrificing font height or dropping any words.
        if (lay.height > available) {
            paint.textScaleX = 0.88f
            lay = layout(d.text, tw, size, d.lines > 1, paint)
        }
        if (lay.height > available || lay.getLineEnd(lay.lineCount - 1) < d.text.length) return null
        if ((0 until lay.lineCount).any { lay.getLineWidth(it) > tw + 1f }) return null
        return Prepared(transform, w, h, pad, lay)
    }

    private fun layout(text: String, width: Int, size: Float, multiline: Boolean, paint: TextPaint): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(if (multiline) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .build()
    }
}

/**
 * Dịch chữ giao diện ngay trong màn chơi Remote Play: lấy hình từ SurfaceView, đọc chữ cả khung hình, lọc bỏ ID, số liệu, biến,
 * chữ viết tắt chỉ số, chữ đã là tiếng Việt ([UiText]); dịch từng khối (gói Việt hoá, thuật ngữ, AI hoặc dịch trên máy), giữ nguyên
 * mã giữ chỗ; vẽ bản dịch đè lên chữ gốc ([UiOverlay]). Bản dịch hỏng (mất mã giữ chỗ, chưa dịch được) thì để nguyên chữ gốc.
 * Danh sách chuỗi chưa dịch được hoặc còn nghi ngờ lưu ở [missed] để người dùng bổ sung vào gói Việt hoá.
 *
 * [exclude]: vùng (tỉ lệ 0..1 của hình) đang do dịch phụ đề đảm nhiệm; khối chữ nằm trong đó bị bỏ qua để khỏi dịch hai lần.
 */
class UiTranslate(
    private val act: Activity,
    private val video: () -> SurfaceView?,
    private val exclude: () -> RectF?,
) {
    companion object {
        private const val TAG = "DG_UITR"
        private const val MAX_SIDE = 1280
        private const val MAX_INFLIGHT = UiBatch.MAX_ITEMS
        /** Một chữ đang hiện bản dịch máy chỉ được xin AI dịch lại tối đa chừng này lần. */
        private const val MAX_UPGRADES = 2
        private const val MAX_CACHE = 600
        private const val MAX_QUEUE = 80
        /** Chế độ một lần: giữ bản dịch bấy nhiêu mili giây rồi tự tắt. */
        const val HOLD_MS = 12000L
        /** Bản dịch máy (do AI lỗi) hiện được ít nhất chừng này mili giây thì khi thấy lại khối chữ sẽ xin AI dịch lại. */
        const val UPGRADE_AFTER_MS = 25_000L
        /** Chế độ một lần: quá thời gian này (từ lúc mô hình dịch sẵn sàng) mà chưa dịch xong thì dừng. */
        private const val ONCE_LIMIT_MS = 30000L
        @Volatile var active = false
    }

    val overlay = UiOverlay(act).also { ov ->
        // Khung hình đổi kích thước (xoay máy, phóng/thu): toạ độ bản dịch cũ không còn đúng → xoá, lượt quét sau vẽ lại
        ov.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) {
                if (BuildConfig.DEBUG) Log.i(TAG, "khung doi kich thuoc ${orr - ol}x${ob - ot} -> ${r - l}x${b - t}, xoa lop phu cu")
                items = emptyList(); ov.clear(); lastFine = null; shown = 0
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    // PixelCopy owns a bitmap until its completion callback. Do not remove this
    // callback when stopping the scanner; it must still recycle stale frames.
    private val copyHandler = Handler(Looper.getMainLooper())

    var running = false
        private set

    /** Giai đoạn để nút trên thanh công cụ báo đúng: tắt / đang tải mô hình / đang quét và dịch / (một lần) đang giữ bản dịch chờ tự tắt. */
    enum class Phase { OFF, PREPARING, WORKING, SHOWING }

    val phase: Phase
        get() = when {
            !running -> Phase.OFF
            !ready -> Phase.PREPARING
            once && holdUntil > 0L -> Phase.SHOWING
            else -> Phase.WORKING
        }

    /** Chế độ một lần: còn bao lâu nữa (ms) bản dịch tự tắt; 0 nếu chưa tới giai đoạn giữ. */
    val holdLeftMs: Long
        get() = if (once && holdUntil > 0L) (holdUntil - SystemClock.uptimeMillis()).coerceAtLeast(0L) else 0L

    /** Số khối chữ đang hiện bản dịch trên hình (cho biết "Dịch menu" đang chạy thật). */
    @Volatile var shown = 0
        private set

    /**
     * Chế độ "Dịch hết chữ": đọc và dịch MỘT lần toàn bộ chữ trên khung hình hiện tại (kể cả vùng phụ đề), giữ bản dịch
     * [HOLD_MS] rồi tự tắt (hoặc tắt ngay khi cảnh đổi). Khác với chế độ liên tục "Dịch menu".
     */
    var once = false
        private set
    private var onDone: (() -> Unit)? = null
    private var onceStart = 0L
    private var holdUntil = 0L
    private var emptyPasses = 0
    private var ready = false
    private var busy = false
    private var ocr: Ocr? = null
    private var tt: TextTranslator? = null
    private var lang = "en"
    private var cjk = false
    private var lastFine: IntArray? = null
    private var skipRun = 0
    private var copyFails = 0
    private var inflight = 0
    private var gen = 0
    private var gameScope = ""
    private fun scope(p: Prefs) = listOf(p.profile, PackStore.gameName(p), p.game, p.lang, p.glossary, p.cast).joinToString("\u0000")
    private var lastNoticeAt = 0L
    private var pumping = false

    /** [via]/[at]: bản dịch đang hiện do đâu và từ lúc nào; bản dịch máy (AI đang lỗi) được dịch lại bằng AI khi AI dùng được ([upgrading]: đang chờ bản mới). */
    private class Entry(var vi: String? = null, var unsure: Boolean = false, var failed: Boolean = false, var via: String = "", var at: Long = 0L, var upgrading: Boolean = false, var upgrades: Int = 0)
    private class Item(val src: String, val key: String, val box: RectF, val bg: Int, val fg: Int, val corners: FloatArray?, val lines: Int)

    private val cache = LinkedHashMap<String, Entry>(64, 0.75f, true)
    /** Cài đặt của lượt dịch đang chạy (đặt ở start). */
    private var prefs: Prefs? = null
    private var items: List<Item> = emptyList()
    /** Khối chữ đang chờ tới lượt dịch (tối đa [MAX_INFLIGHT] khối dịch cùng lúc): (nguyên văn, khoá). */
    private val queue = java.util.ArrayDeque<Pair<String, String>>()

    /** Chuỗi chưa dịch được hoặc còn nghi ngờ: nguyên văn → lý do. */
    val missed = LinkedHashMap<String, String>()

    /** Gắn lớp phủ lên trên hình (gọi lại được nhiều lần, view không bị tạo lại). */
    fun attach(parent: FrameLayout) {
        (overlay.parent as? ViewGroup)?.let { if (it !== parent) it.removeView(overlay) }
        // Nằm ngay trên hình và thẻ trạng thái (vị trí 0 và 1), dưới khung chọn vùng và dòng phụ đề.
        if (overlay.parent == null) parent.addView(overlay, minOf(2, parent.childCount),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** "Dịch hết chữ": dịch một lần rồi tự tắt. [done] được gọi (trên luồng chính) khi tự tắt. */
    fun startOnce(done: () -> Unit) {
        if (running) return
        once = true
        onDone = done
        holdUntil = 0L
        emptyPasses = 0
        onceStart = SystemClock.uptimeMillis()
        start()
        if (!running) { once = false; onDone = null }
    }

    private fun finishOnce(msg: String? = null) {
        val cb = onDone
        stop()
        if (!msg.isNullOrEmpty()) Toast.makeText(act, msg, Toast.LENGTH_LONG).show()
        cb?.invoke()
    }

    fun start() {
        if (running) return
        val pr = Prefs(act)
        prefs = pr
        val newScope = scope(pr)
        if (newScope != gameScope) { cache.clear(); missed.clear(); gameScope = newScope }
        lang = pr.lang
        cjk = Prefs.isCjk(lang)
        running = true
        active = true
        ready = false
        busy = false
        lastFine = null
        skipRun = 0
        copyFails = 0
        inflight = 0
        queue.clear()
        items = emptyList()
        try {
            ocr = Ocr(pr, lang)
            val tr = TextTranslator(act, pr, lang)
            tt = tr
            tr.onFallback = { m -> notice(m) }
            val ctx = act.applicationContext
            Thread { try { PackStore.memory(ctx, pr).warm() } catch (_: Throwable) {} }.start()
            val preparationGeneration = gen
            tr.prepareForPlayback { ok ->
                if (!running || preparationGeneration != gen) return@prepareForPlayback
                if (ok) { ready = true; onceStart = SystemClock.uptimeMillis() }
                else if (once) finishOnce("Không tải được mô hình dịch. Kiểm tra mạng rồi thử lại.")
                else notice("Không tải được mô hình dịch. Kiểm tra mạng rồi tắt bật dịch giao diện lại.")
            }
            handler.postDelayed(tick, 150)
        } catch (e: Throwable) {
            Log.e(TAG, "start loi", e)
            stop()
            Toast.makeText(act, "Không bật được dịch giao diện: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    fun stop() {
        running = false
        active = false
        ready = false
        once = false
        onDone = null
        holdUntil = 0L
        handler.removeCallbacksAndMessages(null)
        overlay.clear()
        items = emptyList()
        shown = 0
        // khối đang chờ hoặc đang dịch dở khi tắt: bỏ khỏi bộ nhớ để lần bật sau được xin dịch lại (không thì nằm trống mãi)
        queue.clear()
        cache.entries.removeAll { it.value.vi == null && !it.value.failed }
        for (v in cache.values) v.upgrading = false
        inflight = 0
        gen++   // kết quả dịch của lượt cũ về muộn sẽ bị bỏ qua (không làm lệch bộ đếm của lượt mới)
        runCatching { tt?.close() }; tt = null
        runCatching { ocr?.close() }; ocr = null
    }

    fun release() = stop()

    private fun notice(m: String) {
        val now = System.currentTimeMillis()
        if (m.isEmpty() || now - lastNoticeAt < 8000) return
        lastNoticeAt = now
        Toast.makeText(act, m, Toast.LENGTH_LONG).show()
    }

    /** Chế độ một lần: xong thì giữ bản dịch rồi tự tắt; quá lâu hoặc không thấy chữ thì dừng kèm báo. Trả true nếu đã tắt. */
    private fun checkOnce(): Boolean {
        if (!once) return false
        val now = SystemClock.uptimeMillis()
        if (holdUntil > 0L) {
            if (now >= holdUntil) { finishOnce(); return true }
            return false
        }
        if (!ready) {
            if (now - onceStart > 45000L) { finishOnce("Mô hình dịch chưa sẵn sàng (mạng chậm?). Thử lại sau."); return true }
            return false
        }
        if (emptyPasses >= 2) { finishOnce("Không thấy chữ nào cần dịch trên hình."); return true }
        if (now - onceStart > ONCE_LIMIT_MS) {
            if (items.isEmpty()) { finishOnce("Không đọc được chữ trên hình. Thử lại khi chữ hiện rõ."); return true }
            // dịch chưa kịp hết: giữ phần đã dịch được vài giây rồi tự tắt, không chờ mãi
            holdUntil = now + HOLD_MS
            queue.clear()
            notice("Dịch chưa xong hết (mạng hoặc mô hình chậm). Phần đã dịch được giữ lại vài giây.")
            return false
        }
        if (items.isNotEmpty() && inflight == 0 && queue.isEmpty() && items.all { val e = cache[it.key]; e != null && (e.vi != null || e.failed) }) {
            holdUntil = now + HOLD_MS
            if (BuildConfig.DEBUG) Log.i(TAG, "mot-lan: dich xong ${items.size} khoi, giu ${HOLD_MS / 1000}s")
        }
        return false
    }

    private val tick = object : Runnable {
        override fun run() {
            val tickGeneration = gen
            try {
                val p = prefs
                if (p != null && scope(p) != gameScope) {
                    if (once) finishOnce() else { stop(); start() }
                    return
                }
                if (!checkOnce()) capture()
            } catch (t: Throwable) {
                Log.e(TAG, "tick loi", t)
                busy = false
            } finally {
                if (running && tickGeneration == gen) handler.postDelayed(this, delay())
            }
        }
    }

    /** Chữ giao diện đổi chậm hơn phụ đề nên quét thưa hơn; đọc bằng Gemini thì thưa nữa để đỡ tốn lượt dùng. */
    private fun delay(): Long {
        if (once) return if (ocr?.isCloud == true) 600L else 250L
        val base = Prefs(act).scanMs.toLong().coerceIn(150L, 3000L)
        return if (ocr?.isCloud == true) (base * 6).coerceIn(900L, 6000L)
        else base.coerceIn(350L, 3000L)
    }

    private fun capture() {
        if (busy || !ready) return
        val sv = video() ?: return
        if (!sv.isShown || !sv.holder.surface.isValid) return
        val sf = sv.holder.surfaceFrame
        val sw = sf.width()
        val sh = sf.height()
        if (sw < 64 || sh < 64) return
        val sc = minOf(1f, MAX_SIDE.toFloat() / maxOf(sw, sh))
        val bw = (sw * sc).toInt().coerceAtLeast(2)
        val bh = (sh * sc).toInt().coerceAtLeast(2)
        val bmp = try { Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888) } catch (_: Throwable) { return }
        busy = true
        try {
            val g = gen
            PixelCopy.request(sv, bmp, { res -> if (g == gen) onCopied(res, bmp) else bmp.recycle() }, copyHandler)
        } catch (e: Throwable) {
            runCatching { bmp.recycle() }
            busy = false
            copyFails++
            Log.e(TAG, "pixelcopy ngoai le", e)
        }
    }

    private fun onCopied(res: Int, bmp: Bitmap) {
        if (!running) { runCatching { bmp.recycle() }; busy = false; return }
        if (res != PixelCopy.SUCCESS) {
            runCatching { bmp.recycle() }
            busy = false
            copyFails++
            if (copyFails == 20) notice("Chưa lấy được hình từ Remote Play để dịch giao diện. Đang tự thử lại…")
            return
        }
        copyFails = 0
        val o = ocr
        if (o == null) { runCatching { bmp.recycle() }; busy = false; return }
        val fine = Ocr.thumbFine(bmp)
        val prev = lastFine
        if (once && holdUntil > 0L) {
            // đang giữ bản dịch: không đọc lại; cảnh đổi hẳn thì tắt luôn để bản dịch cũ khỏi đè lên hình mới
            runCatching { bmp.recycle() }
            busy = false
            if (prev != null && meanDiff(prev, fine) > 18) finishOnce()
            return
        }
        if (prev != null && Ocr.sameStrict(fine, prev) && skipRun < 10) {
            // hình đứng yên: giữ nguyên lớp phủ, khỏi đọc lại
            skipRun++
            runCatching { bmp.recycle() }
            busy = false
            return
        }
        // cảnh đổi hẳn (đóng menu, vào cảnh khác): xoá lớp phủ ngay, khỏi để bản dịch cũ nằm đè lên hình mới
        if (prev != null && meanDiff(prev, fine) > 18) { items = emptyList(); overlay.clear(); shown = 0 }
        lastFine = fine
        skipRun = 0
        // Ocr.read giải phóng ảnh đầu vào khi xong; giữ một bản để lấy màu nền quanh khối chữ
        val keep = try { bmp.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Throwable) { null }
        val bw = bmp.width
        val bh = bmp.height
        val g = gen
        o.read(bmp, { msg ->
            keep?.let { runCatching { it.recycle() } }
            if (g != gen) return@read
            busy = false
            if (msg.isNotEmpty()) notice(msg)
        }) { blocks ->
            try {
                if (running && g == gen) onBlocks(blocks, keep, bw, bh)
            } finally {
                keep?.let { runCatching { it.recycle() } }
                if (g == gen) busy = false
            }
        }
    }

    private fun meanDiff(a: IntArray, b: IntArray): Int {
        if (a.size != b.size || a.isEmpty()) return 255
        var s = 0L
        for (i in a.indices) s += Math.abs(a[i] - b[i])
        return (s / a.size).toInt()
    }

    private fun onBlocks(blocks: List<Blk>, keep: Bitmap?, bw: Int, bh: Int) {
        val vw = overlay.width.toFloat()
        val vh = overlay.height.toFloat()
        if (vw < 2f || vh < 2f) return
        val sv = video() ?: return
        val surfaceLoc = IntArray(2); sv.getLocationOnScreen(surfaceLoc)
        val overlayLoc = IntArray(2); overlay.getLocationOnScreen(overlayLoc)
        val dx = (surfaceLoc[0] - overlayLoc[0]).toFloat()
        val dy = (surfaceLoc[1] - overlayLoc[1]).toFloat()
        val kx = sv.width.toFloat() / bw
        val ky = sv.height.toFloat() / bh
        if (kx <= 0f || ky <= 0f) return
        // "Dịch hết chữ" dịch cả vùng phụ đề; chế độ liên tục để phần phụ đề cho dịch phụ đề lo
        val ex = if (once) null else exclude()
        val out = ArrayList<Item>()
        for (b in blocks) {
            val box = b.box ?: continue
            if (box.height() < 8 || box.width() < 12) continue
            val src = UiText.joinLines(b.text, cjk)
            val vd = UiText.verdict(src, b.conf)
            if (vd != UiText.Verdict.TRANSLATE) {
                if (BuildConfig.DEBUG) Log.i(TAG, "bo qua $vd: ${src.take(60)}")
                continue
            }
            if (ex != null) {
                val sub = RectF(ex.left * bw, ex.top * bh, ex.right * bw, ex.bottom * bh)
                if (overlapFrac(box, sub) > 0.5f) continue
            }
            val style = keep?.let { NativeTextStyle.sample(it, b) }
            if (style == null) {
                if (missed.size < 600) missed[src] = "nền phức tạp, giữ chữ gốc để không che hỏng hình"
                continue
            }
            val key = UiText.key(src)
            val corners = b.corners?.mapIndexed { i, v -> if (i % 2 == 0) v * kx + dx else v * ky + dy }?.toFloatArray()
            out.add(Item(src, key, RectF(box.left * kx + dx, box.top * ky + dy, box.right * kx + dx, box.bottom * ky + dy), style.first, style.second, corners, b.lineCount))
        }
        items = out
        val visible = out.mapTo(HashSet()) { it.key }
        val stale = queue.filter { it.second !in visible }
        queue.removeAll(stale.toSet())
        for ((_, key) in stale) {
            val entry = cache[key] ?: continue
            if (entry.vi == null) cache.remove(key) else entry.upgrading = false
        }
        for (item in out) request(item.src, item.key)
        if (once) emptyPasses = if (out.isEmpty()) emptyPasses + 1 else 0
        refresh()
    }

    private fun overlapFrac(r: android.graphics.Rect, s: RectF): Float {
        val l = maxOf(r.left.toFloat(), s.left)
        val t = maxOf(r.top.toFloat(), s.top)
        val rr = minOf(r.right.toFloat(), s.right)
        val bb = minOf(r.bottom.toFloat(), s.bottom)
        if (rr <= l || bb <= t) return 0f
        val area = (rr - l) * (bb - t)
        val own = (r.width() * r.height()).toFloat().coerceAtLeast(1f)
        return area / own
    }

    /**
     * Xin dịch một khối chữ nếu chưa có trong bộ nhớ. Chạy tối đa [MAX_INFLIGHT] khối cùng lúc, khối còn lại xếp hàng và tự
     * chạy tiếp khi có chỗ (trước đây khối thừa bị bỏ, phải đợi lượt quét sau, mà hình đứng yên thì cả chục giây mới tới lượt).
     */
    private fun request(src: String, key: String) {
        val old = cache[key]
        if (old != null) {
            // khối này đang hiện bản dịch máy vì AI lỗi: sau ít giây (và AI không còn nghỉ) xin dịch lại bằng AI, bản cũ vẫn hiện trong lúc chờ
            // chỉ xin lại khi bản trước là dịch máy; AI đã trả lời rồi thì hỏi lại chỉ tốn lượt
            if (old.via == "may" && old.upgrades < MAX_UPGRADES && !old.upgrading && tt != null && prefs?.let { Ai.enabled(it) && Ai.coolingMs(it) == 0L } == true &&
                SystemClock.uptimeMillis() - old.at > UPGRADE_AFTER_MS && queue.size < MAX_QUEUE) {
                old.upgrading = true
                old.upgrades++
                queue.addLast(src to key)
                pump()
            }
            return
        }
        if (tt == null) return
        if (queue.size >= MAX_QUEUE) return   // quá nhiều khối một lúc: để lượt quét sau
        if (cache.size >= MAX_CACHE) {
            val visible = items.mapTo(HashSet()) { it.key }
            val victim = cache.entries.firstOrNull { it.key !in visible && !it.value.upgrading && (it.value.vi != null || it.value.failed) }
            if (victim == null) return
            cache.remove(victim.key)
        }
        cache[key] = Entry()
        queue.addLast(src to key)
        pump()
    }

    private fun pump() {
        val t = tt ?: return
        if (pumping || !running || holdUntil > 0L) return
        pumping = true
        try {
            while (inflight < MAX_INFLIGHT && queue.isNotEmpty()) {
                val (src, key) = queue.pollFirst() ?: break
                val e = cache[key] ?: continue
                translateOne(t, src, e)
            }
        } finally { pumping = false }
    }

    private fun translateOne(t: TextTranslator, src: String, e: Entry) {
        inflight++
        val g = gen
        val prot = UiText.protect(src)
        t.translateFull(prot.text, null, true) { tr ->
            if (g != gen) return@translateFull   // đã tắt (hoặc tắt rồi bật lại) trong lúc đang dịch
            inflight--
            if (!running) return@translateFull
            val upgrading = e.upgrading
            e.upgrading = false
            e.at = SystemClock.uptimeMillis()
            val restored = UiText.restore(tr.text, prot)
            val valid = restored != null && UiText.safeTranslation(src, restored, tr.uncertain, lang)
            if (upgrading && e.vi != null && !e.unsure && (!valid || tr.via == "may")) {
                pump(); return@translateFull
            }
            e.via = tr.via
            if (BuildConfig.DEBUG) Log.i(TAG, "dich: ${src.take(60)} => ${restored ?: "(mat ma giu cho)"} [${tr.via}]")
            val same = UiText.key(tr.text) == UiText.key(prot.text)
            when {
                restored == null -> { e.failed = true; missed[src] = "mất mã giữ chỗ khi dịch, giữ nguyên chữ gốc" }
                same -> { e.failed = true; missed[src] = "chưa dịch được (bản dịch giống chữ gốc)" }
                !valid -> { e.failed = true; e.unsure = true; missed[src] = "bản dịch không đạt kiểm tra: $restored" }
                else -> {
                    e.vi = restored
                    e.failed = false
                    e.via = tr.via
                    e.at = SystemClock.uptimeMillis()
                    e.unsure = tr.uncertain
                    if (tr.uncertain) missed[src] = "nghi ngờ: $restored" else missed.remove(src)
                }
            }
            refresh()
            pump()
        }
    }

    private fun refresh() {
        val list = ArrayList<UiOverlay.Draw>()
        for (it in items) {
            val e = cache[it.key] ?: continue
            val vi = e.vi ?: continue
            if (!e.unsure && !e.failed) list.add(UiOverlay.Draw(it.box, vi, it.bg, it.fg, false, it.corners, it.lines))
        }
        overlay.set(list)
        shown = list.size
    }

    /** Danh sách chuỗi chưa dịch được hoặc còn nghi ngờ, để sao chép bổ sung vào gói Việt hoá. */
    fun missedReport(): String =
        if (missed.isEmpty()) "" else missed.entries.joinToString("\n") { "${it.key}  →  ${it.value}" }
}
