package com.dichgame.app

import java.util.ArrayDeque
import java.util.Locale

/**
 * Small FIFO for spoken game lines waiting for translation.
 *
 * When full, reject the newest line rather than evicting an older queued line:
 * this preserves the original dialogue order and avoids silently skipping the
 * beginning of a conversation while a slow translation is in flight.
 */
internal class PendingAudioTranscriptQueue(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) {
    data class Entry(val text: String, val receivedAtMs: Long)

    private val entries = ArrayDeque<Entry>()

    init {
        require(capacity > 0)
        require(maxAgeMs >= 0L)
    }

    fun offer(text: String, nowMs: Long): Boolean {
        val normalized = normalize(text)
        if (normalized.isEmpty() || containsNormalized(normalized)) return false
        if (entries.size >= capacity) return false
        entries.addLast(Entry(text, nowMs))
        return true
    }

    fun contains(text: String): Boolean {
        val normalized = normalize(text)
        return normalized.isNotEmpty() && containsNormalized(normalized)
    }

    /** Removes expired entries from the head and returns the next fresh line. */
    fun pollFresh(nowMs: Long): Entry? {
        while (entries.isNotEmpty()) {
            val entry = entries.removeFirst()
            val age = nowMs - entry.receivedAtMs
            if (age in 0L..maxAgeMs) return entry
        }
        return null
    }

    fun isNotEmpty(): Boolean = entries.isNotEmpty()

    fun clear() = entries.clear()

    private fun containsNormalized(normalized: String): Boolean =
        entries.any { normalize(it.text) == normalized }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    companion object {
        const val DEFAULT_CAPACITY = 6
        const val DEFAULT_MAX_AGE_MS = 8_000L
    }
}
