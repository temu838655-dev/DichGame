package com.dichgame.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Dịch phụ đề ngay trong màn chơi Remote Play, KHÔNG cần quyền ghi màn hình.
 *
 * Hình lấy thẳng từ SurfaceView đang phát video bằng PixelCopy, chỉ trong khung phụ đề bạn chọn; lớp chữ dịch nằm trong
 * chính màn chơi nên không bao giờ lọt vào ảnh chụp (không phải ẩn đi rồi chụp lại như dịch qua ghi màn hình).
 * Phần xử lý phía sau dùng đúng các bộ lọc đã kiểm thử của dịch thông minh: lọc chữ rác, tách người nói, hợp nhất chữ + âm thanh,
 * dịch có kiểm tra, giữ nhịp phụ đề. Mọi việc chạy trên luồng chính; mọi lỗi được bắt, không bao giờ làm app đóng.
 */
class LiveSubs(private val act: Activity, private val video: () -> SurfaceView?) {
    companion object {
        private const val TAG = "DG_LIVE"
        private const val PREF_REGION = "ps_region"
        // Khung phụ đề mặc định: dải dưới của hình (phụ đề game thường nằm ở đây)
        const val DEF_L = 0.05f
        const val DEF_T = 0.70f
        const val DEF_R = 0.95f
        const val DEF_B = 0.97f
        @Volatile var active = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stats = SessionStats.live

    /** Khung chọn vùng phụ đề (cùng loại khung với dịch video web), mặc định ở dải dưới của hình. */
    val box = RegionBox(act).apply {
        fl = DEF_L; ft = DEF_T; fr = DEF_R; fb = DEF_B
        load(act.getSharedPreferences("dg", Context.MODE_PRIVATE).getString(PREF_REGION, null))
        onChanged = { act.getSharedPreferences("dg", Context.MODE_PRIVATE).edit().putString(PREF_REGION, save()).apply(); lastFine = null }
    }

    private val view = TextView(act).apply {
        gravity = Gravity.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        visibility = View.GONE
        isClickable = false
        isFocusable = false
    }

    private fun dp(n: Int) = (n * act.resources.displayMetrics.density).toInt()

    /** Đặt khung chọn vùng về dải phụ đề mặc định (khi lỡ kéo quá to hoặc lệch). */
    fun resetRegion() = box.reset(DEF_L, DEF_T, DEF_R, DEF_B)

    /** Giọng đọc vừa đổi trong cài đặt (cơ bản / AI offline / Gemini, chọn giọng): dựng lại bộ đọc theo cài đặt mới. */
    fun reloadVoice() {
        if (!running || speaker == null) return
        runCatching { speaker?.shutdown() }
        speaker = try {
            Speaker(act, Prefs(act)).also { sp -> sp.onAiError = { m -> Toast.makeText(act, m, Toast.LENGTH_LONG).show() }; if (Prefs(act).voiceEngine == "offline") sp.prepare() }
        } catch (_: Throwable) { null }
    }

    // ---- trạng thái
    var running = false
        private set
    private var ready = false
    private var busy = false
    private var adjusting = false
    private var ocr: Ocr? = null
    private var tt: TextTranslator? = null
    private var speaker: Speaker? = null
    private var speak = false
    /** Chế độ Thuyết minh: game đã có phụ đề tiếng Việt, chỉ đọc to, không dịch. */
    private var narrate = false
    private val autoGameDetector = AutoGameDetector()
    private var lang = "en"
    private var sep = " "
    private var needStable = 1
    private var cand = ""
    private var stable = 0
    private var last = ""
    private var prevVi = ""
    private var lastFine: IntArray? = null
    private var lastSig: IntArray? = null
    private var skipRun = 0
    private var copyFails = 0
    private var lastErrAt = 0L
    private var textOnScreen = false
    private var textGoneSince = 0L
    private var translating = false
    private var translatingSince = 0L
    private var translateToken = 0
    private var speakers = SpeakerBook()
    private var activeGameContext = ""
    private var langTrack: LangSwitchTracker? = null
    private var pendingLang: String? = null
    private val retired = ArrayList<TextTranslator>()
    private val fusion = SubtitleFusion()
    private val rate = SubtitlePacer.SpeechRate()
    private val maxJobs = 12
    private val jobs = java.util.ArrayDeque<Job>()
    private var tvLast = ""
    private var tvVoiceWarned = false
    private val tvExec by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

    private class Line(val speaker: String?, val text: String, val nameLike: Boolean, val src: Src)
    private class Job(val lines: List<Line>, val src: Src, val speechMs: Long, val replace: Boolean, val seenAt: Long)
    private class Card(val speaker: String?, val orig: String, val tr: TextTranslator.Tr, val unsure: Boolean)
    private class Payload(val cards: List<Card>, val seenAt: Long)

    private val pacer = SubtitlePacer(mergePayload = { a, b ->
        val x = a as? Payload
        val y = b as? Payload
        if (x != null && y != null) Payload(x.cards + y.cards, minOf(x.seenAt, y.seenAt)) else y ?: x
    })

    // ---- gắn vào màn hình

    /** Đặt khung chọn vùng và dòng phụ đề vào lớp hình (gọi lại mỗi khi màn chơi dựng lại, ví dụ xoay máy). */
    fun attach(parent: FrameLayout) {
        (box.parent as? ViewGroup)?.removeView(box)
        (view.parent as? ViewGroup)?.removeView(view)
        parent.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        parent.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(6), 0, dp(6), dp(4)) })
        styleView()
    }

    fun setAdjusting(on: Boolean) {
        adjusting = on
        box.adjusting = on
        if (on) { view.visibility = View.GONE }
        lastFine = null; lastSig = null
    }

    val isAdjusting: Boolean get() = adjusting

    private fun fg(): Int = when (act.getSharedPreferences("dg", Context.MODE_PRIVATE).getInt("style", 0)) {
        1 -> 0xFFFFEB3B.toInt(); 2 -> Color.BLACK; else -> Color.WHITE
    }

    private fun bg(): Int = when (act.getSharedPreferences("dg", Context.MODE_PRIVATE).getInt("style", 0)) {
        2 -> 0xE6FFFFFF.toInt(); 3 -> 0; else -> 0xB3000000.toInt()
    }

    private fun styleView() {
        val pr = Prefs(act)
        view.setTextColor(fg())
        view.setBackgroundColor(bg())
        view.textSize = (pr.fontSize * pr.subScale / 100f).coerceIn(10f, 40f)
    }

    /** Cỡ chữ/kiểu phụ đề vừa đổi trong cài đặt: áp dụng ngay, không cần tắt bật dịch. */
    fun restyle() {
        try { styleView() } catch (_: Throwable) {}
    }

    /** Bật/tắt đọc to bản dịch ngay khi đang dịch (loa trên biểu tượng 🔊). */
    fun setSpeak(on: Boolean) {
        speak = on
        if (!running) return
        if (on && speaker == null) {
            try {
                speaker = Speaker(act, Prefs(act)).also { sp -> sp.onAiError = { m -> Toast.makeText(act, m, Toast.LENGTH_LONG).show() } }
            } catch (_: Throwable) { speaker = null }
        } else if (!on) {
            runCatching { speaker?.shutdown() }
            speaker = null
        }
    }

    // ---- bắt đầu / dừng

    fun start() {
        if (running) return
        val pr = Prefs(act)
        narrate = pr.narrate
        // Thuyết minh: chữ trong game đã là tiếng Việt (chữ Latin có dấu), đọc bằng bộ đọc chữ Latin rồi đọc to, không dịch
        lang = if (narrate) "en" else pr.lang
        speak = pr.speak || narrate
        needStable = if (pr.slow) 2 else 1
        val cjk = Prefs.isCjk(lang)
        sep = if (cjk) "" else " "
        stats.reset(System.currentTimeMillis(), batteryPct())
        RecentLines.clear()
        running = true
        active = true
        ready = false; busy = false; translating = false
        cand = ""; stable = 0; last = ""; prevVi = ""
        lastFine = null; lastSig = null; skipRun = 0; copyFails = 0
        textOnScreen = false; textGoneSince = 0L
        jobs.clear(); pacer.cleared(); fusion.clear()
        CrashLog.mark("dich:bat")
        try {
            ocr = Ocr(pr, lang)
            if (speak) {
                speaker = Speaker(act, pr).also { sp -> sp.onAiError = { m -> Toast.makeText(act, m, Toast.LENGTH_LONG).show() } }
            }
            activeGameContext = gameContext(pr)
            speakers = SpeakerBook(PackStore.cast(pr).map { it.name } + pr.detectedSpeakers())
            if (narrate) {
                // không cần mô hình dịch: sẵn sàng ngay, câu đọc được đi thẳng tới phần hiển thị và đọc to
                tt = null
                langTrack = null
                ready = true
                Log.i(TAG, "san sang (thuyet minh)")
            } else {
                val tr = TextTranslator(act, pr, lang)
                tt = tr
                val warmPrefs = pr
                Thread { try { PackStore.memory(act.applicationContext, warmPrefs).warm() } catch (_: Throwable) {} }.start()
                langTrack = LangSwitchTracker(lang)
                notice("Đang tải mô hình dịch (lần đầu cần mạng)...")
                tr.prepare { ok ->
                    if (!running) return@prepare
                    if (ok) { ready = true; notice(""); Log.i(TAG, "san sang") }
                    else notice("Không tải được mô hình dịch. Kiểm tra mạng rồi tắt/bật dịch lại.")
                }
            }
            handler.postDelayed(tick, pr.scanMs.toLong().coerceIn(150L, 3000L))
        } catch (e: Throwable) {
            Log.e(TAG, "start loi", e)
            CrashLog.mark("dich:loi " + e.javaClass.simpleName)
            Toast.makeText(act, "Không bật được dịch phụ đề: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            stop()
        }
    }

    fun stop() {
        val was = running
        running = false
        active = false
        ready = false
        handler.removeCallbacksAndMessages(null)
        jobs.clear()
        pacer.cleared()
        view.text = ""
        view.visibility = View.GONE
        sendTv("clear", "")
        if (was) {
            try {
                val rep = stats.report(System.currentTimeMillis(), batteryPct())
                SessionStats.lastReport = rep
                act.getSharedPreferences("dg", Context.MODE_PRIVATE).edit().putString("lastreport", rep).apply()
            } catch (_: Throwable) {}
            CrashLog.mark("dich:tat")
        }
        retired.forEach { runCatching { it.close() } }
        retired.clear()
        runCatching { tt?.close() }; tt = null
        runCatching { ocr?.close() }; ocr = null
        runCatching { speaker?.shutdown() }; speaker = null
    }

    fun release() {
        stop()
        runCatching { tvExec.shutdown() }
    }

    private fun gameContext(p: Prefs): String = p.profile + "\u0000" + p.game

    /** Đổi game trong Kho game khi Remote Play vẫn chạy: bỏ hàng chờ của game cũ
     * và tải lại danh sách nhân vật. Không tắt kết nối hoặc xin quyền ghi hình lại. */
    private fun syncGameContext(): Boolean {
        if (!running) return false
        val p = Prefs(act)
        val context = gameContext(p)
        if (context == activeGameContext) return false
        activeGameContext = context
        speakers = SpeakerBook(PackStore.cast(p).map { it.name } + p.detectedSpeakers())
        translateToken++
        translating = false
        jobs.clear()
        fusion.clear()
        pacer.cleared()
        RecentLines.clear()
        last = ""; cand = ""; stable = 0; prevVi = ""
        lastFine = null; lastSig = null
        textOnScreen = false
        speaker?.stop()
        notice("")
        Log.i(TAG, "doi ho so dich: " + PackStore.gameName(p))
        return true
    }

    // ---- vòng quét

    private val tick = object : Runnable {
        override fun run() {
            try {
                syncGameContext()
                housekeeping()
                capture()
            } catch (t: Throwable) {
                Log.e(TAG, "tick loi", t)
                busy = false
                reportErr("Lỗi khi đọc chữ: ${t.message ?: t.javaClass.simpleName}. Đang tự thử lại…")
            } finally {
                if (running) handler.postDelayed(this, scanDelay())
            }
        }
    }

    private fun scanDelay(): Long {
        val pr = Prefs(act)
        val f = when (pr.perf) {
            0 -> 2.0
            2 -> if (ocr?.isCloud == true) 1.0 else 0.7
            else -> 1.0
        }
        return (pr.scanMs * f).toLong().coerceIn(120L, 4000L)
    }

    private fun capture() {
        if (busy || !ready || adjusting) return
        val sv = video() ?: return
        if (!sv.isShown || !sv.holder.surface.isValid) return
        val sf = sv.holder.surfaceFrame
        val sw = sf.width()
        val sh = sf.height()
        if (sw < 32 || sh < 32) return
        val l = (box.fl * sw).toInt().coerceIn(0, sw - 2)
        val t = (box.ft * sh).toInt().coerceIn(0, sh - 2)
        val r = (box.fr * sw).toInt().coerceIn(l + 2, sw)
        val b = (box.fb * sh).toInt().coerceIn(t + 2, sh)
        val bmp = try { Bitmap.createBitmap(r - l, b - t, Bitmap.Config.ARGB_8888) } catch (_: Throwable) { return }
        busy = true
        try {
            PixelCopy.request(sv, Rect(l, t, r, b), bmp, { res -> onCopied(res, bmp) }, handler)
        } catch (e: Throwable) {
            runCatching { bmp.recycle() }
            busy = false
            copyFails++
            Log.e(TAG, "pixelcopy ngoai le", e)
            if (copyFails == 3) reportErr("Máy không cho lấy hình từ Remote Play để đọc chữ (${e.javaClass.simpleName}).")
        }
    }

    private fun onCopied(res: Int, bmp: Bitmap) {
        if (!running) { runCatching { bmp.recycle() }; busy = false; return }
        if (res != PixelCopy.SUCCESS) {
            runCatching { bmp.recycle() }
            busy = false
            copyFails++
            Log.i(TAG, "pixelcopy loi $res (lan $copyFails)")
            // chưa có khung hình nào (mới kết nối) là bình thường; chỉ báo khi kéo dài
            if (copyFails == 20 || (copyFails > 20 && copyFails % 100 == 0)) {
                reportErr("Chưa lấy được hình từ Remote Play để đọc chữ (mã $res). Đang tự thử lại…")
            }
            return
        }
        copyFails = 0
        val o = ocr
        if (o == null) { runCatching { bmp.recycle() }; busy = false; return }
        stats.inc("quet")
        try {
            if (o.isCloud) {
                val sig = Ocr.thumb(bmp)
                if (Ocr.same(sig, lastSig)) { runCatching { bmp.recycle() }; busy = false; stats.inc("quet_dung_yen"); return }
                lastSig = sig
            } else if (Prefs(act).skipStatic) {
                val fine = Ocr.thumbFine(bmp)
                val settled = cand.isEmpty() || cand == last
                if (settled && skipRun < 12 && Ocr.sameStrict(fine, lastFine)) {
                    skipRun++
                    runCatching { bmp.recycle() }
                    busy = false
                    stats.inc("quet_dung_yen")
                    return
                }
                skipRun = 0
                lastFine = fine
            }
        } catch (t: Throwable) {
            runCatching { bmp.recycle() }
            busy = false
            return
        }
        val scanAt = System.currentTimeMillis()
        o.read(bmp, { m -> ocrFail(m) }) { r ->
            try { onOcrResult(r, scanAt) } catch (t: Throwable) { Log.e(TAG, "ocr xu ly loi", t); busy = false }
        }
    }

    private fun finishBusy() {
        busy = false
        if (running && ready && (jobs.isNotEmpty() || pacer.queued > 0)) handler.post { pump() }
    }

    private fun ocrFail(m: String) {
        finishBusy()
        lastSig = null; lastFine = null
        if (m.isNotEmpty()) reportErr(m)
    }

    private fun reportErr(m: String) {
        val now = System.currentTimeMillis()
        if (now - lastErrAt > 8000) { lastErrAt = now; notice(m) }
    }

    /** Thông báo của app (đang tải, lỗi...): thay dòng phụ đề đang hiện. */
    private fun notice(t: String) {
        pacer.cleared(); handler.removeCallbacks(hideCheck)
        if (t.isEmpty()) sendTv("clear", "")
        if (adjusting) return
        view.text = t
        view.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---- nhận lời nghe được từ âm thanh game

    /** Câu nhận dạng từ âm thanh game: lọc ảo giác, rồi hợp nhất với chữ trên màn hình. Gọi được từ luồng bất kỳ. */
    fun acceptAudio(raw: String, speechMs: Long, rms: Float) {
        if (!running || narrate) return
        handler.post {
            try {
                if (!running || !ready || syncGameContext()) return@post
                val now = System.currentTimeMillis()
                stats.inc("asr_nhan")
                val v = AsrGuard.check(raw, rms, lang)
                if (!v.ok || v.ambiguous) { stats.inc("asr_loc"); return@post }
                rate.add(v.text, speechMs)
                val fresh = ArrayList<Line>()
                applyOuts(fusion.onAsr(v.text, now), false, fresh, speechMs, now)
                enqueue(fresh, speechMs, now, false)
            } catch (t: Throwable) {
                Log.e(TAG, "asr loi", t)
            }
        }
    }

    // ---- xử lý chữ đọc được

    private fun requiredStable(utts: List<Utterance>): Int {
        val minLetters = utts.minOf { OcrGuard.letterCount(it.text) }
        var need = when {
            minLetters <= 1 -> maxOf(needStable, 3)
            minLetters <= 2 -> maxOf(needStable, 2)
            else -> needStable
        }
        if (utts.all { it.nameLike }) need = maxOf(need, 3)
        return need
    }

    private fun onOcrResult(r: List<Blk>, scanAt: Long) {
        if (!running) { busy = false; return }
        if (syncGameContext()) { busy = false; return }
        val now = System.currentTimeMillis()
        val cjk = Prefs.isCjk(lang)
        val lines = ArrayList<OcrLine>()
        for (b in r) {
            val t = OcrGuard.clean(b.text)
            if (t.isEmpty()) continue
            val v = OcrGuard.check(t, b.conf)
            if (!v.ok) { stats.inc("ocr_rac"); continue }
            val bx = b.box
            lines.add(OcrLine(v.text, bx?.let { Box(it.left, it.top, it.right, it.bottom) }, b.conf))
        }
        val prefs = Prefs(act)
        if (prefs.aiAuto) {
            val detected = autoGameDetector.observe(lines.map { it.text }, PackStore.gameName(prefs))
            if (detected != null && AutoGameSwitch.apply(prefs, detected)) {
                syncGameContext()
                notice("✦ AI Tự động nhận diện: $detected")
                finishBusy()
                return
            }
        }
        val utts = DialogueSplitter.split(lines, cjk, speakers).map { u ->
            // Người dùng đã xóa tên OCR nhận nhầm: bỏ nhãn người nói, giữ nguyên nội dung thoại.
            if (prefs.isIgnoredSpeaker(u.speaker)) Utterance(null, u.text, u.nameLike) else u
        }
        if (utts.isEmpty()) {
            if (textOnScreen) { textOnScreen = false; textGoneSince = now }
            fusion.onScreenEmpty(now)
            last = ""; cand = ""; stable = 0
            if (pacer.showing == null) notice("") else if (canHide(now)) softHide() else scheduleHide()
            finishBusy()
            return
        }
        textOnScreen = true
        val sig = DialogueSplitter.signature(utts)
        if (sig == last) { finishBusy(); return }
        if (sig != cand) { cand = sig; stable = 1 } else stable++
        if (stable < requiredStable(utts)) { finishBusy(); return }
        last = sig
        // Chỉ nhớ người nói khi OCR đã ổn định; không lưu tên chưa được xác nhận.
        for (u in utts) if (!u.nameLike && !u.speaker.isNullOrBlank()) prefs.rememberSpeaker(u.speaker!!)
        stats.inc("ocr_cau", utts.size.toLong())
        Log.i(TAG, "doc: " + utts.joinToString(" | ") { (it.speaker?.let { n -> "$n: " } ?: "") + it.text })
        if (Prefs(act).autoLang && !narrate) {
            for (u in utts) {
                if (!u.nameLike) langTrack?.observe(LangDetect.guess(u.text))?.let { switchLang(it) }
            }
        }
        val fresh = ArrayList<Line>()
        for (u in utts) applyOuts(fusion.onOcr(u.text, u.speaker, now), u.nameLike, fresh, 0L, scanAt)
        enqueue(fresh, 0L, scanAt, false)
        finishBusy()
    }

    private fun applyOuts(outs: List<SubtitleFusion.Out>, nameLike: Boolean, fresh: MutableList<Line>, speechMs: Long, seenAt: Long) {
        for (o in outs) {
            when (o.kind) {
                SubtitleFusion.Kind.DROP -> if (o.src == Src.ASR) stats.inc("asr_trung")
                SubtitleFusion.Kind.HOLD -> {}
                SubtitleFusion.Kind.SHOW -> fresh.add(Line(o.speaker, o.text, nameLike, o.src))
                SubtitleFusion.Kind.REPLACE -> {
                    if (o.src == Src.MIXED) stats.inc("ghep")
                    enqueue(listOf(Line(o.speaker, o.text, nameLike, o.src)), speechMs, seenAt, true)
                }
            }
        }
    }

    private fun enqueue(lines: List<Line>, speechMs: Long, seenAt: Long, replace: Boolean) {
        if (lines.isEmpty()) return
        var rep = replace
        if (replace) {
            val removed = jobs.removeAll { j -> j.lines.any { a -> lines.any { b -> fusion.relation(a.text, b.text) != SubtitleFusion.Rel.NONE } } }
            if (removed) rep = false
        }
        var ms = speechMs
        if (ms == 0L && rate.cps > 0f) ms = (lines.sumOf { l -> l.text.count { c -> !c.isWhitespace() } } * 1000f / rate.cps).toLong()
        while (jobs.size >= maxJobs) { jobs.removeFirst(); stats.inc("hang_day") }
        jobs.addLast(Job(lines, lines[0].src, ms, rep, seenAt))
        pump()
    }

    /** Mỗi lúc chỉ dịch một việc (giữ đúng thứ tự lời thoại), xong thì đưa cho bộ giữ nhịp quyết định khi nào hiện. */
    private fun pump() {
        if (!running || !ready) return
        val now = System.currentTimeMillis()
        pacer.poll(now)?.let { render(it) }
        if (translating) {
            if (now - translatingSince < 30_000L) return
            translating = false; stats.inc("dich_treo")
        }
        val w = jobs.pollFirst() ?: return
        translating = true
        translatingSince = now
        val token = ++translateToken
        translateLines(w) { cards ->
            if (token != translateToken || !running) return@translateLines
            translating = false
            deliver(w, cards)
            handler.post { pump() }
        }
    }

    private fun translateLines(w: Job, done: (List<Card>) -> Unit) {
        val t = tt
        val out = ArrayList<Card>()
        fun next(i: Int) {
            if (i >= w.lines.size) { done(out); return }
            val line = w.lines[i]
            if (t == null) {
                // Đọc Vietsub: không chạy bất kỳ bộ dịch nào, chỉ đọc câu thực sự là tiếng Việt.
                if (!narrate || (!line.nameLike && VietsubGuard.isVietnamese(line.text))) {
                    out.add(Card(line.speaker, line.text, TextTranslator.Tr(line.text, null, false, "vietsub"), false))
                }
                next(i + 1)
            } else {
                t.translateFull(line.text, line.speaker) { tr ->
                    Log.i(TAG, "dich: ${tr.text}")
                    out.add(Card(line.speaker, line.text, tr, tr.uncertain && !line.nameLike))
                    next(i + 1)
                }
            }
        }
        next(0)
    }

    private fun deliver(w: Job, cards: List<Card>) {
        if (cards.isEmpty()) return
        val now = System.currentTimeMillis()
        val text = cards.joinToString(" ") { it.tr.text }
        val item = SubtitlePacer.Item(text, w.src, pacer.holdFor(text, w.speechMs), cards[0].speaker, Payload(cards, w.seenAt))
        val shown = pacer.submit(item, now, w.replace)
        if (shown != null) render(shown) else { stats.inc("hang_cho"); scheduleHide() }
    }

    private fun render(item: SubtitlePacer.Item) {
        val p = item.payload as? Payload ?: return
        showCards(p.cards, item.src)
        val at = System.currentTimeMillis()
        for (c in p.cards) RecentLines.add(c.orig, c.tr.text, c.speaker, c.unsure, at)
        stats.latency(System.currentTimeMillis() - p.seenAt)
        scheduleHide()
    }

    private val hideCheck = Runnable {
        try {
            if (running) {
                val now = System.currentTimeMillis()
                pump()
                if (pacer.showing != null) {
                    if (pacer.mayHide(now)) { if (canHide(now)) softHide() else if (!textOnScreen) scheduleHide() } else scheduleHide()
                }
            }
        } catch (t: Throwable) { Log.e(TAG, "hideCheck loi", t) }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideCheck)
        val at = pacer.hideAt()
        if (at == 0L) return
        handler.postDelayed(hideCheck, (at - System.currentTimeMillis()).coerceAtLeast(250L))
    }

    private fun canHide(now: Long): Boolean {
        val cur = pacer.showing ?: return false
        if (!pacer.mayHide(now)) return false
        return cur.src == Src.ASR || (!textOnScreen && now - textGoneSince >= 1_200L)
    }

    private fun softHide() {
        pacer.cleared()
        handler.removeCallbacks(hideCheck)
        sendTv("clear", "")
        view.text = ""
        view.visibility = View.GONE
    }

    private fun housekeeping() {
        if (!running || !ready) return
        val now = System.currentTimeMillis()
        val outs = fusion.poll(now)
        if (outs.isNotEmpty()) {
            val fresh = ArrayList<Line>()
            applyOuts(outs, false, fresh, 0L, now)
            enqueue(fresh, 0L, now, false)
        }
        pump()
        if (!textOnScreen) fusion.onScreenEmpty(now)
        if (canHide(now)) softHide()
    }

    /** Game đổi sang ngôn ngữ Latin khác: nạp bộ dịch mới ở nền, xong mới thay. */
    private fun switchLang(newLang: String) {
        val ok = newLang != lang && newLang != "vi" && newLang != pendingLang &&
            LangDetect.scriptOfLang(newLang) == 'L' && LangDetect.scriptOfLang(lang) == 'L'
        if (!ok) { langTrack?.current = lang; return }
        pendingLang = newLang
        val t2 = TextTranslator(act, Prefs(act), newLang)
        t2.prepare { done ->
            pendingLang = null
            if (!running || !done) {
                runCatching { t2.close() }
                langTrack?.current = lang
                return@prepare
            }
            tt?.let { old -> retired.add(old); handler.postDelayed({ runCatching { old.close() }; retired.remove(old) }, 30_000L) }
            tt = t2
            lang = newLang
            langTrack?.current = newLang
            stats.inc("doi_ngon_ngu")
            Toast.makeText(act, "Chữ trong game đã đổi sang ${Prefs.srcNameOf(newLang)}: đã chuyển bộ dịch.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- hiển thị

    private fun showCards(cards: List<Card>, src: Src) {
        if (adjusting) return
        styleView()
        val sb = SpannableStringBuilder()
        val fg = fg()
        val dim = (fg and 0x00FFFFFF) or 0xCC000000.toInt()
        val pr = Prefs(act)
        fun add(t: String, size: Float, color: Int) {
            if (t.isEmpty()) return
            val a = sb.length
            sb.append(t).append("\n")
            sb.setSpan(RelativeSizeSpan(size), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(color), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (pr.showPrev) add(prevVi, 0.7f, (fg and 0x00FFFFFF) or 0x99000000.toInt())
        for ((i, c) in cards.withIndex()) {
            if (pr.showOrig && !narrate) add((if (src != Src.OCR && c.orig.isNotEmpty()) "🔊 " else "") + c.orig, 0.75f, dim)
            val d = c.tr.delivery
            if (i == cards.lastIndex && pr.emoTr && pr.emoLabel && d != null && d.label.isNotEmpty()) add("● " + d.label, 0.62f, d.mood.color)
            val a = sb.length
            val name = c.speaker
            if (name != null) {
                sb.append(name).append(": ")
                sb.setSpan(ForegroundColorSpan(SpeakerColor.of(name)), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val b = sb.length
            sb.append(c.tr.text)
            sb.setSpan(ForegroundColorSpan(fg), b, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (c.unsure) {
                val u = sb.length
                sb.append(" (?)")
                sb.setSpan(ForegroundColorSpan(0xFFFFB74D.toInt()), u, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append("\n")
            sb.setSpan(RelativeSizeSpan(1.15f), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (sb.isNotEmpty()) sb.delete(sb.length - 1, sb.length)
        view.text = sb
        view.visibility = View.VISIBLE
        val vi = cards.joinToString(" ") { it.tr.text }
        val tvSpeaks = speak && tvSpeakOn() && !tvVoiceOn()
        val tvText = if (tvSpeaks || cards.none { it.speaker != null }) vi
        else cards.joinToString("\n") { (it.speaker?.let { n -> "$n: " } ?: "") + it.tr.text }
        sendTv("sub", tvText, tvSpeaks)
        val d = cards.lastOrNull()?.tr?.delivery
        val speakerName = cards.lastOrNull()?.speaker
        val scene = cards.map { VoiceLine(it.tr.text, it.speaker, it.tr.delivery) }
        if (speak && tvVoiceOn()) tvVoice(vi, d, speakerName, scene)
        if (speak && ready && !tvSpeakOn()) speaker?.speakScene(scene)
        prevVi = vi
    }

    // ---- phụ đề sang TV (nếu đã ghép)

    private fun tvSpeakOn(): Boolean {
        val p = act.getSharedPreferences("dg", Context.MODE_PRIVATE)
        return p.getBoolean("tvspeak", false) && p.getString("playon", "phone") == "tv" &&
            !p.getString("tvip", "").isNullOrEmpty() && !p.getString("tvtoken", "").isNullOrEmpty()
    }

    private fun tvVoiceOn(): Boolean {
        val pr = Prefs(act)
        return tvSpeakOn() && ((pr.voiceEngine == "offline" && Voices.installed(act, pr.offlineVoice)) || pr.aiVoiceOn)
    }

    private fun tvVoice(text: String, d: Delivery? = null, speakerName: String? = null, scene: List<VoiceLine>? = null) {
        val sp = speaker ?: return
        val sendAudio: (Pcm?, Float) -> Unit = { pcm, speed ->
            val pf = act.getSharedPreferences("dg", Context.MODE_PRIVATE)
            val err = if (pcm == null) "giọng đọc lỗi" else TvLink.postAudio(pf.getString("tvip", "").orEmpty(), pf.getString("tvtoken", "").orEmpty(), pcm, speed)
            if (err != null) {
                sendTv("sub", text, true, force = true)
                if (!tvVoiceWarned) {
                    tvVoiceWarned = true
                    handler.post { Toast.makeText(act, "Không gửi được tiếng sang TV ($err). TV đọc bằng giọng của TV.", Toast.LENGTH_LONG).show() }
                }
            }
        }
        if (scene != null) sp.synthForTvScene(scene, sendAudio)
        else sp.synthForTv(text, d, speakerName, sendAudio)
    }

    private fun sendTv(kind: String, text: String, speakIt: Boolean = false, force: Boolean = false) {
        TvOut.publish(act, kind, text)
        val p = act.getSharedPreferences("dg", Context.MODE_PRIVATE)
        val ip = p.getString("tvip", "").orEmpty()
        val tok = p.getString("tvtoken", "").orEmpty()
        if (p.getString("playon", "phone") != "tv" || ip.isEmpty() || tok.isEmpty()) return
        val key = kind + ":" + text
        if (!force && key == tvLast) return
        tvLast = key
        val size = p.getInt("fs", 18) * p.getInt("subscale", 100) / 100 + 12
        val f = fg(); val b = bg()
        try {
            tvExec.execute {
                try {
                    val c = URL("http://$ip:8765/sub").openConnection() as HttpURLConnection
                    c.requestMethod = "POST"; c.connectTimeout = 1500; c.readTimeout = 1500; c.doOutput = true
                    c.setRequestProperty("X-Token", tok)
                    val body = JSONObject().put("kind", kind).put("text", text).put("size", size).put("speak", speakIt).put("fg", f).put("bg", b)
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    c.responseCode
                    c.disconnect()
                } catch (_: Exception) {}
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    private fun batteryPct(): Int = try {
        val v = (act.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (v in 0..100) v else -1
    } catch (_: Exception) { -1 }
}
