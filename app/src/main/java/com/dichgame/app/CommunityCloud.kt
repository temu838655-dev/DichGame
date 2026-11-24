package com.dichgame.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Optional self-hosted service. Nothing is transmitted without explicit enablement. */
object CommunityCloud {
    private val pool = ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(16))
    private val main = Handler(Looper.getMainLooper())
    private var failedUntil = 0L
    data class Config(val url: String, val token: String)
    fun validUrl(raw: String): Boolean = try {
        val url = java.net.URI(raw.trim())
        url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null &&
            url.query == null && url.fragment == null && (url.path.isNullOrEmpty() || url.path == "/") &&
            (url.port == -1 || url.port == 443)
    } catch (_: Exception) { false }
    fun config(p: Prefs): Config? {
        if (!p.communityEnabled || p.narrate) return null
        val url = p.communityUrl.trim().trimEnd('/')
        val token = p.communityToken
        return if (validUrl(url) && token.length in 24..500) Config(url,token) else null
    }
    private fun request(config: Config, path: String, body: JSONObject? = null, timeout: Int = 1400): JSONObject? {
        val c = URL(config.url + path).openConnection() as HttpsURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = timeout; c.readTimeout = timeout
            c.requestMethod = if (body == null) "GET" else "POST"
            c.setRequestProperty("Authorization","Bearer " + config.token)
            c.setRequestProperty("Accept","application/json")
            if (body != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type","application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode !in 200..299) return null
            val bytes = c.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = stream.read(buffer); if(n < 0) break
                    if (out.size()+n > 1_000_000) return null
                    out.write(buffer,0,n)
                }
                out.toString("UTF-8")
            }
            return JSONObject(bytes)
        } finally { c.disconnect() }
    }
    private fun line(game: String, lang: String, speaker: String?, ui: Boolean, text: String) =
        JSONObject().put("game",game).put("lang",lang).put("speaker",speaker.orEmpty())
            .put("kind",if(ui) "menu" else "dialogue").put("original",text)

    private fun allowed(game: String, lang: String, speaker: String?, text: String): Boolean =
        game.length in 1..180 && game != Prefs.DEFAULT_PROFILE && Prefs.isSupportedSource(lang) &&
            speaker.orEmpty().length <= 120 && text.length in 12..500 && text.count { it.isLetter() } >= 8

    /** Run on the existing AI worker, never on Android's UI thread. */
    fun lookup(p: Prefs, game: String, lang: String, speaker: String?, ui: Boolean, text: String): String? {
        val cfg = config(p) ?: return null
        if (!allowed(game,lang,speaker,text)) return null
        if (System.currentTimeMillis() < synchronized(this) { failedUntil }) return null
        return try {
            val result = request(cfg,"/v1/lookup",line(game,lang,speaker,ui,text))
            if (result == null) { synchronized(this) { failedUntil = System.currentTimeMillis()+30_000 }; null }
            else if (result.isNull("vi")) null else result.optString("vi").takeIf { it.length in 3..1400 }
        } catch (_: Exception) {
            synchronized(this) { failedUntil = System.currentTimeMillis()+30_000 }
            null
        }
    }
    fun contribute(p: Prefs, game: String, lang: String, speaker: String?, ui: Boolean, text: String, vi: String) {
        val cfg = config(p) ?: return
        if (!p.communityContribute || !allowed(game,lang,speaker,text) ||
            !LearnedTranslationRules.canRemember(text,vi) || p.trStyle != 0 || p.customPrompt.isNotBlank() ||
            p.glossary.isNotBlank() || p.cast.isNotBlank()) return
        try { pool.execute {
            if (config(p) != cfg || !p.communityContribute || game != PackStore.gameName(p) || p.trStyle != 0 || p.customPrompt.isNotBlank() || p.glossary.isNotBlank() || p.cast.isNotBlank()) return@execute
            runCatching { request(cfg,"/v1/contribute",line(game,lang,speaker,ui,text).put("vi",vi),2500) }
        } } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }
    fun test(p: Prefs, done: (Boolean)->Unit) {
        val cfg = config(p)
        if (cfg == null) { done(false); return }
        try { pool.execute {
            val ok = runCatching { request(cfg,"/v1/status",timeout=4000)?.optBoolean("ok") == true }.getOrDefault(false)
            main.post { done(ok) }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { done(false) }
    }
    fun refreshCatalog(ctx: Context, p: Prefs, done: (Int)->Unit = {}) {
        val cfg = config(p) ?: return
        try { pool.execute {
            val count = runCatching {
                val arr = request(cfg,"/v1/catalog",timeout=4000)?.optJSONArray("games") ?: return@runCatching -1
                val items = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val name=o.optString("title"); val genre=o.optString("genre")
                    if(name.length !in 1..180 || genre.length>180) null else name to genre
                }
                if(config(p)!=cfg) return@runCatching -1
                // Cached public game metadata only, not credentials or dialogue.
                val json = org.json.JSONArray()
                for ((name,genre) in items) json.put(JSONObject().put("title",name).put("genre",genre))
                ctx.applicationContext.getSharedPreferences("dg_cloud_catalog",Context.MODE_PRIVATE).edit()
                    .putString("origin",cfg.url).putString("games",json.toString()).apply()
                items.size
            }.getOrDefault(-1)
            main.post { done(count) }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { done(-1) }
    }
    fun cachedCatalog(ctx: Context, p: Prefs): List<Pair<String,String>> {
        val cfg = config(p) ?: return emptyList()
        val store=ctx.getSharedPreferences("dg_cloud_catalog",Context.MODE_PRIVATE)
        if(store.getString("origin","")!=cfg.url) return emptyList()
        return runCatching {
            val a=org.json.JSONArray(store.getString("games","[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { x->x.getString("title") to x.optString("genre") } }
        }.getOrDefault(emptyList())
    }
    private val syncPool = ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(3))
    private val syncing = HashSet<String>()

    fun offlineCount(ctx: Context,p: Prefs,game: String,lang: String): Int =
        runCatching { GameDatabase.get(ctx).cloudPackCount(p.communityUrl.trim().trimEnd('/'),game,lang) }.getOrDefault(0)

    /** Cached pack remains available even if the server is unreachable or lookup is disabled. */
    fun offlineLookup(ctx: Context,p: Prefs,game: String,lang: String,text: String): String? =
        runCatching { GameDatabase.get(ctx).cloudPackLookup(p.communityUrl.trim().trimEnd('/'),game,lang,text) }.getOrNull()

    fun syncCurrent(ctx: Context,p: Prefs) {
        val game=PackStore.gameName(p)
        downloadPack(ctx.applicationContext,p,game,p.profile,p.lang,force=false) {}
    }

    /** Resumable download, committing each page to SQLite; independent from user-imported packs. */
    fun downloadPack(ctx: Context,p: Prefs,game: String,profile: String,lang: String,force: Boolean=true,done:(Int)->Unit) {
        val cfg=config(p)
        if(cfg==null || !Prefs.isSupportedSource(lang) || game.isBlank() || game==Prefs.DEFAULT_PROFILE) {done(-1);return}
        val app=ctx.applicationContext
        val key=cfg.url+"|"+LearnedTranslationRules.normalize(game)+"|"+lang
        synchronized(syncing) {
            if(!syncing.add(key)) { done(-2);return }
        }
        try { syncPool.execute {
            val result=runCatching {
                val db=GameDatabase.get(app)
                val progress=db.cloudProgress(cfg.url,game,lang)
                if(!force && progress.cursor.isEmpty() && progress.completedMs>0 &&
                    System.currentTimeMillis()-progress.completedMs in 0..1_800_000L) return@runCatching db.cloudPackCount(cfg.url,game,lang)
                var after=progress.cursor
                repeat(200) {
                    if(config(p)!=cfg || profile!=p.profile || lang!=p.lang || game!=PackStore.gameName(p)) return@runCatching -1
                    val body=JSONObject().put("game",game).put("lang",lang).put("after",after)
                    val json=request(cfg,"/v1/pack",body,4000) ?: return@runCatching -1
                    val a=json.optJSONArray("lines") ?: return@runCatching -1
                    val pairs=ArrayList<Pair<String,String>>(a.length())
                    for(i in 0 until a.length()) {
                        val row=a.getJSONObject(i); val original=row.optString("original"); val vi=row.optString("vi")
                        if(original.length in 1..500 && vi.length in 1..1400) pairs.add(original to vi)
                    }
                    val next=if(json.isNull("next")) null else json.optString("next")
                    if(next!=null && (next.isBlank() || next==after)) return@runCatching -1
                    if(config(p)!=cfg) return@runCatching -1
                    db.saveCloudPage(cfg.url,game,lang,pairs,next)
                    if(next==null) return@runCatching db.cloudPackCount(cfg.url,game,lang)
                    after=next
                }
                // Budgeted work per run; next launch resumes from the saved cursor.
                db.cloudPackCount(cfg.url,game,lang)
            }.getOrDefault(-1)
            synchronized(syncing) { syncing.remove(key) }
            main.post {done(result)}
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            synchronized(syncing) { syncing.remove(key) }
            done(-1)
        }
    }
}
