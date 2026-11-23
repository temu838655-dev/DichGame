package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Chạy socket localhost thật, không cần Android hoặc TV. */
class WebSubServerTvPairTest {
    private data class Http(val status: Int, val text: String, val cors: String?)
    private fun get(port: Int, path: String): Http {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.connectTimeout = 1500
        c.readTimeout = 2500
        try {
            val status = c.responseCode
            val body = (if (status < 400) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Http(status, body, c.getHeaderField("Access-Control-Allow-Origin"))
        } finally { c.disconnect() }
    }

    @Test fun codePairsTvAndAllowsPollingOnlyWithValidToken() {
        val server = WebSubServer(0)
        val port = server.start()
        try {
            assertEquals(8, server.tvPairCode.length)
            assertTrue(server.tvPairCode.all { it in '0'..'9' })
            assertEquals(403, get(port, "/tv-poll?token=notvalid").status)
            val wrong = "9".repeat(8).takeIf { it != server.tvPairCode } ?: "8".repeat(8)
            assertEquals(403, get(port, "/tv-pair?code=$wrong").status)
            val pair = get(port, "/tv-pair?code=${server.tvPairCode}")
            assertEquals(200, pair.status)
            assertEquals("*", pair.cors)
            val token = JSONObject(pair.text).getString("token")
            assertEquals(48, token.length)
            assertEquals(200, get(port, "/tv-poll?token=$token&since=-1").status)
            server.push("Hello & <game> xin chào")
            val result = get(port, "/tv-poll?token=$token&since=-1")
            assertEquals(200, result.status)
            assertEquals("Hello & <game> xin chào", JSONObject(result.text).getString("text"))
            assertEquals("*", result.cors)
        } finally {
            server.stop()
        }
    }

    @Test fun excessivePairAttemptsAreRejected() {
        val server = WebSubServer(0)
        val port = server.start()
        try {
            val wrong = if (server.tvPairCode != "99999999") "99999999" else "11111111"
            repeat(8) { assertEquals(403, get(port, "/tv-pair?code=$wrong").status) }
            assertEquals(403, get(port, "/tv-pair?code=${server.tvPairCode}").status)
        } finally { server.stop() }
    }
}
