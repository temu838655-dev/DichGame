package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class RemotePackTest {
    private val bytes = PackText.formatScopedCsv("GTA VI", listOf("Open the door" to "Mở cửa")).toByteArray()
    private val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun manifest(game: String = "GTA VI", url: String = "https://packs.example.org/gta.csv") =
        JSONObject().put("schema",1).put("game",game).put("version",1).put("url",url).put("bytes",bytes.size).put("sha256",hash).toString()

    @Test fun verifiesValidPack() {
        val m = RemotePack.parse(manifest(), "GTA VI")
        assertEquals("Mở cửa", RemotePack.verify(m, bytes).lines.single().second)
    }

    @Test fun rejectsDifferentGameAndTampering() {
        assertTrue(runCatching { RemotePack.parse(manifest(), "GTA V") }.isFailure)
        assertTrue(runCatching { RemotePack.parse(manifest(url = "http://packs.example.org/gta.csv"), "GTA VI") }.isFailure)
        val m = RemotePack.parse(manifest(), "GTA VI")
        val damaged = bytes.copyOf().apply { this[lastIndex] = 0 }
        assertTrue(runCatching { RemotePack.verify(m, damaged) }.isFailure)
        val wrong = RemotePack.parse(manifest("GTA V"), "GTA V")
        assertTrue(runCatching { RemotePack.verify(wrong, bytes) }.isFailure)
    }
}
