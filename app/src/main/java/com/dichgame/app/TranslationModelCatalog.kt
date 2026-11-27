package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** Lists models using the configured credential. Selecting and testing generation are separate actions. */
internal object TranslationModelCatalog {
    data class Model(val id: String, val name: String) {
        val label get() = if (id == name) id else "$name · $id"
    }

    fun preferred(models: List<Model>, gemini: Boolean, officialOpenAI: Boolean): Model? {
        if (!gemini) return if (officialOpenAI) models.find { it.id == "gpt-5-mini" } else null
        fun compact(value: String) = value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
        return models.find { compact(it.name) == "gemini35flashlite" || compact(it.id) == "gemini35flashlite" }
    }

    fun cloudModelsUrl(base: String): String {
        val uri = URI(base.trim())
        require(uri.scheme.equals("https", true) && uri.host != null &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) { "Cần địa chỉ API HTTPS hợp lệ." }
        return uri.toString().trimEnd('/').removeSuffix("/chat/completions") + "/models"
    }

    fun list(gemini: Boolean, cloudBase: String, key: String): List<Model> {
        require(key.isNotBlank()) { "Hãy nhập khóa API trước khi tải danh sách model." }
        val endpoint = if (gemini) "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100" else cloudModelsUrl(cloudBase)
        return pages(gemini) { token ->
            val url = if (token.isEmpty()) endpoint else endpoint + "&pageToken=" + URLEncoder.encode(token, "UTF-8")
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                if (gemini) connection.setRequestProperty("x-goog-api-key", key)
                else connection.setRequestProperty("Authorization", "Bearer $key")
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Máy chủ trả HTTP $code khi lấy danh sách model.")
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun pages(gemini: Boolean, fetch: (String) -> JSONObject): List<Model> {
        val models = linkedMapOf<String, Model>()
        val seen = mutableSetOf<String>()
        var token = ""
        do {
            check(seen.add(token) && seen.size <= 100) { "Phân trang danh sách model không hợp lệ." }
            val page = fetch(token)
            val entries = page.getJSONArray(if (gemini) "models" else "data")
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                if (gemini) {
                    val methods = entry.optJSONArray("supportedGenerationMethods") ?: continue
                    if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
                }
                val id = if (gemini) entry.getString("name").removePrefix("models/") else entry.getString("id")
                if (id.isNotBlank()) models[id] = Model(id, entry.optString("displayName", id).ifBlank { id })
            }
            token = if (gemini) page.optString("nextPageToken") else ""
        } while (token.isNotEmpty())
        return models.values.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }
}
