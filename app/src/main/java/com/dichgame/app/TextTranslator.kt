package com.dichgame.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Dịch một đoạn chữ sang tiếng Việt: dùng AI (CloudAPI / Gemini) nếu đã nhập khóa, lỗi thì quay về dịch trên máy (ML Kit).
 * Nhớ vài câu trước để AI hiểu mạch truyện. Gọi translate() từ luồng chính, kết quả trả về luồng chính.
 */
class TextTranslator(private val ctx: Context, private val p: Prefs, private val lang: String) {
    private val main = Handler(Looper.getMainLooper())
    private val history = ArrayList<String>()
    private var warned = false
    @Volatile private var closed = false

    private val local: Translator? = if (lang == "vi") null else Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(
                when (lang) {
                    "ja" -> TranslateLanguage.JAPANESE
                    "ko" -> TranslateLanguage.KOREAN
                    "zh" -> TranslateLanguage.CHINESE
                    "fr" -> TranslateLanguage.FRENCH
                    "de" -> TranslateLanguage.GERMAN
                    "es" -> TranslateLanguage.SPANISH
                    "pt" -> TranslateLanguage.PORTUGUESE
                    "it" -> TranslateLanguage.ITALIAN
                    "id" -> TranslateLanguage.INDONESIAN
                    "nl" -> TranslateLanguage.DUTCH
                    "pl" -> TranslateLanguage.POLISH
                    "tr" -> TranslateLanguage.TURKISH
                    "hi" -> TranslateLanguage.HINDI
                    "sv" -> TranslateLanguage.SWEDISH
                    "cs" -> TranslateLanguage.CZECH
                    "ro" -> TranslateLanguage.ROMANIAN
                    "hu" -> TranslateLanguage.HUNGARIAN
                    "da" -> TranslateLanguage.DANISH
                    "fi" -> TranslateLanguage.FINNISH
                    "no" -> TranslateLanguage.NORWEGIAN
                    "ms" -> TranslateLanguage.MALAY
                    "ru" -> TranslateLanguage.RUSSIAN
                    "uk" -> TranslateLanguage.UKRAINIAN
                    "th" -> TranslateLanguage.THAI
                    "ar" -> TranslateLanguage.ARABIC
                    "el" -> TranslateLanguage.GREEK
                    "he" -> TranslateLanguage.HEBREW
                    else -> TranslateLanguage.ENGLISH
                }
            )
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build()
    )

    private fun srcName() = Prefs.srcNameOf(lang)

    /** Tải mô hình dịch trên máy (lần đầu cần mạng). */
    fun prepare(onDone: (Boolean) -> Unit) {
        val l = local
        if (l == null) { onDone(true); return }
        l.downloadModelIfNeeded()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun translate(text: String, done: (String) -> Unit) {
        val hist = history.takeLast(p.ctxCount)
        history.add(text)
        while (history.size > 10) history.removeAt(0)
        if (!Ai.enabled(p)) { translateLocal(text, done); return }
        val prompt = Ai.prompt(p, srcName(), p.game, hist, text)
        Thread {
            var out: String? = null
            try {
                out = Ai.translate(p, prompt)
            } catch (e: AiException) {
                warn("Dịch AI lỗi ${e.code}, dùng dịch trên máy.")
            } catch (e: Exception) {
                warn("Dịch AI lỗi, dùng dịch trên máy.")
            }
            val res = out
            main.post {
                if (!closed) {
                    if (res.isNullOrEmpty()) translateLocal(text, done) else done(res)
                }
            }
        }.start()
    }

    private fun translateLocal(text: String, done: (String) -> Unit) {
        val l = local
        if (l == null) { done(text); return }
        l.translate(text)
            .addOnSuccessListener { done(it) }
            .addOnFailureListener { done(text) }
    }

    private fun warn(m: String) {
        if (warned) return
        warned = true
        main.post { Toast.makeText(ctx.applicationContext, m, Toast.LENGTH_LONG).show() }
    }

    fun close() {
        closed = true
        try { local?.close() } catch (_: Exception) {}
    }
}
