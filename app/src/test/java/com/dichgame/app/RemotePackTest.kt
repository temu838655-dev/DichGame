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

    @Test fun pcTranslationSourceCanServeEverySupportedConsole() {
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray()
            .put(JSONObject().put("game", "GTA VI").put("source_platform", "PC")
                .put("target_platforms", org.json.JSONArray().put("PS5").put("PS4").put("XBOX"))
                .put("manifest", "https://packs.example.org/gta-pc-source.dichgame.json")))
        for (console in listOf("PS5", "PS4", "XBOX")) {
            assertEquals("https://packs.example.org/gta-pc-source.dichgame.json",
                RemotePack.catalogSource(catalog.toString(), "GTA VI", console))
        }
    }

    @Test fun catalogLookupIsGameScopedAndIgnoresSourcePlatform() {
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray()
            .put(JSONObject().put("game", "GTA VI").put("source_platform", "PC")
                .put("target_platforms", org.json.JSONArray().put("PS5").put("PS4").put("XBOX"))
                .put("manifest", "https://packs.example.org/gta.dichgame.json")))
        assertEquals(RemotePack.catalogSource(catalog.toString(), "GTA VI", "PS5"),
            RemotePack.catalogSource(catalog.toString(), "GTA VI", "XBOX"))
        assertNull(RemotePack.catalogSource(catalog.toString(), "Red Dead Redemption 2", "PS4"))
    }

    @Test fun catalogRejectsAmbiguousDuplicateGameDatasets() {
        val entry = JSONObject().put("game", "GTA VI").put("source_platform", "PC")
            .put("manifest", "https://packs.example.org/gta.dichgame.json")
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray()
            .put(entry).put(JSONObject(entry.toString()).put("source_platform", "PS5")))
        assertTrue(runCatching { RemotePack.catalogSource(catalog.toString(), "GTA VI", "PS5") }.isFailure)
    }

    @Test fun catalogRejectsNonHttpsManifestButDoesNotRequireConsoleSpecificSource() {
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray()
            .put(JSONObject().put("game", "GTA VI").put("source_platform", "PC")
                .put("target_platforms", org.json.JSONArray().put("PS5").put("PS4").put("XBOX"))
                .put("manifest", "http://packs.example.org/gta.dichgame.json")))
        assertTrue(runCatching { RemotePack.catalogSource(catalog.toString(), "GTA VI", "PS5") }.isFailure)
    }


    @Test fun catalogRequiresExplicitConsoleReusePermission() {
        val base = JSONObject().put("schema", 2).put("games", org.json.JSONArray().put(
            JSONObject().put("game", "GTA VI").put("source_platform", "PC")
                .put("manifest", "https://packs.example.org/gta.dichgame.json")))
        assertTrue(runCatching { RemotePack.catalogSource(base.toString(), "GTA VI", "PS5") }.isFailure)
    }

    @Test fun catalogHonorsTargetRestrictionsForEachConsole() {
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray().put(
            JSONObject().put("game", "GTA VI").put("source_platform", "PC")
                .put("target_platforms", org.json.JSONArray().put("PS5"))
                .put("manifest", "https://packs.example.org/gta.dichgame.json")))
        assertEquals("https://packs.example.org/gta.dichgame.json",
            RemotePack.catalogSource(catalog.toString(), "GTA VI", "PS5"))
        assertTrue(runCatching { RemotePack.catalogSource(catalog.toString(), "GTA VI", "XBOX") }.isFailure)
    }

    @Test fun catalogRejectsPcAsGameplayTarget() {
        val catalog = JSONObject().put("schema", 2).put("games", org.json.JSONArray())
        assertTrue(runCatching { RemotePack.catalogSource(catalog.toString(), "GTA VI", "PC") }.isFailure)
    }

    @Test fun httpsRedirectsMayResolveRelativeLocations() {
        val current = java.net.URL("https://packs.example.org/v1/index.json")
        val target = RemotePack.safeHttpsRedirect(current, "../v2/index.json")
        assertEquals("https://packs.example.org/v2/index.json", target.toExternalForm())
    }

    @Test fun httpsRedirectsRejectDowngradeAndCredentialInjection() {
        val current = java.net.URL("https://packs.example.org/v1/index.json")
        assertTrue(runCatching { RemotePack.safeHttpsRedirect(current, "http://packs.example.org/file.csv") }.isFailure)
        assertTrue(runCatching { RemotePack.safeHttpsRedirect(current, "https://user:pass@packs.example.org/file.csv") }.isFailure)
        assertTrue(runCatching { RemotePack.safeHttpsRedirect(current, "https://packs.example.org/file.csv#fragment") }.isFailure)
        assertTrue(runCatching {
            RemotePack.safeHttpsRedirect(java.net.URL("http://packs.example.org/file.csv"), "/next")
        }.isFailure)
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
