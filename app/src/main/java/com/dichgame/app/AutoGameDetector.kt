package com.dichgame.app

import java.text.Normalizer
import java.util.Locale

/**
 * Tìm game từ tên xuất hiện thật trên ảnh OCR, không đoán game qua vài từ hội thoại.
 * Remote Play hiện không cung cấp tên game đang chạy, nên chỉ kết luận khi ảnh có
 * nhãn/tựa game đầy đủ ở ít nhất hai lượt quét liên tiếp.
 */
class AutoGameDetector(private val games: List<String> = Catalog.GAMES.map { it.first }) {
    private var lastCandidate: String? = null
    private var confirmations = 0

    private val aliases = mapOf(
        "gta v" to "Grand Theft Auto V",
        "gta 5" to "Grand Theft Auto V",
        "gta vi" to "Grand Theft Auto VI",
        "gta 6" to "Grand Theft Auto VI",
        "rdr 2" to "Red Dead Redemption 2"
    )

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    /** Không nhận tựa một từ phổ biến ("Control", "Inside", "Stray"...) từ thoại. */
    fun matchFrame(lines: List<String>): String? {
        if (lines.isEmpty()) return null
        val known = games.associateBy { norm(it) }
        val found = LinkedHashSet<String>()
        for (raw in lines) {
            // Chỉ so khớp cả một dòng/tựa game, không tìm chuỗi bên trong lời thoại.
            val n = norm(raw)
            if (n.isEmpty()) continue
            val clean = listOf("now playing ", "game ", "title ", "đang chơi ")
                .firstOrNull { n.startsWith(it) }?.let { n.removePrefix(it) } ?: n
            val name = known[clean] ?: aliases[clean] ?: continue
            if (norm(name).split(" ").size < 2 || norm(name).length < 9) continue
            found.add(name)
        }
        return found.singleOrNull()
    }

    /**
     * Trả tên game chỉ sau hai lượt quét cùng tên; nếu không chắc hoặc trùng game
     * đang chọn thì giữ nguyên hồ sơ, tránh trộn bản dịch của hai trò.
     */
    fun observe(lines: List<String>, current: String): String? {
        val candidate = matchFrame(lines)
        if (candidate == null || candidate.equals(current, ignoreCase = true)) {
            lastCandidate = null
            confirmations = 0
            return null
        }
        if (candidate == lastCandidate) confirmations++ else {
            lastCandidate = candidate
            confirmations = 1
        }
        if (confirmations < 2) return null
        lastCandidate = null
        confirmations = 0
        return candidate
    }
}

/** Đổi sang hồ sơ game riêng sau khi đã nhận diện tên rõ ràng, giữ nguyên các bộ nhớ khác. */
object AutoGameSwitch {
    fun apply(p: Prefs, title: String): Boolean {
        if (!p.aiAuto || title.isBlank() || title.equals(PackStore.gameName(p), ignoreCase = true)) return false
        if (p.profiles.contains(title)) p.profile = title else p.addProfile(title)
        val genre = Catalog.genreOf(title)
        p.game = if (genre.isEmpty()) title else "$title, $genre"
        p.selectedGameTitle = title
        p.addPlayed(title)
        return true
    }
}
