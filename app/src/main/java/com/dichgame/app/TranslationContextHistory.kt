package com.dichgame.app

/**
 * Keeps a bounded, thread-safe dialogue context for AI translation.
 * A snapshot is captured before the new line is appended, so the prompt
 * contains only preceding dialogue and never the line being translated twice.
 */
internal class TranslationContextHistory(private val capacity: Int = 10) {
    private val entries = ArrayList<String>()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun appendAndSnapshot(text: String, contextCount: Int): List<String> = synchronized(entries) {
        val previous = entries.takeLast(contextCount.coerceIn(0, capacity))
        entries.add(text)
        while (entries.size > capacity) entries.removeAt(0)
        previous
    }
}
