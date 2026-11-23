package com.dichgame.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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
    class Draw(val rect: RectF, val text: String, val bg: Int, val fg: Int, val unsure: Boolean)

    private var draws: List<Draw> = emptyList()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val dm = ctx.resources.displayMetrics

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var logDraw = false

    fun set(list: List<Draw>) {
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
        val padX = dp(3f)
        val padY = dp(2f)
        val base = RectF(d.rect).apply { inset(-padX, -padY) }
        val textW = (base.width() - padX * 2).toInt().coerceAtLeast(dp(20f).toInt())
        // cỡ chữ lớn nhất theo chiều cao khung chữ gốc, thu nhỏ dần tới khi vừa; tiếng Việt thường dài hơn tiếng gốc
        var size = minOf(base.height() * 0.82f, sp(22f))
        val minSize = sp(8.5f)
        tp.color = d.fg
        var lay = layout(d.text, textW, size, Int.MAX_VALUE)
        while (lay.height > base.height() && size > minSize) {
            size -= sp(0.75f)
            lay = layout(d.text, textW, size, Int.MAX_VALUE)
        }
        // vẫn không vừa ở cỡ nhỏ nhất: cho khung cao thêm (tối đa 1,8 lần, đối xứng) rồi cắt bớt bằng dấu ba chấm, không phủ quá rộng
        val maxH = base.height() * 1.8f
        if (lay.height > maxH) {
            val lineH = (lay.height / lay.lineCount.toFloat()).coerceAtLeast(1f)
            lay = layout(d.text, textW, size, maxOf(1, (maxH / lineH).toInt()))
        }
        val h = maxOf(base.height(), lay.height + padY * 2)
        val r = RectF(base.left, base.centerY() - h / 2f, base.right, base.centerY() + h / 2f)
        bgPaint.color = (d.bg and 0x00FFFFFF) or 0xEE000000.toInt()
        cv.drawRoundRect(r, dp(4f), dp(4f), bgPaint)
        if (d.unsure) {
            // bản dịch còn nghi ngờ: viền vàng mảnh để người chơi biết đây chưa chắc
            markPaint.color = 0xCCF2B84B.toInt()
            markPaint.strokeWidth = dp(1f)
            cv.drawRoundRect(r, dp(4f), dp(4f), markPaint)
        }
        cv.save()
        cv.translate(r.left + padX, r.centerY() - lay.height / 2f)
        lay.draw(cv)
        cv.restore()
    }

    private fun layout(text: String, width: Int, size: Float, maxLines: Int): StaticLayout {
        tp.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
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
        private const val MAX_INFLIGHT = 3
        private const val MAX_CACHE = 600
        private const val MAX_QUEUE = 80
        /** Chế độ một lần: giữ bản dịch bấy nhiêu mili giây rồi tự tắt. */
        const val HOLD_MS = 12000L
        /** Chế độ một lần: quá thời gian này (từ lúc mô hình dịch sẵn sàng) mà chưa dịch xong thì dừng. */
        private const val ONCE_LIMIT_MS = 30000L
        @Volatile var active = false
    }

    val overlay = UiOverlay(act).also { ov ->
        // Khung hình đổi kích thước (xoay máy, phóng/thu): toạ độ bản dịch cũ không còn đúng → xoá, lượt quét sau vẽ lại
        ov.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) {
                if (BuildConfig.DEBUG) Log.i(TAG, "khung doi kich thuoc ${orr - ol}x${ob - ot} -> ${r - l}x${b - t}, xoa lop phu cu")
                items = emptyList(); ov.clear(); lastFine = null
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())

    var running = false
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
    private var lastNoticeAt = 0L

    private class Entry(var vi: String? = null, var unsure: Boolean = false, var failed: Boolean = false)
    private class Item(val src: String, val key: String, val box: RectF, val bg: Int, val fg: Int)

    private val cache = HashMap<String, Entry>()
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
            tr.prepare { ok ->
                if (!running) return@prepare
                if (ok) { ready = true; onceStart = SystemClock.uptimeMillis() }
                else if (once) finishOnce("Không tải được mô hình dịch. Kiểm tra mạng rồi thử lại.")
                else notice("Không tải được mô hình dịch. Kiểm tra mạng rồi tắt bật dịch giao diện lại.")
            }
            handler.postDelayed(tick, 700)
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
        // khối đang chờ hoặc đang dịch dở khi tắt: bỏ khỏi bộ nhớ để lần bật sau được xin dịch lại (không thì nằm trống mãi)
        queue.clear()
        cache.entries.removeAll { it.value.vi == null && !it.value.failed }
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
            try {
                if (!checkOnce()) capture()
            } catch (t: Throwable) {
                Log.e(TAG, "tick loi", t)
                busy = false
            } finally {
                if (running) handler.postDelayed(this, delay())
            }
        }
    }

    /** Chữ giao diện đổi chậm hơn phụ đề nên quét thưa hơn; đọc bằng Gemini thì thưa nữa để đỡ tốn lượt dùng. */
    private fun delay(): Long {
        if (once) return 600L   // một lần: người chơi đang chờ kết quả nên quét sát hơn
        val base = Prefs(act).scanMs.toLong().coerceIn(150L, 3000L) * 2
        return (if (ocr?.isCloud == true) base * 3 else base).coerceIn(900L, 6000L)
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
            PixelCopy.request(sv, bmp, { res -> onCopied(res, bmp) }, handler)
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
        if (prev != null && meanDiff(prev, fine) > 18) { items = emptyList(); overlay.clear() }
        lastFine = fine
        skipRun = 0
        // Ocr.read giải phóng ảnh đầu vào khi xong; giữ một bản để lấy màu nền quanh khối chữ
        val keep = try { bmp.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Throwable) { null }
        val bw = bmp.width
        val bh = bmp.height
        o.read(bmp, { msg ->
            keep?.let { runCatching { it.recycle() } }
            busy = false
            if (msg.isNotEmpty()) notice(msg)
        }) { blocks ->
            try {
                if (running) onBlocks(blocks, keep, bw, bh)
            } finally {
                keep?.let { runCatching { it.recycle() } }
                busy = false
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
        val kx = vw / bw
        val ky = vh / bh
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
            val bg = keep?.let { sampleBackground(it, box.left, box.top, box.right, box.bottom) } ?: Color.BLACK
            val fg = if (luminance(bg) > 150) 0xFF101010.toInt() else Color.WHITE
            val key = UiText.key(src)
            out.add(Item(src, key, RectF(box.left * kx, box.top * ky, box.right * kx, box.bottom * ky), bg, fg))
            request(src, key)
        }
        items = out
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

    /** Màu nền quanh khối chữ: trung vị các điểm ngay sát bên ngoài khung (chữ nằm trong khung nên không lẫn vào). */
    private fun sampleBackground(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): Int {
        val m = 3
        val xs = intArrayOf(l - m, (l + r) / 2, r + m)
        val ys = intArrayOf(t - m, (t + b) / 2, b + m)
        val rs = ArrayList<Int>()
        val gs = ArrayList<Int>()
        val bs = ArrayList<Int>()
        for (yi in ys.indices) for (xi in xs.indices) {
            if (xi == 1 && yi == 1) continue
            val x = xs[xi].coerceIn(0, bmp.width - 1)
            val y = ys[yi].coerceIn(0, bmp.height - 1)
            val c = bmp.getPixel(x, y)
            rs.add(Color.red(c)); gs.add(Color.green(c)); bs.add(Color.blue(c))
        }
        fun med(v: ArrayList<Int>): Int { v.sort(); return v[v.size / 2] }
        return Color.rgb(med(rs), med(gs), med(bs))
    }

    private fun luminance(c: Int): Int = (Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)) / 10

    /**
     * Xin dịch một khối chữ nếu chưa có trong bộ nhớ. Chạy tối đa [MAX_INFLIGHT] khối cùng lúc, khối còn lại xếp hàng và tự
     * chạy tiếp khi có chỗ (trước đây khối thừa bị bỏ, phải đợi lượt quét sau, mà hình đứng yên thì cả chục giây mới tới lượt).
     */
    private fun request(src: String, key: String) {
        if (cache.containsKey(key)) return
        if (tt == null) return
        if (queue.size >= MAX_QUEUE) return   // quá nhiều khối một lúc: để lượt quét sau
        if (cache.size > MAX_CACHE) { cache.clear(); queue.clear() }
        cache[key] = Entry()
        queue.addLast(src to key)
        pump()
    }

    private fun pump() {
        val t = tt ?: return
        while (inflight < MAX_INFLIGHT && queue.isNotEmpty()) {
            val (src, key) = queue.pollFirst() ?: break
            val e = cache[key] ?: continue
            translateOne(t, src, e)
        }
    }

    private fun translateOne(t: TextTranslator, src: String, e: Entry) {
        inflight++
        val g = gen
        val prot = UiText.protect(src)
        t.translateFull(prot.text, null, true) { tr ->
            if (g != gen) return@translateFull   // đã tắt (hoặc tắt rồi bật lại) trong lúc đang dịch
            inflight--
            if (!running) return@translateFull
            val restored = UiText.restore(tr.text, prot)
            if (BuildConfig.DEBUG) Log.i(TAG, "dich: ${src.take(60)} => ${restored ?: "(mat ma giu cho)"} [${tr.via}]")
            val same = UiText.key(tr.text) == UiText.key(prot.text)
            when {
                restored == null -> { e.failed = true; missed[src] = "mất mã giữ chỗ khi dịch, giữ nguyên chữ gốc" }
                same -> { e.failed = true; missed[src] = "chưa dịch được (bản dịch giống chữ gốc)" }
                else -> {
                    e.vi = restored
                    e.unsure = tr.uncertain
                    if (tr.uncertain) missed[src] = "nghi ngờ: $restored"
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
            list.add(UiOverlay.Draw(it.box, vi, it.bg, it.fg, e.unsure))
        }
        overlay.set(list)
    }

    /** Danh sách chuỗi chưa dịch được hoặc còn nghi ngờ, để sao chép bổ sung vào gói Việt hoá. */
    fun missedReport(): String =
        if (missed.isEmpty()) "" else missed.entries.joinToString("\n") { "${it.key}  →  ${it.value}" }
}
