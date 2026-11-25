package com.dichgame.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** One refresh loop per active translation session. No network work on the UI thread. */
class CloudRefresh(ctx: Context) {
    private val app = ctx.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0L
    private var enabled = false
    private var busy = false
    private var lastContext = ""
    private var next = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!enabled) return
            try {
            val p = Prefs(app)
            val profile = p.profile
            val game = PackStore.gameName(p)
            val context = profile + "\u0000" + game + "\u0000" + p.packCatalogUrl + "\u0000" + p.autoPackRefresh
            if (context != lastContext) { lastContext = context; generation++; next = 0L }
            val now = android.os.SystemClock.elapsedRealtime()
            if (p.autoPackRefresh && game.isNotBlank() && !busy && now >= next) {
                val token = generation
                val catalog = p.packCatalogUrl
                next = now + 30 * 60_000L
                run {
                    busy = true
                    exec.execute {
                        try {
                            val epoch = PackStore.autoCloudEpoch(app, profile, game) ?: return@execute
                            if (generation != token) return@execute
                            val source = PackStore.cloudInfo(app, profile, game)?.source
                                ?: catalog.takeIf { it.isNotBlank() }?.let { PackStore.catalogSource(it, game) }
                            if (source != null) PackStore.importLink(app, p, source, profile, game, autoEpoch = epoch) {
                                generation == token && p.autoPackRefresh && p.packCatalogUrl == catalog && !Thread.currentThread().isInterrupted
                            }
                        } catch (_: Exception) {
                            // Keep existing offline data.
                        } finally { handler.post { busy = false } }
                    }
                }
            }
            } catch (_: Exception) {
                busy = false
            } finally {
                if (enabled) handler.postDelayed(this, 15_000L)
            }
        }
    }
    fun start() {
        if (enabled) return
        enabled = true
        generation++
        next = 0L
        handler.post(tick)
    }
    fun stop() {
        enabled = false
        generation++
        handler.removeCallbacks(tick)
    }
    fun close() { stop(); exec.shutdownNow() }
}
