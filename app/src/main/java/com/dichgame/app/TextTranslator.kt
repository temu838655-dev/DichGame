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

    /** Nếu đặt, mỗi lần dịch AI lỗi và phải dùng dịch trên máy thì gọi hàm này (thay vì hiện thông báo một lần). */
    var onFallback: ((String) -> Unit)? = null

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

    /**
     * Kết quả một lần dịch. [uncertain]: bản dịch đã qua kiểm tra nhưng còn nghi ngờ (mất số, đổi tên, dài ngắn bất thường)
     * hoặc dịch dự phòng cũng không đạt; giao diện đánh dấu "(?)" thay vì khẳng định. [via]: goi (gói Việt hoá), cache, ai, may.
     */
    class Tr(val text: String, val delivery: Delivery?, val uncertain: Boolean, val via: String)

    /** Các câu đã dịch xong và đạt kiểm tra: gặp lại (khung hình lặp, nhân vật nói lại) thì dùng ngay, không gọi AI lần nữa. */
    private val cache = object : LinkedHashMap<String, Tr>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Tr>?): Boolean = size > 160
    }

    fun translate(text: String, done: (String) -> Unit) = translateEx(text) { vi, _ -> done(vi) }

    fun translateEx(text: String, done: (String, Delivery?) -> Unit) = translateFull(text, null) { done(it.text, it.delivery) }

    private fun guardNames(terms: TermMap, cast: List<Character>): List<Pair<String, String>> =
        terms.all + cast.flatMap { c -> c.names.map { it to it } }

    /**
     * Dịch một câu thoại, kèm tên người nói nếu biết. Thứ tự: (0) bộ nhớ đệm các câu vừa dịch; (1) bộ nhớ câu thoại Việt hoá chuẩn;
     * (2) AI nếu đã bật, kèm thuật ngữ, thẻ nhân vật, người nói và chỉ dẫn cảm xúc; (3) dịch trên máy, vẫn ép đúng thuật ngữ.
     * Mọi bản dịch AI đều qua [TranslationGuard]: bị loại thì hỏi lại AI một lần với lời nhắc nghiêm hơn, vẫn hỏng thì dùng dịch trên máy.
     */
    fun translateFull(text: String, speaker: String?, done: (Tr) -> Unit) {
        val st = SessionStats.live
        val key = (speaker?.lowercase() ?: "") + "|" + OcrGuard.key(text)
        val hist = history.takeLast(p.ctxCount)
        history.add(if (speaker != null) "$speaker: $text" else text)
        while (history.size > 10) history.removeAt(0)
        // gói Việt hoá (file đã nhập, bản người dùng sửa) đứng trước mọi thứ, kể cả bộ nhớ đệm: bản vừa sửa phải có hiệu lực ngay
        PackStore.memory(ctx, p).lookupEx(text)?.let { h ->
            st.inc("dich_goi"); if (h.fuzzy) st.inc("dich_goi_gan")
            done(Tr(h.vi, null, false, "goi")); return
        }
        cache[key]?.let { st.inc("dich_cache"); done(it); return }
        val terms = PackStore.terms(p)
        val cast = PackStore.cast(p)
        val names = guardNames(terms, cast)
        val pace = (Cast.byName(speaker, cast) ?: Cast.speakerOf(text, cast))?.pace
        fun finish(t: Tr) {
            if (t.uncertain) st.inc("dich_nghi") else cache[key] = t
            done(t)
        }
        if (!Ai.enabled(p)) { translateLocal(text, names, pace, ::finish); return }
        val emo = p.emoTr
        Thread {
            var chosen: Tr? = null
            var strict: String? = null
            for (attempt in 0..1) {
                val prompt = Ai.prompt(p, srcName(), p.game, hist, text, terms, cast, emo, speaker, strict)
                var raw: String? = null
                try {
                    raw = Ai.translate(p, prompt)
                } catch (e: AiException) {
                    warn("Dịch AI lỗi ${e.code}, dùng dịch trên máy.")
                } catch (e: Exception) {
                    warn("Dịch AI lỗi, dùng dịch trên máy.")
                }
                if (raw.isNullOrEmpty()) break
                val parsed = if (emo) Emo.parse(raw) else Emo.Parsed(raw, null)
                val fixed = terms.fixOutput(TranslationGuard.clean(parsed.text, text))
                val v = TranslationGuard.check(text, fixed, names)
                if (v.level != TranslationGuard.Level.REJECT) {
                    val d = if (emo) parsed.delivery ?: Emo.detect(text, fixed, pace) else null
                    chosen = Tr(fixed, d, v.level == TranslationGuard.Level.SUSPECT, "ai")
                    st.inc("dich_ai")
                    break
                }
                st.inc("dich_loai")
                strict = v.reasons.joinToString("; ")
            }
            val res = chosen
            main.post {
                if (closed) return@post
                if (res != null) finish(res) else translateLocal(text, names, pace, ::finish)
            }
        }.start()
    }

    private fun deliveryOf(text: String, vi: String, pace: Pace?): Delivery? = if (p.emoTr) Emo.detect(text, vi, pace) else null

    private fun translateLocal(text: String, names: List<Pair<String, String>>, pace: Pace?, done: (Tr) -> Unit) {
        val l = local
        if (l == null) { done(Tr(text, null, false, "may")); return }
        val terms = PackStore.terms(p)
        val prot = terms.protect(text)
        fun ok(r: String) {
            SessionStats.live.inc("dich_may")
            // dịch trên máy vốn kém hơn AI nên chỉ coi là "chưa chắc" khi hỏng rõ ràng (rỗng, chưa dịch, còn chữ gốc)
            val bad = TranslationGuard.check(text, r, names).level == TranslationGuard.Level.REJECT
            done(Tr(r, deliveryOf(text, r, pace), bad, "may"))
        }
        l.translate(prot.text)
            .addOnSuccessListener { out ->
                val (r, lost) = terms.restore(out, prot)
                if (prot.tokens.isEmpty() || lost == 0) ok(r)
                else {
                    // bộ dịch làm mất mã giữ chỗ: dịch lại nguyên câu rồi sửa tên theo từ điển
                    l.translate(text)
                        .addOnSuccessListener { o2 -> ok(terms.fixOutput(o2)) }
                        .addOnFailureListener { done(Tr(text, null, true, "may")) }
                }
            }
            .addOnFailureListener { done(Tr(text, null, true, "may")) }
    }

    private fun warn(m: String) {
        val cb = onFallback
        if (cb != null) { main.post { if (!closed) cb(m) }; return }
        if (warned) return
        warned = true
        main.post { Toast.makeText(ctx.applicationContext, m, Toast.LENGTH_LONG).show() }
    }

    fun close() {
        closed = true
        try { local?.close() } catch (_: Exception) {}
    }
}
