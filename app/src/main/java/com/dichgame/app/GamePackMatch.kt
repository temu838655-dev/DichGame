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
}
