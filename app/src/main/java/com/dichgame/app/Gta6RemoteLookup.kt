package com.dichgame.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public read-only lookup for administrator-verified GTA VI translations. */
internal object Gta6RemoteLookup {
    private const val ENDPOINT = "https://dichgame-gta6-api.temu838655.workers.dev/v1/gta6/lookup"
    private const val MAX_RESPONSE = 16 * 1024

    fun supports(game: String, language: String): Boolean =
        GamePackMatch.selected(game, "Grand Theft Auto VI") && language == "en"

    fun lookup(game: String, language: String, text: String, externalId: String? = null): String? {
        if (!supports(game, language) || text.isBlank()) return null
        if (externalId.isNullOrBlank() && text.length > 700) return null
        val query = buildString {
            if (!externalId.isNullOrBlank()) append("id=").append(URLEncoder.encode(externalId, "UTF-8"))
            if (text.isNotBlank()) {
                if (isNotEmpty()) append('&')
                append("text=").append(URLEncoder.encode(text, "UTF-8"))
            }
        }
        val connection = (URL("$ENDPOINT?$query").openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 2500
            connection.readTimeout = 3000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "DichGame-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val result = JSONObject(bytes.toString(Charsets.UTF_8))
            result.optString("translation").takeIf { result.optBoolean("hit") && it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
