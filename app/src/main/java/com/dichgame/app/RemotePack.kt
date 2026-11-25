package com.dichgame.app

import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Versioned manifest for an authorized, HTTPS-hosted game-specific CSV. */
internal object RemotePack {
    data class Manifest(val game: String, val version: Int, val url: URL, val bytes: Int, val sha256: String)
    enum class Decision { INSTALL, SAME, REJECT }

    /**
     * Resolve one game-level translation dataset. Its source may be a PC localization
     * or another permitted source; it is intentionally independent from the console
     * where the player is currently viewing the game.
     */
    fun catalogSource(raw: String, game: String, platform: String? = null): String? {
        require(game.isNotBlank())
        val target = platform?.trim()?.uppercase(Locale.ROOT)
        require(target == null || target in setOf("PS5", "PS4", "XBOX")) {
            "DịchGame chỉ hỗ trợ đích PS5, PS4 và Xbox."
        }
        val obj = JSONObject(raw)
        val schema = obj.getInt("schema")
        require(schema in 1..2) { "Phiên bản danh mục chưa được hỗ trợ." }
        val entries = obj.getJSONArray("games")
        require(entries.length() <= 2000)
        val matches = (0 until entries.length()).map { entries.getJSONObject(it) }
            .filter { GamePackMatch.selected(game, it.getString("game")) }
        require(matches.size <= 1) { "Danh mục có nhiều gói dữ liệu cho cùng game; hãy hợp nhất thành một bộ dữ liệu." }
        val entry = matches.singleOrNull() ?: return null

        // Schema 2 must declare the console targets explicitly. Source platform is
        // provenance only (e.g. PC); it never grants cross-console reuse by itself.
        if (schema >= 2) {
            val targets = entry.optJSONArray("target_platforms")
                ?: throw IllegalArgumentException("Danh mục thiếu target_platforms.")
            require(targets.length() in 1..3) { "target_platforms phải có ít nhất một đích PS5, PS4 hoặc Xbox." }
            val allowed = setOf("PS5", "PS4", "XBOX")
            val normalized = (0 until targets.length()).map { targets.getString(it).trim().uppercase(Locale.ROOT) }
            require(normalized.all { it in allowed } && normalized.distinct().size == normalized.size) {
                "target_platforms chỉ được chứa PS5, PS4 và XBOX, không trùng lặp."
            }
            require(target == null || target in normalized) {
                "Gói Việt hóa này chưa khai báo quyền sử dụng cho $target."
            }
        } else if (target != null) {
            // Legacy schema 1 has no explicit rights metadata. Do not infer PC-to-console reuse.
            val source = entry.optString("source_platform", "").trim().uppercase(Locale.ROOT)
            require(source == target) { "Danh mục cũ không xác nhận quyền dùng nguồn $source trên $target." }
        }

        val url = URL(entry.getString("manifest"))
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
            url.ref == null && url.path.endsWith(".dichgame.json", true))
        return url.toExternalForm()
    }

    fun retryableHttp(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

    fun versionDecision(oldVersion: Int?, oldHash: String?, version: Int, hash: String): Decision =
        when {
            oldVersion == null || version > oldVersion -> Decision.INSTALL
            version == oldVersion && hash == oldHash -> Decision.SAME
            else -> Decision.REJECT
        }

    /** Manual/imported local translations always override cloud entries by literal source. */
    fun mergeOffline(cloud: List<Pair<String, String>>, local: List<Pair<String, String>>): List<Pair<String, String>> {
        val map = LinkedHashMap<String, Pair<String, String>>()
        for (rows in listOf(cloud, local)) for ((source, vi) in rows) {
            if (source.isNotBlank() && vi.isNotBlank()) map[Canon.literal(source)] = source to vi
        }
        return map.values.toList()
    }

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
