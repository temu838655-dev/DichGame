package com.dichgame.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Khung chọn vùng chữ để đọc (OCR) đè lên trang web: kéo thân khung để dời, kéo chấm tròn ở góc dưới phải để đổi cỡ.
 * Vị trí lưu theo tỉ lệ so với vùng trang web nên giữ được khi xoay màn hình.
 */
class RegionBox(c: Context) : View(c) {
    var fl = 0.04f
    var ft = 0.20f
    var fr = 0.96f
    var fb = 0.36f
    var adjusting = false
        set(v) { field = v; invalidate() }
    var active = false
        set(v) { field = v; invalidate() }
    var covering = false
        set(v) { field = v; invalidate() }
    var onChanged: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#FFD0A8") }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var mode = 0 // 0 không, 1 dời, 2 đổi cỡ
    private var lastX = 0f
    private var lastY = 0f

    fun reset() { fl = 0.04f; ft = 0.20f; fr = 0.96f; fb = 0.36f; invalidate(); onChanged?.invoke() }

    fun load(s: String?) {
        val a = s?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return
        if (a.size == 4 && a[2] > a[0] + 0.05f && a[3] > a[1] + 0.03f) { fl = a[0]; ft = a[1]; fr = a[2]; fb = a[3] }
    }

    fun save(): String = "$fl,$ft,$fr,$fb"

    /** Khung theo điểm ảnh trong view này. */
    fun region(): RectF = RectF(fl * width, ft * height, fr * width, fb * height)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(fl * w, ft * h, fr * w, fb * h)
        if (adjusting) {
            fill.color = 0x70000000
            fill.style = Paint.Style.FILL
            cv.drawRect(0f, 0f, w, rect.top, fill)
            cv.drawRect(0f, rect.bottom, w, h, fill)
            cv.drawRect(0f, rect.top, rect.left, rect.bottom, fill)
            cv.drawRect(rect.right, rect.top, w, rect.bottom, fill)
        } else if (covering && active) {
            fill.color = 0xF0101510.toInt()
            fill.style = Paint.Style.FILL
            cv.drawRoundRect(rect, 10f * d, 10f * d, fill)
        }
        if (adjusting || active) {
            line.strokeWidth = if (adjusting) 3f * d else 1.6f * d
            line.alpha = if (adjusting) 255 else 110
            cv.drawRoundRect(rect, 10f * d, 10f * d, line)
        }
        if (adjusting) {
            fill.style = Paint.Style.FILL
            fill.color = Color.WHITE
            cv.drawCircle(rect.right, rect.bottom, 14f * d, fill)
            line.alpha = 255
            line.strokeWidth = 2f * d
            cv.drawCircle(rect.right, rect.bottom, 14f * d, line)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!adjusting) return false
        val w = width.toFloat()
        val h = height.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val r = region()
                lastX = e.x; lastY = e.y
                mode = when {
                    abs(e.x - r.right) < 34f * d && abs(e.y - r.bottom) < 34f * d -> 2
                    r.contains(e.x, e.y) || RectF(r).apply { inset(-10f * d, -10f * d) }.contains(e.x, e.y) -> 1
                    else -> 0
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - lastX) / w
                val dy = (e.y - lastY) / h
                lastX = e.x; lastY = e.y
                val minW = 80f * d / w
                val minH = 40f * d / h
                if (mode == 1) {
                    val bw = fr - fl
                    val bh = fb - ft
                    fl = (fl + dx).coerceIn(0f, 1f - bw); fr = fl + bw
                    ft = (ft + dy).coerceIn(0f, 1f - bh); fb = ft + bh
                } else if (mode == 2) {
                    fr = (fr + dx).coerceIn(fl + minW, 1f)
                    fb = (fb + dy).coerceIn(ft + minH, 1f)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode != 0) onChanged?.invoke()
                mode = 0
                return true
            }
        }
        return true
    }
}

/**
 * Dịch video trên web: mở trang video trong app rồi dịch phụ đề tiếng Việt ngay bên dưới.
 * Nguồn chữ: phụ đề CC của trình phát (đọc từ trang) hoặc quét chữ trên hình (OCR) trong khung bạn chọn.
 * Nút dịch bắt đầu / tạm dừng, chọn ngôn ngữ, đọc to, cỡ chữ và các tùy chọn nằm ở thanh công cụ dưới cùng.
 */
class WebTranslateActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var sp: SharedPreferences
    private lateinit var web: WebView
    private lateinit var urlField: EditText
    private lateinit var box: RegionBox
    private lateinit var textView: TextView
    private lateinit var origView: TextView
    private lateinit var statusChip: TextView
    private lateinit var langChip: TextView
    private lateinit var transBtn: FrameLayout
    private lateinit var transIcon: ToolIcon
    private lateinit var srcIcon: ToolIcon
    private lateinit var srcLabel: TextView
    private lateinit var srcPill: View
    private lateinit var speakIcon: ToolIcon
    private lateinit var toolsRow: View
    private lateinit var adjustRow: View
    private lateinit var headRow: View
    private lateinit var bodyScroll: ScrollView
    private lateinit var fallbackPill: View
    private lateinit var backBtn: TextView
    private lateinit var fwdBtn: TextView
    private val ui = Handler(Looper.getMainLooper())

    private var translator: TextTranslator? = null
    private var ocr: Ocr? = null
    private var ocrFor = ""
    private var speaker: Speaker? = null

    private var running = false
    private var everStarted = false
    private var source = "cc"          // "cc" hoặc "ocr"
    private var hideOrig = false
    private var adjusting = false
    private var seq = 0
    private var lastProcessed = ""
    private var pendingText: String? = null
    private var scanBusy = false
    private var lastThumb: IntArray? = null
    private var lastCand = ""
    private var lastShown = ""
    private var miss = 0

    private val js = """
(function(){
  if (window.__dg) return; window.__dg = true;
  var last = '';
  function send(t){
    t = (t || '').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim();
    if (t && t.length < 400 && t !== last) { last = t; DG.sub(t); }
  }
  function tracks(){
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) {
      var tt = vs[i].textTracks;
      for (var j = 0; j < tt.length; j++) {
        var t = tt[j];
        if (t.kind !== 'subtitles' && t.kind !== 'captions') continue;
        if (t.mode === 'disabled') t.mode = 'hidden';
        if (!t.__dg) {
          t.__dg = true;
          t.addEventListener('cuechange', function(){
            var a = this.activeCues, s = '';
            if (a) for (var k = 0; k < a.length; k++) s += (a[k].text || '') + ' ';
            send(s);
          });
        }
      }
    }
  }
  var sel = ['.ytp-caption-segment', '.captions-text', '.vjs-text-track-display', '.jw-text-track-cue', '.plyr__captions', '.player-timedtext', '.fp-subtitle'];
  function dom(){
    for (var i = 0; i < sel.length; i++) {
      var els = document.querySelectorAll(sel[i]);
      if (els.length) {
        var s = '';
        for (var k = 0; k < els.length; k++) s += (els[k].innerText || '') + ' ';
        s = s.trim();
        if (s) { send(s); return; }
      }
    }
  }
  window.__dgHide = function(on){
    var s = document.getElementById('__dgcss');
    if (on) {
      if (!s) {
        s = document.createElement('style'); s.id = '__dgcss';
        s.textContent = '.ytp-caption-window-container,.caption-window,.vjs-text-track-display,.jw-captions,.plyr__captions,.player-timedtext,.fp-subtitle{opacity:0 !important}video::cue{opacity:0 !important}';
        document.head.appendChild(s);
      }
    } else if (s) s.remove();
  };
  setInterval(function(){ try { tracks(); dom(); } catch (e) {} }, 500);
})();
"""

    inner class Bridge {
        @JavascriptInterface
        fun sub(t: String) { runOnUiThread { onSub(t) } }
    }

    private fun dp(n: Number) = Ui.dp(this, n)

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        sp = getSharedPreferences("web", MODE_PRIVATE)
        source = sp.getString("source", "cc") ?: "cc"
        hideOrig = sp.getBoolean("hide", false)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = Ui.vbox(this)
        root.setBackgroundColor(Ui.BG)

        // ---- thanh trình duyệt
        val bar = Ui.hbox(this)
        bar.setPadding(dp(10), dp(8), dp(10), dp(8))
        bar.addView(circleBtn("✕") { finish() })
        backBtn = navBtn("‹") { if (web.canGoBack()) web.goBack() }
        fwdBtn = navBtn("›") { if (web.canGoForward()) web.goForward() }
        bar.addView(backBtn, Ui.lp(dp(44), dp(44)).apply { leftMargin = dp(4) })
        bar.addView(fwdBtn, Ui.lp(dp(44), dp(44)))
        val pill = Ui.hbox(this)
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = Ui.bg(this, Ui.CARD, 22, Ui.LINE, 1)
        pill.setPadding(dp(12), 0, dp(12), 0)
        pill.addView(ToolIcon(this, ToolIcon.Kind.SEARCH, Ui.SUB), Ui.lp(dp(18), dp(18)))
        urlField = EditText(this).apply {
            setText("https://www.youtube.com")
            setTextColor(Ui.TEXT); setHintTextColor(Ui.SUB); hint = "Nhập địa chỉ hoặc từ khóa"
            textSize = 15f; maxLines = 1; isSingleLine = true
            background = null
            setPadding(dp(8), 0, 0, 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, id, e ->
                if (id == EditorInfo.IME_ACTION_GO || (e != null && e.keyCode == KeyEvent.KEYCODE_ENTER)) { load(text.toString()); clearFocus(); hideKeyboard(); true } else false
            }
        }
        pill.addView(urlField, Ui.lp(0, dp(44), 1f))
        bar.addView(pill, Ui.lp(0, dp(44), 1f).apply { leftMargin = dp(6); rightMargin = dp(6) })
        bar.addView(Ui.tv(this, "↻", 26f, Ui.TEXT).apply {
            gravity = Gravity.CENTER
            setOnClickListener { web.reload() }
        }, Ui.lp(dp(44), dp(44)))
        root.addView(bar)

        // ---- trang web và khung chọn vùng
        val frame = FrameLayout(this)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(), "DG")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, u: String?) {
                view?.evaluateJavascript(js, null)
                applyHide()
                syncNav(u)
            }
            override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) { syncNav(u) }
        }
        frame.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        box = RegionBox(this)
        box.load(sp.getString("box", null))
        box.onChanged = { sp.edit().putString("box", box.save()).apply() }
        frame.addView(box, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        fallbackPill = Ui.hbox(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.bg(this@WebTranslateActivity, Color.parseColor("#E6201A10"), 18, Color.parseColor("#66FFA040"), 1)
            setPadding(dp(12), dp(7), dp(10), dp(7))
            addView(Ui.tv(this@WebTranslateActivity, "ⓘ Dịch dự phòng", 14f, Color.parseColor("#FFA040"), true))
            addView(Ui.tv(this@WebTranslateActivity, "✕", 15f, Color.parseColor("#FFA040"), true).apply {
                setPadding(dp(10), 0, dp(2), 0)
                setOnClickListener { fallbackPill.visibility = View.GONE }
            })
            visibility = View.GONE
        }
        frame.addView(fallbackPill, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8); rightMargin = dp(10)
        })
        root.addView(frame, Ui.lp(MATCH_PARENT, 0, 1f))

        // ---- bảng dịch ở đáy
        val panel = Ui.vbox(this)
        panel.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Ui.CARD)
            cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
            setStroke(dp(1), Ui.LINE)
        }
        val head = Ui.hbox(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(14), dp(10), dp(14), 0)
        langChip = Ui.tv(this, "", 13f, Ui.TEXT, true).apply {
            background = Ui.bg(this@WebTranslateActivity, Ui.CARD2, 16, Ui.LINE, 1)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { pickLang() }
        }
        head.addView(langChip)
        head.addView(View(this), Ui.lp(0, 1, 1f))
        statusChip = Ui.tv(this, "", 12.5f, Ui.SUB, true)
        head.addView(statusChip)
        panel.addView(head)
        headRow = head
        origView = Ui.tv(this, "", 13f, Ui.SUB).apply { gravity = Gravity.CENTER; visibility = View.GONE }
        textView = Ui.tv(this, "", 20f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }
        val body = Ui.vbox(this)
        body.gravity = Gravity.CENTER
        body.setPadding(dp(16), dp(6), dp(16), dp(6))
        body.addView(origView)
        body.addView(textView)
        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(sv, Ui.lp(MATCH_PARENT, dp(112)))
        bodyScroll = sv
        panel.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1))

        // thanh công cụ
        val tools = Ui.hbox(this)
        tools.gravity = Gravity.CENTER_VERTICAL
        tools.setPadding(dp(10), dp(8), dp(10), dp(10))
        transBtn = FrameLayout(this)
        transIcon = ToolIcon(this, ToolIcon.Kind.TRANSLATE, Ui.TEXT, Ui.CARD)
        transBtn.addView(transIcon, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        transBtn.setOnClickListener { if (running) pause() else start() }
        transBtn.setOnLongClickListener { pickLang(); true }
        tools.addView(transBtn, Ui.lp(dp(52), dp(48)))
        tools.addView(View(this), Ui.lp(0, 1, 1f))
        val sp1 = Ui.hbox(this)
        sp1.gravity = Gravity.CENTER_VERTICAL
        sp1.background = Ui.bg(this, Ui.CARD2, 22)
        sp1.setPadding(dp(12), 0, dp(14), 0)
        srcIcon = ToolIcon(this, ToolIcon.Kind.SCAN, Ui.TEXT)
        sp1.addView(srcIcon, Ui.lp(dp(22), dp(22)))
        srcLabel = Ui.tv(this, "OCR", 17f, Ui.TEXT, true).apply { setPadding(dp(8), 0, 0, 0) }
        sp1.addView(srcLabel)
        sp1.setOnClickListener { showSourceMenu(sp1) }
        srcPill = sp1
        tools.addView(sp1, Ui.lp(WRAP_CONTENT, dp(44)))
        fun icon(kind: ToolIcon.Kind, click: (ToolIcon) -> Unit): ToolIcon {
            val ic = ToolIcon(this, kind, Ui.TEXT)
            val holder = FrameLayout(this)
            holder.addView(ic, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            holder.setOnClickListener { click(ic) }
            tools.addView(holder, Ui.lp(dp(46), dp(48)))
            return ic
        }
        icon(ToolIcon.Kind.CROP) { enterAdjust() }
        icon(ToolIcon.Kind.TEXT_SIZE) { showFontDialog() }
        speakIcon = icon(ToolIcon.Kind.SPEAKER_ON) { p.speak = !p.speak; if (!p.speak) speaker?.stop(); refresh() }
        icon(ToolIcon.Kind.GEAR) { showSettingsSheet() }
        toolsRow = tools

        val adj = Ui.hbox(this)
        adj.gravity = Gravity.CENTER_VERTICAL
        adj.setPadding(dp(16), dp(10), dp(12), dp(12))
        adj.addView(Ui.tv(this, "Kéo khung hồng trùng với chỗ phụ đề", 15f, Ui.SUB), Ui.lp(0, WRAP_CONTENT, 1f))
        adj.addView(Ui.tv(this, "Đặt lại", 16f, Ui.TEXT, true).apply {
            background = Ui.bg(this@WebTranslateActivity, Ui.CARD2, 22, Ui.LINE, 1)
            setPadding(dp(18), dp(11), dp(18), dp(11))
            setOnClickListener { box.reset() }
        }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(8) })
        adj.addView(Ui.tv(this, "Xong", 16f, Color.parseColor("#2A1A08"), true).apply {
            background = Ui.bg(this@WebTranslateActivity, Color.WHITE, 22)
            setPadding(dp(22), dp(11), dp(22), dp(11))
            setOnClickListener { leaveAdjust() }
        }, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(8) })
        adj.visibility = View.GONE
        adjustRow = adj
        panel.addView(tools)
        panel.addView(adj)
        root.addView(panel)
        setContentView(root)

        speaker = Speaker(this, p)
        speaker?.onAiError = { m -> Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
        applyFont()
        applyOrientation()
        refresh()
        setPanel("Bấm nút dịch để bắt đầu", "")
        load(urlField.text.toString())
        // chỉ để smoke test chụp màn hình các bảng của màn này
        when (intent.getStringExtra("demo")) {
            "adjust" -> ui.postDelayed({ enterAdjust() }, 800)
            "menu" -> ui.postDelayed({ showSourceMenu(srcPill) }, 800)
            "settings" -> ui.postDelayed({ showSettingsSheet() }, 800)
            "running" -> ui.postDelayed({
                running = true; everStarted = true; refresh()
                setPanel("Xin chào, đây là bản dịch thử.", "Hello, this is a test.")
                showFallback()
            }, 800)
        }
    }

    // ---------------- giao diện nhỏ ----------------

    /** Màn ngang: bỏ hàng ngôn ngữ và thu bảng dịch lại để trang web còn đủ chỗ. */
    private fun applyOrientation() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        headRow.visibility = if (land) View.GONE else View.VISIBLE
        (bodyScroll.layoutParams as LinearLayout.LayoutParams).height = dp(if (land) 58 else 112)
        bodyScroll.requestLayout()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        applyOrientation()
    }

    private fun circleBtn(label: String, onClick: () -> Unit): TextView = Ui.tv(this, label, 20f, Ui.TEXT, true).apply {
        gravity = Gravity.CENTER
        background = Ui.bg(this@WebTranslateActivity, Ui.CARD2, 22)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun navBtn(label: String, onClick: () -> Unit): TextView = Ui.tv(this, label, 30f, Ui.TEXT).apply {
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(urlField.windowToken, 0)
    }

    private fun syncNav(u: String?) {
        if (!urlField.hasFocus() && !u.isNullOrEmpty()) urlField.setText(u)
        backBtn.alpha = if (web.canGoBack()) 1f else 0.35f
        fwdBtn.alpha = if (web.canGoForward()) 1f else 0.35f
    }

    private fun langShort(): String = if (p.narrate) "Việt" else Prefs.langName(p.lang).removePrefix("Tiếng ")

    private fun refresh() {
        langChip.text = if (p.narrate) "🌐 Thuyết minh · Việt ▾" else "🌐 ${langShort()} → Việt ▾"
        if (running) {
            transBtn.background = Ui.goldBg(this, 16)
            transIcon.set(ToolIcon.Kind.PAUSE, Color.parseColor("#2A1A08"))
        } else {
            transBtn.background = null
            transIcon.set(ToolIcon.Kind.TRANSLATE, Ui.TEXT)
        }
        statusChip.text = if (running) "● Đang dịch" else if (everStarted) "⏸ Tạm dừng" else "Chưa bắt đầu"
        statusChip.setTextColor(if (running) Ui.OK else if (everStarted) Ui.GOLD else Ui.SUB)
        if (source == "ocr") { srcIcon.set(ToolIcon.Kind.SCAN, Ui.TEXT); srcLabel.text = "OCR" }
        else { srcIcon.set(ToolIcon.Kind.CC, Ui.TEXT); srcLabel.text = "CC" }
        speakIcon.set(if (p.speak) ToolIcon.Kind.SPEAKER_ON else ToolIcon.Kind.SPEAKER_OFF, if (p.speak) Ui.GOLD else Ui.SUB)
        box.active = running && source == "ocr"
        box.covering = hideOrig && source == "ocr"
    }

    private fun applyFont() {
        val sz = p.fontSize * p.subScale / 100f
        textView.textSize = sz.coerceIn(12f, 40f)
        origView.textSize = (sz * 0.7f).coerceIn(11f, 28f)
        textView.setTextColor(if (p.style == 1) 0xFFFFEB3B.toInt() else Color.WHITE)
    }

    private fun setPanel(vi: String, orig: String) {
        applyFont()
        val grey = vi.startsWith("Bấm nút dịch") || vi.startsWith("Đang ") || vi.startsWith("Đã tạm") || vi.startsWith("Không ")
        if (grey) textView.setTextColor(Ui.SUB)
        textView.text = vi
        origView.text = orig
        origView.visibility = if (orig.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showFallback() {
        fallbackPill.visibility = View.VISIBLE
        ui.removeCallbacks(hideFallback)
        ui.postDelayed(hideFallback, 8000)
    }

    private val hideFallback = Runnable { fallbackPill.visibility = View.GONE }

    // ---------------- điều khiển dịch ----------------

    private fun ocrLang() = if (p.narrate) "vi" else p.lang

    private fun engine(): Ocr {
        var o = ocr
        if (o == null || ocrFor != ocrLang()) {
            o?.close()
            o = Ocr(p, ocrLang())
            ocr = o
            ocrFor = ocrLang()
        }
        return o
    }

    private fun prepareTranslator(done: (Boolean) -> Unit) {
        translator?.close()
        val t = TextTranslator(this, p, p.lang)
        t.onFallback = { showFallback() }
        translator = t
        setPanel("Đang chuẩn bị bộ dịch...", "")
        t.prepare { ok ->
            if (ok) done(true) else {
                translator = null
                setPanel("Không tải được mô hình dịch. Kiểm tra mạng rồi bấm lại.", "")
                done(false)
            }
        }
    }

    private fun start() {
        if (running) return
        if (!p.narrate && !sp.getBoolean("langAsked", false)) { pickLang(thenStart = true); return }
        if (!p.narrate && translator == null) { prepareTranslator { ok -> if (ok) beginRun() }; return }
        beginRun()
    }

    private fun beginRun() {
        running = true
        everStarted = true
        lastProcessed = ""; lastShown = ""; lastCand = ""; miss = 0; lastThumb = null
        refresh()
        setPanel(if (source == "ocr") "Đang quét chữ trên video..." else "Đang chờ phụ đề của video...", "")
        applyHide()
        if (source == "ocr") scheduleScan(200)
    }

    private fun pause() {
        running = false
        seq++
        ui.removeCallbacks(scanTask)
        ui.removeCallbacks(pendingRun)
        speaker?.stop()
        refresh()
        applyHide()
        setPanel("Đã tạm dừng. Bấm nút dịch để tiếp tục.", "")
    }

    private fun setSource(s: String) {
        if (source == s) return
        val was = running
        if (was) pause()
        source = s
        sp.edit().putString("source", s).apply()
        refresh()
        if (was) start()
    }

    private fun applyHide() {
        if (!::web.isInitialized) return
        val on = hideOrig && running && source == "cc"
        web.evaluateJavascript("window.__dgHide && window.__dgHide($on)", null)
        refresh()
    }

    private fun setLang(code: String) {
        if (code == p.lang) return
        val was = running
        if (was) pause()
        p.lang = code
        translator?.close(); translator = null
        refresh()
        setPanel("Ngôn ngữ trong video: ${Prefs.langName(code)}", "")
        if (was) start()
    }

    private fun pickLang(thenStart: Boolean = false) {
        val codes = Prefs.LANGS.map { it.first }.filter { it != "vi" }
        Ui.pick(this, "Ngôn ngữ trong video", codes.map { Prefs.langLabel(it) }) { i ->
            sp.edit().putBoolean("langAsked", true).apply()
            setLang(codes[i])
            if (thenStart) start()
        }
    }

    // ---- nguồn CC

    private val pendingRun = Runnable { pendingText?.let { process(it) } }

    private fun onSub(t: String) {
        if (!running || source != "cc") return
        if (p.slow) {
            pendingText = t
            ui.removeCallbacks(pendingRun)
            ui.postDelayed(pendingRun, 450)
        } else process(t)
    }

    /** Dịch (hoặc đọc, khi thuyết minh) một câu vừa lấy được từ CC hoặc OCR. */
    private fun process(text: String) {
        if (!running || text == lastProcessed) return
        lastProcessed = text
        if (p.narrate) {
            setPanel(text, "")
            if (p.speak) speaker?.speak(text)
            return
        }
        val t = translator ?: return
        val my = ++seq
        t.translateEx(text) { vi, d ->
            if (my == seq && running) {
                setPanel(vi, if (p.showOrig) text else "")
                if (p.speak) speaker?.speak(vi, false, d)
            }
        }
    }

    // ---- nguồn OCR

    private val scanTask = Runnable { scan() }

    private fun interval(): Long {
        val base = p.scanMs.toLong().coerceIn(100, 3000)
        return if (engine().isCloud) max(base, 1500L) else base
    }

    private fun scheduleScan(delayMs: Long = interval()) {
        ui.removeCallbacks(scanTask)
        if (running && source == "ocr") ui.postDelayed(scanTask, delayMs)
    }

    private fun captureRect(): Rect? {
        val loc = IntArray(2)
        box.getLocationInWindow(loc)
        val r = box.region()
        val inset = dp(7)
        val dw = window.decorView.width
        val dh = window.decorView.height
        val l = max(0, (loc[0] + r.left + inset).toInt())
        val t = max(0, (loc[1] + r.top + inset).toInt())
        val rr = min(dw, (loc[0] + r.right - inset).toInt())
        val bb = min(dh, (loc[1] + r.bottom - inset).toInt())
        if (rr - l < 24 || bb - t < 16) return null
        return Rect(l, t, rr, bb)
    }

    private fun scan() {
        if (!running || source != "ocr" || scanBusy) return
        if (adjusting || !window.decorView.isShown) { scheduleScan(500); return }
        val r = captureRect() ?: run { scheduleScan(1000); return }
        val bmp = try { Bitmap.createBitmap(r.width(), r.height(), Bitmap.Config.ARGB_8888) } catch (_: Throwable) { scheduleScan(2000); return }
        scanBusy = true
        val copy = {
            try {
                PixelCopy.request(window, r, bmp, { res ->
                    if (hideOrig) { box.covering = true }
                    if (res != PixelCopy.SUCCESS) { bmp.recycle(); scanBusy = false; scheduleScan(1000) }
                    else readBitmap(bmp)
                }, ui)
            } catch (_: Throwable) {
                bmp.recycle(); scanBusy = false
                if (hideOrig) box.covering = true
                scheduleScan(2000)
            }
        }
        if (hideOrig) {
            // tạm bỏ lớp che để chụp được chữ gốc, rồi che lại ngay
            box.covering = false
            val ch = Choreographer.getInstance()
            ch.postFrameCallback { ch.postFrameCallback { copy() } }
        } else copy()
    }

    private fun readBitmap(bmp: Bitmap) {
        if (isDestroyed || !running) { bmp.recycle(); scanBusy = false; return }
        val th = Ocr.thumb(bmp)
        if (lastThumb != null && Ocr.same(th, lastThumb)) { bmp.recycle(); scanBusy = false; scheduleScan(); return }
        lastThumb = th
        engine().read(bmp,
            { m ->
                bmp.recycle(); scanBusy = false
                if (m.isNotEmpty()) setPanel(m, "")
                scheduleScan(if (m.isNotEmpty()) 4000 else interval())
            },
            { blocks ->
                bmp.recycle(); scanBusy = false
                onOcr(blocks)
                scheduleScan()
            })
    }

    private fun onOcr(blocks: List<Blk>) {
        if (!running) return
        val text = blocks.joinToString(" ") { it.text.replace(Regex("\\s+"), " ").trim() }.trim()
        if (text.length < 2 || text.none { it.isLetterOrDigit() }) {
            miss++
            if (miss >= 3 && lastShown.isNotEmpty()) {
                lastShown = ""; lastCand = ""; lastProcessed = ""
                setPanel("Đang quét chữ trên video...", "")
            }
            return
        }
        miss = 0
        if (p.slow && text != lastCand) { lastCand = text; return } // "Kỹ": chờ chữ ổn định qua hai lần đọc liên tiếp
        lastCand = text
        if (text == lastShown) return
        lastShown = text
        process(text)
    }

    // ---------------- khung chọn vùng ----------------

    private fun enterAdjust() {
        if (source != "ocr") setSource("ocr")
        adjusting = true
        box.adjusting = true
        toolsRow.visibility = View.GONE
        adjustRow.visibility = View.VISIBLE
    }

    private fun leaveAdjust() {
        adjusting = false
        box.adjusting = false
        toolsRow.visibility = View.VISIBLE
        adjustRow.visibility = View.GONE
        lastThumb = null
        if (!running) start() else scheduleScan(200)
    }

    // ---------------- menu và hộp thoại ----------------

    private fun showSourceMenu(anchor: View) {
        val col = Ui.vbox(this)
        col.background = Ui.bg(this, Color.parseColor("#152019"), 24, Ui.LINE, 1)
        col.setPadding(dp(6), dp(8), dp(6), dp(8))
        var pw: PopupWindow? = null
        fun item(title: String, sub: String?, checked: Boolean, onClick: () -> Unit) {
            val row = Ui.hbox(this)
            row.setPadding(dp(6), dp(11), dp(14), dp(11))
            row.addView(Ui.tv(this, if (checked) "✓" else "", 20f, Ui.GOLD, true).apply { gravity = Gravity.CENTER }, Ui.lp(dp(38), WRAP_CONTENT))
            val t = Ui.vbox(this)
            t.addView(Ui.tv(this, title, 17f, Ui.TEXT))
            if (sub != null) t.addView(Ui.tv(this, sub, 13f, Ui.SUB))
            row.addView(t, Ui.lp(0, WRAP_CONTENT, 1f))
            row.setOnClickListener { pw?.dismiss(); onClick() }
            col.addView(row)
        }
        item("Chữ hiện dần", "Chỉ bật khi phụ đề hiện ra từng chữ", p.slow) { p.slow = !p.slow; lastCand = "" }
        item("Thuyết minh", "Video có sẵn phụ đề tiếng Việt: đọc to, không dịch", p.narrate) { toggleNarrate() }
        item("Ẩn phụ đề gốc trên video", if (source == "ocr") "Che khung bằng nền tối khi quét OCR" else null, hideOrig) {
            hideOrig = !hideOrig
            sp.edit().putBoolean("hide", hideOrig).apply()
            applyHide()
        }
        col.addView(Ui.divider(this), Ui.lp(MATCH_PARENT, 1).apply { leftMargin = dp(14); rightMargin = dp(14); topMargin = dp(4); bottomMargin = dp(4) })
        item("Phụ đề CC của video", null, source == "cc") { setSource("cc") }
        item("Quét chữ trên video (OCR)", null, source == "ocr") { setSource("ocr") }
        val w = dp(310)
        val pop = PopupWindow(col, w, WRAP_CONTENT, true)
        pw = pop
        pop.setBackgroundDrawable(ColorDrawable(0))
        pop.elevation = dp(12).toFloat()
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val xoff = max(-(w - anchor.width), dp(8) - loc[0])
        pop.showAsDropDown(anchor, xoff, -(col.measuredHeight + anchor.height + dp(8)))
    }

    private fun toggleNarrate() {
        val was = running
        if (was) pause()
        p.narrate = !p.narrate
        ocr?.close(); ocr = null
        translator?.close(); translator = null
        lastThumb = null
        refresh()
        if (was) start() else setPanel(if (p.narrate) "Thuyết minh: đọc to phụ đề tiếng Việt, không dịch." else "Đã chuyển sang dịch. Bấm nút dịch để bắt đầu.", "")
    }

    private fun showFontDialog() {
        val a = this
        Ui.sheet(a) { dlg ->
            addView(Ui.tv(a, "Cỡ chữ phụ đề", 18f, Ui.TEXT, true))
            addView(Ui.seekRow(a, "Cỡ chữ", { v -> "$v%" }, 50, 200, p.subScale) { p.subScale = it; applyFont() }, Ui.lpm(a, 10, 8))
            addView(Ui.button(a, "Xong", true) { dlg.dismiss() }, Ui.lpm(a, 8, 0))
        }
    }

    private fun showSettingsSheet() {
        val a = this
        Ui.sheet(a) { dlg ->
            val box = this
            fun fill() {
                box.removeAllViews()
                box.addView(Ui.tv(a, "Cài đặt dịch video", 18f, Ui.TEXT, true))
                box.addView(Ui.tv(a, "Ngôn ngữ trong video", 15f, Ui.TEXT, true), Ui.lpm(a, 14, 0))
                box.addView(Ui.button(a, if (p.narrate) "Thuyết minh (tiếng Việt)" else Prefs.langLabel(p.lang) + "  ›") {
                    dlg.dismiss(); pickLang()
                }, Ui.lpm(a, 6, 0))
                box.addView(Ui.tv(a, "Cách đọc chữ trên hình", 15f, Ui.TEXT, true), Ui.lpm(a, 14, 0))
                box.addView(Ui.segmented(a, listOf("Kỹ", "Nhanh"), if (p.slow) 0 else 1) { p.slow = it == 0; lastCand = ""; fill() }, Ui.lpm(a, 6, 0))
                box.addView(Ui.tv(a, "Kỹ: chờ chữ hiện đủ rồi mới dịch. Nhanh: dịch ngay khi thấy chữ.", 12.5f, Ui.SUB), Ui.lpm(a, 6, 0))
                val rates = listOf(3, 4, 6, 8)
                val sel = rates.indexOfFirst { abs(1000 / it - p.scanMs) <= 20 }
                box.addView(Ui.tv(a, "Mỗi giây đọc phụ đề mấy lần (khi quét chữ)", 15f, Ui.TEXT, true), Ui.lpm(a, 14, 0))
                box.addView(Ui.segmented(a, rates.map { it.toString() }, sel) { p.scanMs = 1000 / rates[it]; fill() }, Ui.lpm(a, 6, 0))
                box.addView(Ui.tv(a, "Đọc nhiều đỡ sót câu nhưng máy nóng và hao pin hơn. Chữ đọc bằng Gemini luôn tối thiểu 1,5 giây một lần.", 12.5f, Ui.SUB), Ui.lpm(a, 6, 0))
                box.addView(Ui.switchRow(a, "Hiện câu gốc", "Chữ nhỏ phía trên bản dịch.", p.showOrig) { p.showOrig = it }, Ui.lpm(a, 10, 0))
                box.addView(Ui.button(a, "Cách dịch (AI hoặc trên máy)  ›") { dlg.dismiss(); startActivity(Intent(a, TranslateActivity::class.java)) }, Ui.lpm(a, 14, 0))
                box.addView(Ui.button(a, "Giọng đọc và phụ đề  ›") { dlg.dismiss(); startActivity(Intent(a, SettingsActivity::class.java)) }, Ui.lpm(a, 8, 0))
            }
            fill()
        }
    }

    // ---------------- vòng đời ----------------

    private fun load(input: String) {
        val s = input.trim()
        if (s.isEmpty()) return
        val u = when {
            s.contains("://") -> s
            s.contains('.') && !s.contains(' ') -> "https://$s"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(s, "UTF-8")
        }
        web.loadUrl(u)
    }

    override fun onResume() {
        super.onResume()
        if (running && source == "ocr") scheduleScan(500)
    }

    override fun onPause() {
        ui.removeCallbacks(scanTask)
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            adjusting -> leaveAdjust()
            ::web.isInitialized && web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        try { web.destroy() } catch (_: Exception) {}
        translator?.close()
        ocr?.close()
        speaker?.shutdown()
        super.onDestroy()
    }
}
