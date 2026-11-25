package com.dichgame.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Đóng góp cộng đồng ở phía Android.
 *
 * Quan trọng: các bản ghi ở đây chỉ là bản nháp/báo lỗi lưu cục bộ.
 * Chúng KHÔNG được gửi lên server, KHÔNG có trạng thái VERIFIED và
 * KHÔNG được đưa vào bộ nhớ dịch cho tới khi có backend kiểm duyệt thật.
 */
data class CommunityDraft(
    val id: String,
    val profile: String,
    val game: String,
    val source: String,
    val translation: String,
    val contributor: String,
    val createdAt: Long
)

data class CommunityReport(
    val id: String,
    val profile: String,
    val game: String,
    val source: String,
    val reason: String,
    val createdAt: Long
)

object CommunityContributionRules {
    const val MAX_DRAFTS = 200
    const val MAX_REPORTS = 200
    const val MAX_SOURCE_LENGTH = 2_000
    const val MAX_TRANSLATION_LENGTH = 4_000
    const val MAX_REASON_LENGTH = 1_000

    fun clean(value: String): String = value
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()

    fun validateDraft(game: String, source: String, translation: String, contributor: String) {
        require(clean(game).isNotEmpty()) { "Hãy chọn game trước khi đóng góp." }
        val s = clean(source)
        val v = clean(translation)
        require(s.length in 2..MAX_SOURCE_LENGTH) { "Câu gốc phải có từ 2 đến $MAX_SOURCE_LENGTH ký tự." }
        require(v.isNotEmpty() && v.length <= MAX_TRANSLATION_LENGTH) {
            "Bản dịch không được để trống và tối đa $MAX_TRANSLATION_LENGTH ký tự."
        }
        require(clean(contributor).length <= 60) { "Tên đóng góp tối đa 60 ký tự." }
    }

    fun validateReport(game: String, source: String, reason: String) {
        require(clean(game).isNotEmpty()) { "Hãy chọn game trước khi báo lỗi." }
        require(clean(source).length in 2..MAX_SOURCE_LENGTH) { "Câu cần báo phải có từ 2 đến $MAX_SOURCE_LENGTH ký tự." }
        require(clean(reason).isNotEmpty() && clean(reason).length <= MAX_REASON_LENGTH) {
            "Lý do không được để trống và tối đa $MAX_REASON_LENGTH ký tự."
        }
    }
}

object CommunityLocalStore {
    private const val PREFS = "community_local_v1"
    private const val DRAFTS = "drafts"
    private const val REPORTS = "reports"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun drafts(ctx: Context, profile: String, game: String): List<CommunityDraft> =
        decodeDrafts(prefs(ctx).getString(DRAFTS, "[]").orEmpty())
            .filter { it.profile == profile && it.game == game }
            .sortedByDescending { it.createdAt }

    fun reports(ctx: Context, profile: String, game: String): List<CommunityReport> =
        decodeReports(prefs(ctx).getString(REPORTS, "[]").orEmpty())
            .filter { it.profile == profile && it.game == game }
            .sortedByDescending { it.createdAt }

    @Synchronized
    fun addDraft(ctx: Context, profile: String, game: String, source: String, translation: String, contributor: String): CommunityDraft {
        val g = CommunityContributionRules.clean(game)
        val s = CommunityContributionRules.clean(source)
        val v = CommunityContributionRules.clean(translation)
        val c = CommunityContributionRules.clean(contributor)
        CommunityContributionRules.validateDraft(g, s, v, c)
        val all = decodeDrafts(prefs(ctx).getString(DRAFTS, "[]").orEmpty())
        require(all.size < CommunityContributionRules.MAX_DRAFTS) { "Đã đạt giới hạn bản nháp trên thiết bị." }
        require(all.none { it.profile == profile && it.game == g && it.source.equals(s, ignoreCase = true) }) {
            "Câu gốc này đã có bản nháp trong hồ sơ và game hiện tại."
        }
        val item = CommunityDraft(UUID.randomUUID().toString(), profile, g, s, v, c, System.currentTimeMillis())
        val next = JSONArray().apply { all.forEach { put(it.toJson()) }; put(item.toJson()) }
        check(prefs(ctx).edit().putString(DRAFTS, next.toString()).commit()) { "Không thể ghi bản nháp xuống thiết bị." }
        return item
    }

