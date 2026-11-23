package com.dichgame.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
 * Bộ nhớ dịch AI lâu dài, lưu nội bộ theo hồ sơ game; không gửi dữ liệu tới server mới.
 * Khớp chính xác câu gốc + người nói + ngôn ngữ + loại phụ đề/menu, KHÔNG tìm gần đúng.
 * Dữ liệu được ghi bằng AtomicFile và ghi gom trên một luồng I/O.
 */
object LearnedTranslations {
    data class Entry(val key: String, val game: String, val original: String, val vi: String, val manual: Boolean)

    private const val LIMIT = 2500
    private val lock = Any()
    private val loaded = HashMap<String, LinkedHashMap<String, Entry>>()
    private val pending = HashSet<String>()
    private val scheduled = HashSet<String>()
    private val writer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "DichGame-Learned-Translations").apply { isDaemon = true }
    }

    private fun file(ctx: Context, profile: String): File {
        val safe = profile.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40)
        return File(File(ctx.filesDir, "learned_translations"),
            safe + "_" + Integer.toHexString(profile.hashCode()) + ".json")
    }

    /** Must be called under lock. */
    private fun data(ctx: Context, profile: String): LinkedHashMap<String, Entry> =
        loaded.getOrPut(profile) {
            val map = LinkedHashMap<String, Entry>()
            try {
                val f = file(ctx, profile)
                // AtomicFile có thể phục hồi từ .bak sau khi app bị tắt giữa lúc ghi.
                if (f.length() < 12_000_000) {
                    val items = JSONArray(String(AtomicFile(f).readFully(), Charsets.UTF_8))
                    for (i in 0 until items.length()) {
                        val o = items.optJSONObject(i) ?: continue
                        val key = o.optString("key")
                        val source = o.optString("source")
                        val vi = o.optString("vi")
                        if (key.isNotEmpty() && source.isNotBlank() && vi.isNotBlank()) {
                            map.remove(key)
                            map[key] = Entry(key, o.optString("game"), source, vi, o.optBoolean("manual"))
                            if (map.size > LIMIT) map.remove(map.keys.first())
                        }
                    }
                }
            } catch (_: Exception) {
                // Lỗi tệp bộ nhớ không được làm gián đoạn dịch game.
            }
            map
        }

    private fun json(items: Collection<Entry>): String {
        val a = JSONArray()
        for (v in items) a.put(JSONObject().put("key", v.key)
            .put("game", v.game).put("source", v.original).put("vi", v.vi).put("manual", v.manual))
        return a.toString()
    }

    /** Must be called under lock. */
    private fun schedule(ctx: Context, profile: String) {
        pending.add(profile)
        if (!scheduled.add(profile)) return
        val app = ctx.applicationContext
        writer.schedule({
            val snapshot = synchronized(lock) {
                scheduled.remove(profile)
                if (!pending.remove(profile)) null else json(data(app, profile).values)
            }
            if (snapshot != null) {
                val target = AtomicFile(file(app, profile))
                target.baseFile.parentFile?.mkdirs()
                var stream: java.io.FileOutputStream? = null
                try {
                    stream = target.startWrite()
                    stream.write(snapshot.toByteArray(Charsets.UTF_8))
                    target.finishWrite(stream)
                } catch (_: Exception) {
                    if (stream != null) target.failWrite(stream)
                }
            }
        }, 300, TimeUnit.MILLISECONDS)
    }

    fun lookup(ctx: Context, profile: String, game: String, lang: String, speaker: String?,
               ui: Boolean, source: String): Entry? = synchronized(lock) {
        if (game.isBlank()) return@synchronized null
        data(ctx, profile)[LearnedTranslationRules.key(game, lang, speaker, ui, source)]
    }

    /** Ghi một câu AI đã được TranslationGuard đánh giá OK, không ghi đè bản sửa tay. */
    fun remember(ctx: Context, profile: String, game: String, lang: String, speaker: String?,
                 ui: Boolean, source: String, vi: String) {
        if (game.isBlank() || !LearnedTranslationRules.canRemember(source, vi)) return
        synchronized(lock) {
            val key = LearnedTranslationRules.key(game, lang, speaker, ui, source)
            val map = data(ctx, profile)
            if (map.containsKey(key)) return
            map[key] = Entry(key, game, source.trim(), vi.trim(), false)
            if (map.size > LIMIT) map.remove(map.keys.first())
            schedule(ctx, profile)
        }
    }

    fun recent(ctx: Context, profile: String, game: String, limit: Int = 50,
               search: String = ""): List<Entry> = synchronized(lock) {
        val query = LearnedTranslationRules.normalize(search)
        data(ctx, profile).values.filter {
            it.game.equals(game, ignoreCase = true) &&
                (query.isEmpty() || LearnedTranslationRules.normalize(it.original).contains(query) ||
                    LearnedTranslationRules.normalize(it.vi).contains(query))
        }.takeLast(limit.coerceIn(1, 100)).asReversed()
    }

    fun count(ctx: Context, profile: String, game: String): Int = synchronized(lock) {
        data(ctx, profile).values.count { it.game.equals(game, ignoreCase = true) }
    }

    /** Người dùng sửa bản dịch: ưu tiên bản sửa, không cho AI tự ghi đè. */
    fun edit(ctx: Context, profile: String, key: String, corrected: String): Boolean {
        val vi = corrected.trim()
        if (vi.isEmpty() || vi.length > 1400) return false
        synchronized(lock) {
            val map = data(ctx, profile)
            val old = map[key] ?: return false
            map.remove(key)
            map[key] = old.copy(vi = vi, manual = true)
            schedule(ctx, profile)
            return true
        }
    }

    fun delete(ctx: Context, profile: String, key: String) {
        synchronized(lock) {
            if (data(ctx, profile).remove(key) != null) schedule(ctx, profile)
        }
    }

    fun clear(ctx: Context, profile: String, game: String) {
        synchronized(lock) {
            val map = data(ctx, profile)
            val keys = map.values.filter { it.game.equals(game, ignoreCase = true) }.map { it.key }
            for (key in keys) map.remove(key)
            if (keys.isNotEmpty()) schedule(ctx, profile)
        }
    }

    /** Khi xóa hồ sơ game, xóa mọi bản dịch của hồ sơ đó. */
    fun clearProfile(ctx: Context, profile: String) {
        synchronized(lock) {
            data(ctx, profile).clear()
            schedule(ctx, profile)
        }
    }
}
