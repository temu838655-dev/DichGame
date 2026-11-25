package com.dichgame.app

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

/**
 * Shares one in-flight computation for equal keys. Entries are removed as soon as the
 * owner finishes, so this is request coalescing rather than a second translation cache.
 */
internal class RequestDeduplicator<K : Any, V> {
    private val inFlight = ConcurrentHashMap<K, FutureTask<V>>()

    fun execute(key: K, call: () -> V): V {
        val created = FutureTask(Callable { call() })
        val existing = inFlight.putIfAbsent(key, created)
        val selected = existing ?: created
        if (existing == null) {
            try {
                created.run()
            } finally {
                inFlight.remove(key, created)
            }
        }
        return try {
            selected.get()
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is Exception) throw cause
            throw e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    fun inFlightCount(): Int = inFlight.size
}