    @Synchronized
    fun addReport(ctx: Context, profile: String, game: String, source: String, reason: String): CommunityReport {
        val g = CommunityContributionRules.clean(game)
        val s = CommunityContributionRules.clean(source)
        val r = CommunityContributionRules.clean(reason)
        CommunityContributionRules.validateReport(g, s, r)
        val all = decodeReports(prefs(ctx).getString(REPORTS, "[]").orEmpty())
        require(all.size < CommunityContributionRules.MAX_REPORTS) { "Đã đạt giới hạn báo lỗi trên thiết bị." }
        require(all.none { it.profile == profile && it.game == g && it.source.equals(s, true) && it.reason.equals(r, true) }) {
            "Báo lỗi tương tự đã được lưu."
        }
        val item = CommunityReport(UUID.randomUUID().toString(), profile, g, s, r, System.currentTimeMillis())
        val next = JSONArray().apply { all.forEach { put(it.toJson()) }; put(item.toJson()) }
        check(prefs(ctx).edit().putString(REPORTS, next.toString()).commit()) { "Không thể ghi báo lỗi xuống thiết bị." }
        return item
    }

    @Synchronized
    fun deleteDraft(ctx: Context, profile: String, game: String, id: String) {
        val all = decodeDrafts(prefs(ctx).getString(DRAFTS, "[]").orEmpty())
            .filterNot { it.profile == profile && it.game == game && it.id == id }
        val next = JSONArray().apply { all.forEach { put(it.toJson()) } }
        check(prefs(ctx).edit().putString(DRAFTS, next.toString()).commit()) { "Không thể xóa bản nháp." }
    }

    @Synchronized
    fun deleteReport(ctx: Context, profile: String, game: String, id: String) {
        val all = decodeReports(prefs(ctx).getString(REPORTS, "[]").orEmpty())
            .filterNot { it.profile == profile && it.game == game && it.id == id }
        val next = JSONArray().apply { all.forEach { put(it.toJson()) } }
        check(prefs(ctx).edit().putString(REPORTS, next.toString()).commit()) { "Không thể xóa báo lỗi." }
    }

    internal fun encodeDrafts(items: List<CommunityDraft>): String =
        JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

    internal fun decodeDrafts(raw: String): List<CommunityDraft> {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val profile = o.optString("profile")
                val game = o.optString("game")
                val source = o.optString("source")
                val translation = o.optString("translation")
                if (id.isNotBlank() && profile.isNotBlank() && game.isNotBlank() && source.isNotBlank() && translation.isNotBlank()) {
                    add(CommunityDraft(id, profile, game, source, translation, o.optString("contributor"), o.optLong("createdAt")))
                }
            }
        }
    }

    internal fun encodeReports(items: List<CommunityReport>): String =
        JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

    internal fun decodeReports(raw: String): List<CommunityReport> {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val profile = o.optString("profile")
                val game = o.optString("game")
                val source = o.optString("source")
                val reason = o.optString("reason")
                if (id.isNotBlank() && profile.isNotBlank() && game.isNotBlank() && source.isNotBlank() && reason.isNotBlank()) {
                    add(CommunityReport(id, profile, game, source, reason, o.optLong("createdAt")))
                }
            }
        }
    }

    private fun CommunityDraft.toJson() = JSONObject()
        .put("id", id).put("profile", profile).put("game", game)
        .put("source", source).put("translation", translation)
        .put("contributor", contributor).put("createdAt", createdAt)

    private fun CommunityReport.toJson() = JSONObject()
        .put("id", id).put("profile", profile).put("game", game)
        .put("source", source).put("reason", reason).put("createdAt", createdAt)
}
