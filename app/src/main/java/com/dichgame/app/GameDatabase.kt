package com.dichgame.app

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.util.Locale

/**
 * Offline, device-local database. No account, network or third-party SDK required.
 * Each old profile is imported transactionally once. A source file is only deleted
 * after SQLite has committed both the imported rows and the migration marker.
 */
internal class GameDatabase private constructor(private val app: Context) :
    SQLiteOpenHelper(app, NAME, null, VERSION) {
    companion object {
        const val NAME = "dichgame.db"
        const val VERSION = 2
        @Volatile private var instance: GameDatabase? = null
        fun get(ctx: Context): GameDatabase = instance ?: synchronized(this) {
            instance ?: GameDatabase(ctx.applicationContext).also {
                it.setWriteAheadLoggingEnabled(true)
                instance = it
            }
        }
        fun legacySlug(profile: String): String = profile.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40) +
            "_" + Integer.toHexString(profile.hashCode())

        fun warm(ctx: Context) {
            val app = ctx.applicationContext
            Thread({
                try {
                    val db = get(app)
                    db.catalog()
                    for (profile in Prefs(app).profiles) {
                        runCatching { db.ensureProfile(profile) }
                        runCatching { db.ensureLearned(profile) }
                        runCatching { db.ensurePack(profile) }
                    }
                } catch (_: Exception) { Log.w("DG_DB", "Database warm-up failed") }
            }, "DG-Database-Migration").start()
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE migration (kind TEXT NOT NULL, profile TEXT NOT NULL, PRIMARY KEY(kind,profile))")
        db.execSQL("CREATE TABLE learned (id INTEGER PRIMARY KEY AUTOINCREMENT, profile TEXT NOT NULL, lookup_key TEXT NOT NULL, game TEXT NOT NULL, game_norm TEXT NOT NULL, original TEXT NOT NULL, vi TEXT NOT NULL, original_norm TEXT NOT NULL, vi_norm TEXT NOT NULL, manual INTEGER NOT NULL DEFAULT 0, UNIQUE(profile,lookup_key))")
        db.execSQL("CREATE INDEX learned_game_recent ON learned(profile,game_norm,id DESC)")
        db.execSQL("CREATE TABLE pack_line (id INTEGER PRIMARY KEY AUTOINCREMENT, profile TEXT NOT NULL, canon TEXT NOT NULL, original TEXT NOT NULL, vi TEXT NOT NULL, UNIQUE(profile,canon))")
        db.execSQL("CREATE INDEX pack_profile_order ON pack_line(profile,id)")
        db.execSQL("CREATE TABLE game_profile (profile TEXT PRIMARY KEY, glossary TEXT NOT NULL DEFAULT '', cast_text TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE game_catalog (name TEXT PRIMARY KEY, genre TEXT NOT NULL, ordinal INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE catalog_meta (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createCloudPackTables(db)
    }

    private fun createCloudPackTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_pack (origin TEXT NOT NULL, game_key TEXT NOT NULL, lang TEXT NOT NULL, source_key TEXT NOT NULL, original TEXT NOT NULL, vi TEXT NOT NULL, PRIMARY KEY(origin,game_key,lang,source_key))")
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_pack_sync (origin TEXT NOT NULL, game_key TEXT NOT NULL, lang TEXT NOT NULL, cursor TEXT NOT NULL DEFAULT '', completed_ms INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(origin,game_key,lang))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            createCloudPackTables(db)
            return
        }
        throw IllegalStateException("Unsupported database upgrade $oldVersion -> $newVersion")
    }

    data class CloudProgress(val cursor: String, val completedMs: Long)
    private fun cloudKey(value: String) = LearnedTranslationRules.normalize(value)
    fun cloudProgress(origin: String, game: String, lang: String): CloudProgress =
        readableDatabase.rawQuery("SELECT cursor,completed_ms FROM cloud_pack_sync WHERE origin=? AND game_key=? AND lang=?",arrayOf(origin,cloudKey(game),lang)).use {
            if(it.moveToFirst()) CloudProgress(it.getString(0),it.getLong(1)) else CloudProgress("",0L)
        }
    fun cloudPackCount(origin: String, game: String, lang: String): Int =
        DatabaseUtils.longForQuery(readableDatabase,"SELECT COUNT(*) FROM cloud_pack WHERE origin=? AND game_key=? AND lang=?",arrayOf(origin,cloudKey(game),lang)).toInt()

    /** Stream the selected game's offline rows, with no network or full in-memory copy. */
    fun exportCloudPack(origin: String, game: String, lang: String, out: java.io.Writer): Int {
        require(game.isNotBlank() && lang in listOf("en","zh"))
        fun cell(value: String): String = buildString { append('"'); for(c in value) { if(c=='"') append('"'); append(c) }; append('"') }
        out.write(lang+",vi\r\n")
        var count=0
        readableDatabase.rawQuery("SELECT original,vi FROM cloud_pack WHERE origin=? AND game_key=? AND lang=? ORDER BY source_key",arrayOf(origin,cloudKey(game),lang)).use { c ->
            while(c.moveToNext()) {
                out.write(cell(c.getString(0)));out.write(",");out.write(cell(c.getString(1)));out.write("\r\n")
                count++
            }
        }
        return count
    }

    /** Strict indexed lookup, without OCR character folding or loading the whole game into RAM. */
    fun cloudPackLookup(origin: String, game: String, lang: String, original: String): String? {
        val key = cloudKey(original)
        if (key.length < 2 || game.isBlank()) return null
        return readableDatabase.rawQuery("SELECT vi FROM cloud_pack WHERE origin=? AND game_key=? AND lang=? AND source_key=?",arrayOf(origin,cloudKey(game),lang,key)).use {
            if(it.moveToFirst()) it.getString(0) else null
        }
    }

    /** Save page and resume cursor together; a failed network request never removes local rows. */
    fun saveCloudPage(origin: String, game: String, lang: String, pairs: List<Pair<String,String>>, next: String?) {
        val gameKey=cloudKey(game)
        transaction { db ->
            val stmt=db.compileStatement("INSERT OR REPLACE INTO cloud_pack(origin,game_key,lang,source_key,original,vi) VALUES(?,?,?,?,?,?)")
            try {
                for ((original,vi) in pairs) {
                    val key=cloudKey(original)
                    if(key.isBlank() || vi.isBlank()) continue
                    stmt.clearBindings()
                    listOf(origin,gameKey,lang,key,original,vi).forEachIndexed { i,v->stmt.bindString(i+1,v) }
                    stmt.executeInsert()
                }
            } finally { stmt.close() }
            db.execSQL("INSERT OR REPLACE INTO cloud_pack_sync(origin,game_key,lang,cursor,completed_ms) VALUES(?,?,?,?,?)",
                arrayOf(origin,gameKey,lang,next.orEmpty(),if(next==null) System.currentTimeMillis() else 0L))
        }
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    private val migrated = HashSet<Pair<String, String>>()

    /** Serialize first migration, not subsequent lookups. Do not mark a failed import. */
    @Synchronized private fun migrate(kind: String, profile: String, importRows: (SQLiteDatabase) -> Unit, cleanup: () -> Unit = {}) {
        val key = kind to profile
        if (key in migrated) return
        transaction { db ->
            val present = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM migration WHERE kind=? AND profile=?", arrayOf(kind, profile)) > 0
            if (!present) {
                importRows(db)
                db.execSQL("INSERT INTO migration(kind,profile) VALUES(?,?)", arrayOf(kind, profile))
            }
        }
        migrated.add(key)
        // If the process died after commit but before cleanup, cleanup is safe on next open.
        runCatching { cleanup() }
    }

    private fun learnedValues(profile: String, entry: LearnedTranslations.Entry) = ContentValues().apply {
        put("profile", profile); put("lookup_key", entry.key); put("game", entry.game)
        put("game_norm", LearnedTranslationRules.normalize(entry.game))
        put("original", entry.original); put("vi", entry.vi)
        put("original_norm", LearnedTranslationRules.normalize(entry.original))
        put("vi_norm", LearnedTranslationRules.normalize(entry.vi))
        put("manual", if (entry.manual) 1 else 0)
    }

    fun ensureLearned(profile: String) {
        val file = File(File(app.filesDir, "learned_translations"), legacySlug(profile) + ".json")
        val atomic = AtomicFile(file)
        migrate("learned", profile, { db ->
            if (file.exists() || File(file.path + ".bak").exists()) {
                val items = JSONArray(String(atomic.readFully(), Charsets.UTF_8))
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val key = o.optString("key"); val original = o.optString("source"); val vi = o.optString("vi")
                    if (key.isNotBlank() && original.isNotBlank() && vi.isNotBlank()) {
                        val entry = LearnedTranslations.Entry(key, o.optString("game"), original, vi, o.optBoolean("manual"))
                        db.insertWithOnConflict("learned", null, learnedValues(profile, entry), SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }
            }
        }, { atomic.delete() })
    }

    private fun readEntry(cursor: android.database.Cursor) = LearnedTranslations.Entry(
        cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) != 0)
    private val learnedColumns = "lookup_key,game,original,vi,manual"

    fun lookup(profile: String, key: String): LearnedTranslations.Entry? {
        ensureLearned(profile)
        return readableDatabase.rawQuery("SELECT $learnedColumns FROM learned WHERE profile=? AND lookup_key=?", arrayOf(profile,key)).use {
            if (it.moveToFirst()) readEntry(it) else null
        }
    }
    fun remember(profile: String, entry: LearnedTranslations.Entry) {
        ensureLearned(profile)
        writableDatabase.insertWithOnConflict("learned", null, learnedValues(profile, entry), SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun recent(profile: String, game: String, limit: Int, search: String): List<LearnedTranslations.Entry> {
        ensureLearned(profile)
        val q = LearnedTranslationRules.normalize(search)
        return readableDatabase.rawQuery(
            "SELECT $learnedColumns FROM learned WHERE profile=? AND game_norm=? AND (?='' OR instr(original_norm,?)>0 OR instr(vi_norm,?)>0) ORDER BY id DESC LIMIT ?",
            arrayOf(profile, LearnedTranslationRules.normalize(game), q, q, q, limit.coerceIn(1,100).toString())).use { c ->
            buildList { while (c.moveToNext()) add(readEntry(c)) }
        }
    }
    fun count(profile: String, game: String): Int {
        ensureLearned(profile)
        return DatabaseUtils.longForQuery(readableDatabase, "SELECT COUNT(*) FROM learned WHERE profile=? AND game_norm=?",
            arrayOf(profile, LearnedTranslationRules.normalize(game))).toInt()
    }
    fun edit(profile: String, key: String, vi: String): Boolean {
        ensureLearned(profile)
        return writableDatabase.update("learned", ContentValues().apply {
            put("vi", vi); put("vi_norm", LearnedTranslationRules.normalize(vi)); put("manual", 1)
        }, "profile=? AND lookup_key=?", arrayOf(profile,key)) > 0
    }
    fun deleteLearned(profile: String, key: String) {
        ensureLearned(profile)
        writableDatabase.delete("learned", "profile=? AND lookup_key=?", arrayOf(profile,key))
    }
    fun clearGame(profile: String, game: String) {
        ensureLearned(profile)
        writableDatabase.delete("learned", "profile=? AND game_norm=?", arrayOf(profile,LearnedTranslationRules.normalize(game)))
    }
    fun clearLearned(profile: String) {
        ensureLearned(profile)
        writableDatabase.delete("learned", "profile=?", arrayOf(profile))
    }

    private fun packValues(profile: String, original: String, vi: String): ContentValues? {
        val canon = Canon.key(original)
        if (!Canon.usable(canon) || vi.isBlank()) return null
        return ContentValues().apply {
            put("profile", profile); put("canon", canon)
            put("original", original.replace('\t',' ').replace('\n',' '))
            put("vi", vi.replace('\t',' ').replace('\n',' '))
        }
    }
    fun ensurePack(profile: String) {
        // Original PackStore used the process default locale. Keep both paths for migration.
        val legacy = profile.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40) +
            "_" + Integer.toHexString(profile.hashCode())
        val files = listOf(legacySlug(profile), legacy).distinct().map { File(File(app.filesDir,"lines"), "$it.tsv") }
        migrate("pack", profile, { db ->
            for (file in files) if (file.exists()) file.useLines(Charsets.UTF_8) { lines ->
                for (line in lines) {
                    val i = line.indexOf('\t')
                    if (i <= 0) continue
                    val values = packValues(profile, line.substring(0,i), line.substring(i+1)) ?: continue
                    db.insertWithOnConflict("pack_line", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        }, { files.forEach { it.delete() } })
    }
    fun readPack(profile: String): List<Pair<String,String>> {
        ensurePack(profile)
        return readableDatabase.rawQuery("SELECT original,vi FROM pack_line WHERE profile=? ORDER BY id", arrayOf(profile)).use { c ->
            buildList { while(c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
    }
    fun packCount(profile: String): Int {
        ensurePack(profile)
        return DatabaseUtils.longForQuery(readableDatabase,"SELECT COUNT(*) FROM pack_line WHERE profile=?",arrayOf(profile)).toInt()
    }
    fun addPack(profile: String, pairs: List<Pair<String,String>>): Int {
        ensurePack(profile)
        return transaction { db ->
            for ((original,vi) in pairs) {
                val values = packValues(profile,original,vi) ?: continue
                db.insertWithOnConflict("pack_line",null,values,SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.execSQL("DELETE FROM pack_line WHERE profile=? AND id NOT IN (SELECT id FROM pack_line WHERE profile=? ORDER BY id DESC LIMIT ?)",
                arrayOf(profile,profile,PackText.MAX_LINES))
            DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM pack_line WHERE profile=?",arrayOf(profile)).toInt()
        }
    }
    fun clearPack(profile: String) {
        ensurePack(profile)
        writableDatabase.delete("pack_line","profile=?",arrayOf(profile))
    }

    fun ensureProfile(profile: String) {
        val prefs = app.getSharedPreferences("dg",Context.MODE_PRIVATE)
        migrate("profile",profile,{ db ->
            db.insertWithOnConflict("game_profile",null,ContentValues().apply {
                put("profile",profile)
                put("glossary",prefs.getString("gloss_$profile","").orEmpty())
                put("cast_text",prefs.getString("cast_$profile","").orEmpty())
            },SQLiteDatabase.CONFLICT_IGNORE)
        },{ prefs.edit().remove("gloss_$profile").remove("cast_$profile").commit() })
    }
    fun profileText(profile: String, field: String): String {
        require(field == "glossary" || field == "cast_text")
        ensureProfile(profile)
        return readableDatabase.rawQuery("SELECT $field FROM game_profile WHERE profile=?",arrayOf(profile)).use {
            if(it.moveToFirst()) it.getString(0) else ""
        }
    }
    fun setProfileText(profile: String, field: String, text: String) {
        require(field == "glossary" || field == "cast_text")
        ensureProfile(profile)
        transaction { db ->
            db.execSQL("INSERT OR IGNORE INTO game_profile(profile) VALUES(?)",arrayOf(profile))
            db.update("game_profile",ContentValues().apply { put(field,text) },"profile=?",arrayOf(profile))
        }
    }
    fun clearProfileText(profile: String) {
        ensureProfile(profile)
        writableDatabase.delete("game_profile","profile=?",arrayOf(profile))
    }

    @Synchronized fun catalog(): List<Pair<String,String>> {
        val version = Catalog.GAMES.hashCode().toString()
        val db = writableDatabase
        val old = db.rawQuery("SELECT value FROM catalog_meta WHERE name='seed'",null).use { if(it.moveToFirst()) it.getString(0) else null }
        if (old != version) transaction { sql ->
            sql.delete("game_catalog",null,null)
            Catalog.GAMES.forEachIndexed { i, game ->
                sql.insertOrThrow("game_catalog",null,ContentValues().apply {
                    put("name",game.first); put("genre",game.second); put("ordinal",i)
                })
            }
            sql.execSQL("INSERT OR REPLACE INTO catalog_meta(name,value) VALUES('seed',?)",arrayOf(version))
        }
        return db.rawQuery("SELECT name,genre FROM game_catalog ORDER BY ordinal",null).use { c ->
            buildList { while(c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
    }
}
