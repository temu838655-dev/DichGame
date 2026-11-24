package com.dichgame.app

import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Versioned manifest for an authorized, HTTPS-hosted game-specific CSV. */
internal object RemotePack {
    data class Manifest(val game: String, val version: Int, val url: URL, val bytes: Int, val sha256: String)
    fun parse(raw: String, selectedGame: String): Manifest {
        val obj = JSONObject(raw)
        require(obj.getInt("schema") == 1) { "Định dạng gói Cloud chưa được hỗ trợ." }
        val game = obj.getString("game").trim()
        require(game.isNotBlank() && selectedGame.isNotBlank() && GamePackMatch.selected(selectedGame, game)) { "Gói Cloud không đúng game đang chọn." }
        val version = obj.getInt("version")
        require(version > 0) { "Phiên bản gói không hợp lệ." }
        val url = URL(obj.getString("url"))
        require(url.protocol == "https" && url.userInfo == null && url.host.isNotBlank() && url.ref == null) { "Gói Cloud phải có link HTTPS trực tiếp." }
        val bytes = obj.getLong("bytes")
        require(bytes in 1..24_000_000L) { "Gói Cloud vượt giới hạn 24 MB." }
        val hash = obj.getString("sha256").lowercase(Locale.ROOT)
        require(Regex("^[0-9a-f]{64}$").matches(hash)) { "Mã SHA-256 không hợp lệ." }
        return Manifest(game, version, url, bytes.toInt(), hash)
    }

    fun verify(m: Manifest, bytes: ByteArray): PackText.Pack {
        require(bytes.size == m.bytes) { "Kích thước gói không khớp." }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(digest == m.sha256) { "SHA-256 không khớp, không nhập gói." }
        val pack = PackText.parseScopedCsv(PackText.decode(bytes)) ?: error("Gói Cloud cần CSV source,vi,game.")
        require(pack.name.isNotBlank() && GamePackMatch.selected(m.game, pack.name) && pack.lines.isNotEmpty()) { "Nội dung CSV không đúng game hoặc trống." }
        return pack
    }
}
