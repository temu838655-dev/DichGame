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

    @Test fun catalogSelectsOnlyTheExactGamePlatform() {
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray()
            .put(JSONObject().put("game", "GTA VI").put("platform", "PC")
                .put("manifest", "https://packs.example.org/gta-pc.dichgame.json"))
            .put(JSONObject().put("game", "GTA VI").put("platform", "PS5")
                .put("manifest", "https://packs.example.org/gta-ps5.dichgame.json")))
        assertEquals("https://packs.example.org/gta-ps5.dichgame.json",
            RemotePack.catalogSource(catalog.toString(), "GTA VI", "PS5"))
        assertEquals("https://packs.example.org/gta-pc.dichgame.json",
            RemotePack.catalogSource(catalog.toString(), "GTA VI", "PC"))
        assertNull(RemotePack.catalogSource(catalog.toString(), "GTA VI", "SWITCH"))
    }

    @Test fun catalogNeverFallsBackToAnotherPlatformOrUnscopedLegacyEntry() {
        val catalog = JSONObject().put("schema", 1).put("games", org.json.JSONArray()
            .put(JSONObject().put("game", "GTA VI").put("manifest", "https://packs.example.org/gta.dichgame.json")))
        assertEquals("https://packs.example.org/gta.dichgame.json",
            RemotePack.catalogSource(catalog.toString(), "GTA VI"))
        assertNull(RemotePack.catalogSource(catalog.toString(), "GTA VI", "PS5"))
    }

    @Test fun catalogRejectsDuplicateGamePlatformEntries() {
        val entry = JSONObject().put("game", "GTA VI").put("platform", "PC")
            .put("manifest", "https://packs.example.org/gta.dichgame.json")
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray().put(entry).put(JSONObject(entry.toString())))
        assertTrue(runCatching { RemotePack.catalogSource(catalog.toString(), "GTA VI", "PC") }.isFailure)
    }

    @Test fun catalogRejectsUnknownPlatformAndNonHttpsManifest() {
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray()
            .put(JSONObject().put("game", "GTA VI").put("platform", "PC")
                .put("manifest", "http://packs.example.org/gta.dichgame.json")))
        assertTrue(runCatching { RemotePack.catalogSource(catalog.toString(), "GTA VI", "PLAYSTATION") }.isFailure)
        assertTrue(runCatching { RemotePack.catalogSource(catalog.toString(), "GTA VI", "PC") }.isFailure)
    }

    @Test fun transientCloudFailuresMayRetryButPermanentFailuresDoNot() {
        for (code in listOf(408, 429, 500, 502, 503, 504)) assertTrue(RemotePack.retryableHttp(code))
        for (code in listOf(200, 301, 400, 401, 403, 404, 413)) assertFalse(RemotePack.retryableHttp(code))
    }

    @Test fun versionsDoNotDowngradeOrAcceptConflictingContent() {
        assertEquals(RemotePack.Decision.INSTALL, RemotePack.versionDecision(null, null, 1, hash))
        assertEquals(RemotePack.Decision.SAME, RemotePack.versionDecision(2, hash, 2, hash))
        assertEquals(RemotePack.Decision.REJECT, RemotePack.versionDecision(2, hash, 1, hash))
        assertEquals(RemotePack.Decision.REJECT, RemotePack.versionDecision(2, hash, 2, "a".repeat(64)))
        assertEquals(RemotePack.Decision.INSTALL, RemotePack.versionDecision(2, hash, 3, "a".repeat(64)))
    }

    @Test fun localCorrectionsOverrideUpdatedCloudEntries() {
        val cloud = listOf("Open the door" to "Mở cửa", "Continue" to "Tiếp tục")
        val local = listOf("OPEN THE DOOR" to "Hãy mở cửa")
        val merged = RemotePack.mergeOffline(cloud, local)
        assertEquals(2, merged.size)
        assertEquals("Hãy mở cửa", TransMemory(merged).lookup("Open the door"))
        assertEquals("Tiếp tục", TransMemory(merged).lookup("Continue"))
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
