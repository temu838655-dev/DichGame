package com.dichgame.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
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
            .setSourceLanguage(mlCode(lang))
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build()
    )

    companion object {
        /** Mã ngôn ngữ của ML Kit cho ngôn ngữ trong game. */
        fun mlCode(lang: String): String = when (lang) {
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

        /** Hỏi xem mô hình dịch (ngôn ngữ trong game và tiếng Việt) đã tải về máy chưa. */
        fun isDownloaded(lang: String, cb: (Boolean) -> Unit) {
            val m = RemoteModelManager.getInstance()
            m.isModelDownloaded(TranslateRemoteModel.Builder(mlCode(lang)).build())
                .addOnSuccessListener { a ->
                    m.isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build())
                        .addOnSuccessListener { b -> cb(a && b) }
                        .addOnFailureListener { cb(false) }
                }
                .addOnFailureListener { cb(false) }
        }
    }

    private fun srcName() = Prefs.srcNameOf(lang)

    /** Tải mô hình dịch trên máy (lần đầu cần mạng). */
    fun prepare(onDone: (Boolean) -> Unit) {
        val l = local
        if (l == null) { onDone(true); return }
        l.downloadModelIfNeeded()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun translate(text: String, done: (String) -> Unit) = translateEx(text) { vi, _ -> done(vi) }

    /**
     * Dịch một đoạn chữ. Thứ tự: (1) bộ nhớ câu thoại Việt hoá chuẩn, khớp thì dùng luôn; (2) AI nếu đã bật,
     * kèm thuật ngữ, thẻ nhân vật và chỉ dẫn cảm xúc; (3) dịch trên máy, vẫn ép đúng thuật ngữ.
     * [done] nhận bản dịch và cách đọc (cảm xúc, nhịp) nếu có.
     */
    fun translateEx(text: String, done: (String, Delivery?) -> Unit) {
        val hist = history.takeLast(p.ctxCount)
        history.add(text)
        while (history.size > 10) history.removeAt(0)
        PackStore.memory(ctx, p).lookup(text)?.let { done(it, null); return }
        if (!Ai.enabled(p)) { translateLocal(text, done); return }
        val terms = PackStore.terms(p)
        val cast = PackStore.cast(p)
        val emo = p.emoTr
        val prompt = Ai.prompt(p, srcName(), p.game, hist, text, terms, cast, emo)
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
                    if (res.isNullOrEmpty()) translateLocal(text, done)
                    else {
                        val parsed = if (emo) Emo.parse(res) else Emo.Parsed(res, null)
                        val vi = terms.fixOutput(parsed.text)
                        done(vi, if (emo) parsed.delivery ?: Emo.detect(text, vi, Cast.speakerOf(text, cast)?.pace) else null)
                    }
                }
            }
        }.start()
    }

    private fun deliveryOf(text: String, vi: String): Delivery? =
        if (p.emoTr) Emo.detect(text, vi, Cast.speakerOf(text, PackStore.cast(p))?.pace) else null

    private fun translateLocal(text: String, done: (String, Delivery?) -> Unit) {
        val l = local
        if (l == null) { done(text, null); return }
        val terms = PackStore.terms(p)
        val prot = terms.protect(text)
        l.translate(prot.text)
            .addOnSuccessListener { out ->
                val (r, lost) = terms.restore(out, prot)
                if (prot.tokens.isEmpty() || lost == 0) done(r, deliveryOf(text, r))
                else {
                    // bộ dịch làm mất mã giữ chỗ: dịch lại nguyên câu rồi sửa tên theo từ điển
                    l.translate(text)
                        .addOnSuccessListener { o2 -> val f = terms.fixOutput(o2); done(f, deliveryOf(text, f)) }
                        .addOnFailureListener { done(text, null) }
                }
            }
            .addOnFailureListener { done(text, null) }
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
