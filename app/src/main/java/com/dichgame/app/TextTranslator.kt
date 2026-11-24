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
    // Một instance có thể sống qua thao tác chọn game ở Kho game. Không cho cache
    // hoặc ngữ cảnh của game cũ đi vào lời nhắc của game mới.
    private var activeGameContext = ""
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
    class Tr(val text: String, val delivery: Delivery?, val uncertain: Boolean, val via: String) {
        val caption get() = if (uncertain) "$text (?)" else text
    }

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
    fun translateFull(text: String, speaker: String?, done: (Tr) -> Unit) = translateFull(text, speaker, false, done)

    /**
     * [ui] = true: chữ giao diện game (menu, vật phẩm, kỹ năng...), không phải lời thoại: lời nhắc riêng, không ghi vào mạch thoại,
     * không có người nói hay cảm xúc. Gói Việt hoá, bộ nhớ đệm, thuật ngữ và kiểm tra bản dịch vẫn áp dụng như cũ.
     */
    fun translateFull(text: String, speaker: String?, ui: Boolean, done: (Tr) -> Unit) {
        val st = SessionStats.live
        val profile = p.profile
        val game = PackStore.gameName(p)
        val gameDescription = p.game
        val context = profile + "\u0000" + gameDescription
        if (activeGameContext != context) {
            activeGameContext = context
            history.clear()
            cache.clear()
        }
        // Một câu dịch đang chờ Gemini khi người chơi đổi game sẽ bị loại,
        // không hiện phụ đề cũ và không lưu vào bộ nhớ của game mới.
        fun stillCurrent(): Boolean = !closed && profile == p.profile && gameDescription == p.game
        fun emit(result: Tr) { if (stillCurrent()) done(result) }
        val key = context + "\u0000" +
            (if (ui) "ui|" else "") + (speaker?.lowercase() ?: "") + "|" + OcrGuard.key(text)
        val hist = if (ui) emptyList() else history.takeLast(p.ctxCount)
        if (!ui) {
            history.add(if (speaker != null) "$speaker: $text" else text)
            while (history.size > 10) history.removeAt(0)
        }
        // gói Việt hoá (file đã nhập, bản người dùng sửa) đứng trước mọi thứ, kể cả bộ nhớ đệm: bản vừa sửa phải có hiệu lực ngay
        val packHit = PackStore.memory(ctx, p).lookupEx(text)
        // Bản Việt hóa khớp đúng luôn đứng đầu. Với kết quả khớp mờ,
        // ưu tiên bộ nhớ AI khớp chính xác của chính game để tránh câu tương tự bị lẫn.
        if (packHit != null && !packHit.fuzzy) {
            st.inc("dich_goi")
            emit(Tr(packHit.vi, null, false, "goi")); return
        }
        // Gói Việt hóa do người dùng nhập có ưu tiên cao nhất; tiếp theo là bản dịch đã
        // lưu lâu dài trên máy. Khớp đúng nguồn + ngôn ngữ + người nói, không khớp mờ.
        if (p.learnTranslations) {
            LearnedTranslations.lookup(ctx, profile, game, lang, speaker, ui, text)?.takeIf {
                it.manual || TranslationGuard.check(text, it.vi, sourceLang = lang).ok
            }?.let { saved ->
                st.inc("dich_nho")
                val cast = PackStore.cast(p)
                val pace = if (ui) null else
                    (Cast.byName(speaker, cast) ?: Cast.speakerOf(text, cast))?.pace
                val delivery = if (p.emoTr && !ui) Emo.detect(text, saved.vi, pace) else null
                emit(Tr(saved.vi, delivery, false, "nho"))
                return
            }
        }
        if (packHit != null) {
            st.inc("dich_goi"); st.inc("dich_goi_gan")
            emit(Tr(packHit.vi, null, false, "goi")); return
        }
        cache[key]?.let { st.inc("dich_cache"); emit(it); return }
        val terms = PackStore.terms(p)
        val cast = PackStore.cast(p)
        val names = guardNames(terms, cast)
        val pace = if (ui) null else (Cast.byName(speaker, cast) ?: Cast.speakerOf(text, cast))?.pace
        val aiOn = Ai.enabled(p)
        fun finish(t: Tr) {
            if (!stillCurrent()) return
            // Chỉ tự lưu bản Gemini/CloudAPI chắc chắn đạt TranslationGuard.OK.
            // Không ghi bản dịch máy dự phòng hoặc bản dịch AI bị nghi ngờ.
            val saved = p.learnTranslations && t.via == "ai" && !t.uncertain &&
                LearnedTranslationRules.canRemember(text, t.text)
            if (saved) LearnedTranslations.remember(ctx, profile, game, lang, speaker, ui, text, t.text)
            if (t.uncertain) st.inc("dich_nghi")
            // Câu đã lưu lâu dài không giữ thêm trong cache: khi người dùng xóa bộ nhớ
            // sẽ không bị trả lại bản cũ từ cache trong cùng phiên chơi.
            else if (!saved && (t.via != "may" || !aiOn)) cache[key] = t
            emit(t)
        }
        if (!aiOn) { translateLocal(text, names, pace, ::finish); return }
        val emo = p.emoTr && !ui
        Thread {
            var chosen: Tr? = null
            var strict: String? = null
            val cool = Ai.coolingMs(p)
            if (cool > 0) {
                // vừa hết lượt: dịch máy ngay cho câu này, khỏi chờ AI vô ích; hết thời gian nghỉ sẽ thử AI lại
                st.inc(AiRetry.statKey(AiRetry.Kind.QUOTA))
                warn(AiRetry.explain(AiRetry.Kind.QUOTA, 429, cool))
            } else for (attempt in 0..1) {
                val prompt = Ai.prompt(p, srcName(), gameDescription, hist, text, terms, if (ui) emptyList() else cast, emo, speaker, strict, ui)
                var raw: String? = null
                try {
                    raw = Ai.translateRetry(p, prompt)
                } catch (e: Exception) {
                    val kind = AiRetry.kindOf(e)
                    st.inc(AiRetry.statKey(kind))
                    warn(AiRetry.explain(kind, (e as? AiException)?.code ?: 0, Ai.coolingMs(p)))
                }
                if (raw.isNullOrEmpty()) break
                val parsed = if (emo) Emo.parse(raw) else Emo.Parsed(raw, null)
                val fixed = terms.fixOutput(TranslationGuard.clean(parsed.text, text))
                val v = TranslationGuard.check(text, fixed, names, lang)
                if (v.level != TranslationGuard.Level.REJECT) {
                    val d = if (emo) parsed.delivery ?: Emo.detect(text, fixed, pace) else null
                    chosen = Tr(fixed, d, v.level == TranslationGuard.Level.SUSPECT, "ai")
                    st.inc("dich_ai")
                    break
                }
                st.inc("dich_loai")
                strict = v.reasons.joinToString("; ")
                if (attempt == 1) st.inc("may_loai")
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
            val bad = !TranslationGuard.check(text, r, names, lang).ok
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

    private var lastWarnAt = 0L

    /** Báo người dùng lý do câu này phải dịch máy. Có ô báo thì báo mỗi lần; không thì nhắn (Toast) tối đa mỗi 45 giây một lần. */
    private fun warn(m: String) {
        val cb = onFallback
        if (cb != null) { main.post { if (!closed) cb(m) }; return }
        val now = System.currentTimeMillis()
        if (now - lastWarnAt < 45_000L) return
        lastWarnAt = now
        main.post { Toast.makeText(ctx.applicationContext, m, Toast.LENGTH_LONG).show() }
    }

    fun close() {
        closed = true
        try { local?.close() } catch (_: Exception) {}
    }
}
