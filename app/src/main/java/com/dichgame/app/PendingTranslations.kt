package com.dichgame.app

/** Identity preserves field boundaries: menu and dialogue, speakers and game scopes never collide. */
internal data class TranslationRequestKey(
    val context: String, val speaker: String?, val ui: Boolean, val text: String
)

/** Coalesce identical in-flight translations; old completions cannot remove newer flights. */
internal class PendingTranslations<K, V> {
    class Flight<V> internal constructor(callback: (V) -> Unit) {
        internal val callbacks = arrayListOf(callback)
    }

    private val active = HashMap<K, Flight<V>>()

    @Synchronized fun contains(key: K): Boolean = active.containsKey(key)

    /** A null ticket means the request joined an existing flight. */
    @Synchronized fun begin(key: K, callback: (V) -> Unit): Flight<V>? {
        val existing = active[key]
        if (existing != null) {
            existing.callbacks.add(callback)
            return null
        }
        return Flight(callback).also { active[key] = it }
    }

    @Synchronized fun take(key: K, flight: Flight<V>): List<(V) -> Unit> {
        if (active[key] !== flight) return emptyList()
        active.remove(key)
        return flight.callbacks.toList()
    }

    @Synchronized fun clear() = active.clear()
}
