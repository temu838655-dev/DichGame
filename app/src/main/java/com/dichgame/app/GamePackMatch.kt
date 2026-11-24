package com.dichgame.app

import java.util.Locale

/**
 * So khớp tên gói với tên game có ranh giới từ. Không để "GTA V" nhận gói
 * "GTA VI" hoặc "Final Fantasy VII" nhận gói "Final Fantasy VIII".
 * Các khóa series ("resident evil", "god of war"...) vẫn khớp mọi phần trong series.
 */
object GamePackMatch {
    fun contains(game: String, keyword: String): Boolean {
        val title = game.lowercase(Locale.ROOT)
        val key = keyword.trim().lowercase(Locale.ROOT)
        if (title.isBlank() || key.isBlank()) return false
        var at = title.indexOf(key)
        while (at >= 0) {
            val end = at + key.length
            val left = at == 0 || !title[at - 1].isLetterOrDigit()
            val right = end == title.length || !title[end].isLetterOrDigit()
            if (left && right) return true
            at = title.indexOf(key, at + 1)
        }
        return false
    }

    fun selected(currentGame: String, candidate: String): Boolean =
        currentGame.trim().equals(candidate.trim(), ignoreCase = true)

    /** Empty means the existing profile store; otherwise a distinct game namespace. */
    fun storageGame(profile: String, game: String): String =
        if (game.isBlank() || selected(game, profile)) "" else game.trim().lowercase(Locale.ROOT)

    fun storageHash(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Bảo toàn tên trò chơi chứa dấu phẩy; người dùng có thể đặt thể loại sau dấu phẩy. */
    fun title(description: String, profile: String, explicit: String, defaultProfile: String): String {
        if (explicit.isNotBlank()) return explicit.trim()
        val value = description.trim()
        val name = profile.trim()
        if (name.isNotBlank() && name != defaultProfile &&
            (value.equals(name, ignoreCase = true) ||
                value.startsWith("$name, ", ignoreCase = true))) return name
        return value.substringBefore(',').trim().ifEmpty { if (name != defaultProfile) name else "" }
    }
}
