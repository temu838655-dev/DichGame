package com.dichgame.app

import android.content.Context
import java.util.Locale

/**
 * Những quy tắc thuần Kotlin được kiểm thử riêng: chỉ ghi nhớ câu đủ dài để giảm
 * nguy cơ áp một bản dịch phụ thuộc ngữ cảnh cho câu ngắn như "Yes", "No".
 */
internal object LearnedTranslationRules {
    private val whitespace = Regex("\\s+")
    fun normalize(text: String): String = text.trim().replace(whitespace, " ").lowercase(Locale.ROOT)

    fun key(game: String, lang: String, speaker: String?, ui: Boolean, source: String): String =
        listOf(normalize(game), lang.lowercase(Locale.ROOT), if (ui) "menu" else "dialogue",
            normalize(speaker.orEmpty()), normalize(source)).joinToString("\u0001")

    fun canRemember(source: String, vi: String): Boolean {
        val src = source.trim()
        val translated = vi.trim()
        if (src.length !in 12..500 || translated.length !in 3..1400) return false
        if (src.count { it.isLetter() } < 8) return false
        if (src.contains('\u0000') || translated.contains('\u0000')) return false
        return normalize(src) != normalize(translated)
    }
}

/**
 * Persistent translation memory backed by indexed local SQLite.
 * Exact game/language/speaker/menu/source keys; manual corrections win.
 */
object LearnedTranslations {
    data class Entry(val key: String, val game: String, val original: String, val vi: String, val manual: Boolean)

    private inline fun <T> safe(fallback: T, block: () -> T): T = try { block() }
        catch (_: Exception) { android.util.Log.w("DG_DB", "Translation memory unavailable"); fallback }

    fun lookup(ctx: Context, profile: String, game: String, lang: String, speaker: String?,
               ui: Boolean, source: String): Entry? = safe<Entry?>(null) {
        if (game.isBlank()) null else GameDatabase.get(ctx).lookup(profile,
            LearnedTranslationRules.key(game,lang,speaker,ui,source))
    }

    fun remember(ctx: Context, profile: String, game: String, lang: String, speaker: String?,
                 ui: Boolean, source: String, vi: String) {
        if (game.isBlank() || !LearnedTranslationRules.canRemember(source,vi)) return
        safe(Unit) {
            GameDatabase.get(ctx).remember(profile, Entry(
                LearnedTranslationRules.key(game,lang,speaker,ui,source),game,source.trim(),vi.trim(),false))
        }
    }

    fun recent(ctx: Context, profile: String, game: String, limit: Int = 50, search: String = ""): List<Entry> =
        safe(emptyList<Entry>()) { GameDatabase.get(ctx).recent(profile,game,limit,search) }
    fun count(ctx: Context, profile: String, game: String): Int =
        safe(0) { GameDatabase.get(ctx).count(profile,game) }
    fun edit(ctx: Context, profile: String, key: String, corrected: String): Boolean {
        val vi = corrected.trim()
        if (vi.isEmpty() || vi.length > 1400) return false
        return safe(false) { GameDatabase.get(ctx).edit(profile,key,vi) }
    }
    fun delete(ctx: Context, profile: String, key: String) {
        safe(Unit) { GameDatabase.get(ctx).deleteLearned(profile,key) }
    }
    fun clear(ctx: Context, profile: String, game: String) {
        safe(Unit) { GameDatabase.get(ctx).clearGame(profile,game) }
    }
    fun clearProfile(ctx: Context, profile: String) {
        safe(Unit) { GameDatabase.get(ctx).clearLearned(profile) }
    }
}
