package com.dichgame.update

import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
    @Test fun buildNumberPrefersBuildTag() {
        assertEquals(1467, Update.buildNo("build-1467", "Bản dựng 9999"))
    }

    @Test fun buildNumberSupportsLegacyVersionTagsByReleaseName() {
        assertEquals(1739, Update.buildNo("v2.0.1739", "Bản dựng 1739 · v2.0.1739"))
    }

    @Test fun buildNumberRejectsUnrecognizedNames() {
        assertNull(Update.buildNo("release-1739", "DichGame 2.0"))
    }

    @Test fun assetUrlRequiresExactRepositoryReleasePath() {
        assertTrue(Update.validAssetUrl(
            "https://github.com/temu838655-dev/DichGame/releases/download/build-1467/DichGame.apk",
            "DichGame.apk"
        ))
        assertFalse(Update.validAssetUrl(
            "https://evil.example/temu838655-dev/DichGame/releases/download/build-1467/DichGame.apk",
            "DichGame.apk"
        ))
        assertFalse(Update.validAssetUrl(
            "https://github.com/temu838655-dev/DichGame/releases/download/build-1467/Other.apk",
            "DichGame.apk"
        ))
        assertFalse(Update.validAssetUrl(
            "https://github.com/temu838655-dev/DichGame/releases/download/build-1467/DichGame.apk?download=1",
            "DichGame.apk"
        ))
    }

    @Test fun digestAcceptsOnlySha256Hex() {
        val digest = "sha256:" + "A".repeat(64)
        assertEquals("a".repeat(64), Update.digest(digest))
        assertNull(Update.digest("sha1:" + "a".repeat(40)))
        assertNull(Update.digest("sha256:bad"))
        assertNull(Update.digest(""))
    }

    @Test fun releaseNotesRemoveAutomationNoiseAndLimitLength() {
        val clean = Update.cleanNotes(
            "Tính năng mới\nCo-Authored-By: Bot\n**Full Changelog**\n\nSửa lỗi"
        )
        assertEquals("Tính năng mới\n\nSửa lỗi", clean)
        assertTrue(Update.cleanNotes("x".repeat(900)).length <= 701)
    }
}
