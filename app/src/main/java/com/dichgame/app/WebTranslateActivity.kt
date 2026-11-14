package com.dichgame.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Dịch video trên web: mở trang video trong app, đọc phụ đề của trình phát (thẻ track hoặc khung phụ đề trên trang)
 * rồi hiện bản dịch tiếng Việt đè lên đáy video, có thể đọc to.
 */
class WebTranslateActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var web: WebView
    private lateinit var overlay: TextView
    private lateinit var toggle: TextView
    private var translator: TextTranslator? = null
    private var speaker: Speaker? = null
    private var enabled = true
    private var seq = 0
    private var ocr: Ocr? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastSubtitleAt = 0L
    private var lastOcrAt = 0L
    private var ocrBusy = false
    private var destroyed = false
    private var ocrCandidate = ""
    private var ocrStableFrames = 0
    private var lastOcrDelivered = ""
    private var lastOcrTextAt = 0L
    private var speechRecognizer: SpeechRecognizer? = null
    private var micEnabled = false
    private var micButton: TextView? = null
    private var lastAsrDelivered = ""
    private var lastAsrDeliveredAt = 0L
    private val speechRestart = Runnable { startSpeechRecognition() }
    private val ocrLoop = object : Runnable {
        override fun run() {
            if (destroyed || !::web.isInitialized) return
            val now = System.currentTimeMillis()
            // Chỉ quét ảnh khi phụ đề HTML không xuất hiện; tránh OCR đè lên phụ đề thật.
            val reader = ocr
            // OCR local peut être rapide; OCR cloud est ralenti pour limiter latence et quota Gemini.
            val scanInterval = if (reader?.isCloud == true) 1800L else 500L
            if (enabled && !ocrBusy && now - lastSubtitleAt > 1400L && now - lastOcrAt >= scanInterval) {
                captureSubtitleRegion()
            }
            main.postDelayed(this, scanInterval)
        }
    }

    private val js = """
(function(){
  if (window.__dg) return; window.__dg = true;
  var last = '';
  function send(t){
    t = (t || '').replace(/<[^>]+>/g,' ').replace(/\\s+/g,' ').trim();
    // Chỉ gửi khi nội dung đổi; khi caption biến mất thì mở khóa để câu giống hệt ở cảnh sau được gửi lại.
    if (!t) { last = ''; return; }
    if (t.length < 400 && t !== last) {
      last = t;
      DG.sub(t);
    }
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
  setInterval(function(){ try { tracks(); dom(); } catch (e) {} }, 500);
})();
"""

    inner class Bridge {
        @JavascriptInterface
        fun sub(t: String) { runOnUiThread { onSub(t) } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = Ui.vbox(this)
        root.setBackgroundColor(Ui.BG)

        val bar = Ui.hbox(this)
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 6))
        val back = Ui.tv(this, "←", 26f)
        back.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0)
        back.setOnClickListener { finish() }
        bar.addView(back)
        bar.addView(Ui.tv(this, "Dịch video trên web", 18f, Ui.TEXT, true), Ui.lp(0, WRAP_CONTENT, 1f))
        toggle = Ui.tv(this, "", 13f, Color.parseColor("#2A1A08"), true)
        toggle.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7))
        toggle.setOnClickListener { enabled = !enabled; refreshToggle(); if (!enabled) { overlay.visibility = android.view.View.GONE; try { speechRecognizer?.cancel() } catch (_: Exception) {} } else if (micEnabled) { scheduleSpeechRestart(150L) } }
        micButton = Ui.tv(this, "Nghe mic: TẮT", 12f, Color.parseColor("#2A1A08"), true).also { button ->
            button.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7))
            button.setOnClickListener { toggleMicrophoneRecognition() }
        }
        bar.addView(micButton)
        bar.addView(toggle)
        root.addView(bar)

        val urlRow = Ui.hbox(this)
        urlRow.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8))
        val url = Ui.field(this, "Nhập địa chỉ trang video hoặc từ khóa", "https://www.youtube.com")
        url.imeOptions = EditorInfo.IME_ACTION_GO
        urlRow.addView(url, Ui.lp(0, WRAP_CONTENT, 1f))
        val go = Ui.button(this, "Đi", true) { load(url.text.toString()) }
        urlRow.addView(go, Ui.lp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = Ui.dp(this@WebTranslateActivity, 8) })
        url.setOnEditorActionListener { _, id, e ->
            if (id == EditorInfo.IME_ACTION_GO || (e != null && e.keyCode == KeyEvent.KEYCODE_ENTER)) { load(url.text.toString()); true } else false
        }
        root.addView(urlRow)

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
            }
        }
        frame.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        overlay = TextView(this)
        overlay.gravity = Gravity.CENTER
        overlay.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8))
        overlay.visibility = android.view.View.GONE
        frame.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = Ui.dp(this@WebTranslateActivity, 56)
        })
        root.addView(frame, Ui.lp(MATCH_PARENT, 0, 1f))
        setContentView(root)

        refreshToggle()
        speaker = Speaker(this, p)
        // OCR dự phòng cho phụ đề được đốt trực tiếp vào hình video (không có track HTML).
        ocr = Ocr(p, p.lang)
        main.postDelayed(ocrLoop, 1800L)
        speaker?.onAiError = { m -> android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show() }
        if (!p.narrate) {
            val t = TextTranslator(this, p, p.lang)
            translator = t
            show("Đang chuẩn bị bộ dịch...", "")
            t.prepare { ok ->
                if (ok) overlay.visibility = android.view.View.GONE
                else show("Không tải được mô hình dịch. Kiểm tra mạng rồi mở lại.", "")
            }
        }
        load(url.text.toString())
    }


    /**
     * ASR dự phòng qua micro, chỉ chạy khi người dùng chủ động bật.
     * Đây là nghe âm thanh môi trường/loa bằng micro, KHÔNG phải thu PCM nội bộ từ WebView.
     */
    private fun toggleMicrophoneRecognition() {
        if (!micEnabled && !enabled) {
            android.widget.Toast.makeText(this, "Hãy bật Dịch trước khi bật nghe micro.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (micEnabled) {
            stopMicrophoneRecognition()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            android.widget.Toast.makeText(this, "Máy chưa có dịch vụ nhận dạng giọng nói.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7401)
            return
        }
        micEnabled = true
        micButton?.text = "Nghe mic: BẬT"
        android.widget.Toast.makeText(this, "Đang nghe qua micro. Hãy phát âm thanh qua loa ngoài để micro nghe được.", android.widget.Toast.LENGTH_LONG).show()
        createSpeechRecognizer()
        startSpeechRecognition()
    }

    private fun createSpeechRecognizer() {
        if (speechRecognizer != null) return
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                // Không dịch kết quả tạm: chờ kết quả cuối để tránh hiện chữ đoán rồi sửa liên tục.
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                val now = System.currentTimeMillis()
                // Cho phép cùng một câu ngắn xuất hiện lại trong video; chỉ chặn kết quả lặp sát nhau.
                val duplicateImmediately = text.equals(lastAsrDelivered, ignoreCase = true) &&
                    now - lastAsrDeliveredAt < 1200L
                if (text.isNotBlank() && !duplicateImmediately && !destroyed && micEnabled) {
                    lastAsrDelivered = text
                    lastAsrDeliveredAt = now
                    runOnUiThread { if (micEnabled && enabled) onSub(text) }
                }
                if (micEnabled && !destroyed) scheduleSpeechRestart(250L)
            }
            override fun onError(error: Int) {
                if (!micEnabled || destroyed) return
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stopMicrophoneRecognition()
                    android.widget.Toast.makeText(this@WebTranslateActivity, "Chưa cấp quyền micro.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    // Các lỗi timeout/no-match là bình thường khi có khoảng lặng; tự nghe lượt kế tiếp.
                    scheduleSpeechRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900L else 500L)
                }
            }
        })
    }

    /** Chỉ giữ một lịch khởi động lại để tránh nhiều callback cùng gọi startListening(). */
    private fun scheduleSpeechRestart(delayMs: Long) {
        if (!micEnabled || destroyed) return
        main.removeCallbacks(speechRestart)
        main.postDelayed(speechRestart, delayMs)
    }

    private fun startSpeechRecognition() {
        main.removeCallbacks(speechRestart)
        if (!micEnabled || destroyed || !enabled) return
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.forLanguageTag(p.lang).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, java.util.Locale.forLanguageTag(p.lang).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 450L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 250L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 250L)
        }
        try { recognizer.startListening(intent) }
        catch (_: Exception) { scheduleSpeechRestart(900L) }
    }

    private fun stopMicrophoneRecognition() {
        micEnabled = false
        main.removeCallbacks(speechRestart)
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        micButton?.text = "Nghe mic: TẮT"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7401) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleMicrophoneRecognition()
            else android.widget.Toast.makeText(this, "Cần quyền micro để nhận dạng lời thoại.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshToggle() {
        toggle.text = if (enabled) "Dịch: BẬT" else "Dịch: TẮT"
        toggle.background = if (enabled) Ui.goldBg(this, 18) else Ui.bg(this, Color.parseColor("#9A9AA0"), 18)
    }

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

    private fun onSub(text: String) {
        if (!enabled) return
        lastSubtitleAt = System.currentTimeMillis()
        // Phụ đề thật vừa xuất hiện: xóa trạng thái OCR để không phát lại câu cũ.
        ocrCandidate = ""
        ocrStableFrames = 0
        if (p.narrate) {
            show(text, "")
            speaker?.speak(text)
            return
        }
        val t = translator ?: return
        val my = ++seq
        t.translateEx(text) { vi, d ->
            if (my == seq && enabled) {
                show(vi, if (p.showOrig) text else "")
                if (p.speak) speaker?.speak(vi, false, d)
            }
        }
    }

    /**
     * Chụp vùng 45% phía dưới WebView để tìm phụ đề đã được ghép cứng vào khung hình.
     * Đây là lớp dự phòng; ưu tiên track/caption HTML vì chúng giữ được dấu câu và thời điểm tốt hơn.
     */
    private fun captureSubtitleRegion() {
        if (web.width <= 0 || web.height <= 0) return
        lastOcrAt = System.currentTimeMillis()
        ocrBusy = true
        var full: Bitmap? = null
        var crop: Bitmap? = null
        try {
            val scale = minOf(1f, 1280f / maxOf(web.width, web.height).toFloat())
            val w = (web.width * scale).toInt().coerceAtLeast(1)
            val h = (web.height * scale).toInt().coerceAtLeast(1)
            val frameBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            full = frameBitmap
            val canvas = Canvas(frameBitmap)
            canvas.scale(scale, scale)
            web.draw(canvas)
            val top = (h * 0.55f).toInt().coerceIn(0, h - 1)
            crop = Bitmap.createBitmap(frameBitmap, 0, top, w, h - top)
            full.recycle()
            full = null
            val shot = crop ?: throw IllegalStateException("Không tạo được ảnh OCR")
            val reader = ocr ?: throw IllegalStateException("OCR chưa sẵn sàng")
            reader.read(shot, { _ ->
                shot.recycle()
                ocrBusy = false
            }) { blocks ->
                try {
                    if (!destroyed && enabled && System.currentTimeMillis() - lastSubtitleAt > 1800L) {
                        val recognized = blocks.joinToString(" ") { it.text.trim() }
                            .replace(Regex("\\s+"), " ").trim()
                        // Chỉ chấp nhận OCR khi cùng một câu xuất hiện ổn định ở ít nhất 2 lần quét.
                        // Chống nhiễu và tránh gửi đi dịch lại cùng một câu ở mọi khung hình.
                        if (recognized.isNotEmpty()) {
                            if (recognized == ocrCandidate) ocrStableFrames++
                            else { ocrCandidate = recognized; ocrStableFrames = 1 }
                            // Một ký tự rất dễ là nhiễu; cần 3 khung ổn định. Hai ký tự cần ít nhất 2.
                            val requiredFrames = if (recognized.length == 1) 3 else 2
                            val nowText = System.currentTimeMillis()
                            val sameCueReturned = recognized == lastOcrDelivered && nowText - lastOcrTextAt > 1800L
                            if (ocrStableFrames >= requiredFrames && (recognized != lastOcrDelivered || sameCueReturned)) {
                                lastOcrDelivered = recognized
                                lastOcrTextAt = nowText
                                onSub(recognized)
                            } else if (recognized == lastOcrDelivered) {
                                lastOcrTextAt = nowText
                            }
                        } else {
                            ocrCandidate = ""
                            ocrStableFrames = 0
                        }
                    }
                } finally {
                    shot.recycle()
                    ocrBusy = false
                }
            }
            crop = null
        } catch (_: Exception) {
            try { full?.recycle() } catch (_: Exception) {}
            try { crop?.recycle() } catch (_: Exception) {}
            ocrBusy = false
        }
    }

    private fun show(vi: String, orig: String) {
        val fg = when (p.style) { 1 -> 0xFFFFEB3B.toInt(); 2 -> Color.BLACK; else -> Color.WHITE }
        val bgc = when (p.style) { 2 -> 0xE6FFFFFF.toInt(); 3 -> 0; else -> 0xB3000000.toInt() }
        overlay.setTextColor(fg)
        overlay.setBackgroundColor(bgc)
        overlay.setShadowLayer(6f, 0f, 0f, Color.BLACK)
        overlay.textSize = p.fontSize * p.subScale / 100f
        overlay.text = if (orig.isEmpty()) vi else "$orig\n$vi"
        overlay.visibility = android.view.View.VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        destroyed = true
        stopMicrophoneRecognition()
        main.removeCallbacks(ocrLoop)
        try { web.destroy() } catch (_: Exception) {}
        ocr?.close()
        translator?.close()
        speaker?.shutdown()
        super.onDestroy()
    }
}
