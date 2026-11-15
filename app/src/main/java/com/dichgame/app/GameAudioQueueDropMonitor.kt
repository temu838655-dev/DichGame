package com.dichgame.app

/** Converts PCM queue overflow counters into throttled warnings without losing drops. */
internal class GameAudioQueueDropMonitor(
    private val warningIntervalMs: Long = DEFAULT_WARNING_INTERVAL_MS,
) {
    private var lastObservedDrops = 0L
    private var pendingDrops = 0L
    private var lastWarningAtMs: Long? = null

    init { require(warningIntervalMs >= 0L) }

    fun shouldWarn(totalDropped: Long, nowMs: Long): Boolean {
        if (totalDropped > lastObservedDrops) {
            val delta = totalDropped - lastObservedDrops
            pendingDrops = (pendingDrops + delta).coerceAtMost(Long.MAX_VALUE)
            lastObservedDrops = totalDropped
        }
        if (pendingDrops == 0L) return false
        val previousWarning = lastWarningAtMs
        if (previousWarning != null &&
            (nowMs < previousWarning || nowMs - previousWarning < warningIntervalMs)
        ) return false
        pendingDrops = 0L
        lastWarningAtMs = nowMs
        return true
    }

    companion object { const val DEFAULT_WARNING_INTERVAL_MS = 5_000L }
}
