package com.dichgame.app

import java.security.SecureRandom

/** Short-lived pairing requests and revocable remembered receiver tokens. */
internal class WebReceiverPairing(
    saved: Set<String> = emptySet(),
    private val persist: (Set<String>) -> Unit = {},
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    data class Request(val id: String, val code: String, val expires: Long, var token: String? = null, val peer: String = "")
    private val random = SecureRandom()
    private val pending = LinkedHashMap<String, Request>()
    private val tokens = LinkedHashSet(saved.filter { it.matches(Regex("[0-9a-f]{64}")) }.take(16))
    private fun secret(): String = ByteArray(32).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun prune() { pending.values.removeAll { it.expires <= now() } }

    @Synchronized fun register(peer: String = ""): Request? {
        prune()
        if (pending.size >= 8 || (peer.isNotEmpty() && pending.values.count { it.peer == peer } >= 2)) return null
        var code: String
        do { code = (10000000 + random.nextInt(90000000)).toString() } while (pending.values.any { it.code == code })
        return Request(secret(), code, now() + 120000, peer = peer).also { pending[it.id] = it.copy() }
    }

    @Synchronized fun approve(code: String): Boolean {
        prune()
        val r = pending.values.firstOrNull { it.code == code && it.token == null } ?: return false
        if (tokens.size >= 16) return false
        val token = secret()
        tokens.add(token)
        persist(tokens.toSet())
        r.token = token
        return true
    }

    @Synchronized fun request(id: String): Request? {
        prune()
        return pending[id]?.copy()
    }

    @Synchronized fun clearPending() { pending.clear() }
    @Synchronized fun valid(token: String): Boolean = token.length == 64 && tokens.contains(token)
    @Synchronized fun revokeAll() { tokens.clear(); pending.clear(); persist(emptySet()) }
}
