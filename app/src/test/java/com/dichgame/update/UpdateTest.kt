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
    @Test fun resumedDownloadRangeMustMatchRequestedOffsetAndBounds() {
        val valid = DownloadRangeParser.parse("bytes 100-199/500", 100, 100, 1000, 500)
        assertNotNull(valid)
        assertEquals(200L, valid!!.endExclusive)
        assertEquals(500L, valid.total)
        assertNull(DownloadRangeParser.parse("bytes 99-199/500", 100, 101, 1000, 500))
        assertNull(DownloadRangeParser.parse("bytes 100-199/500", 100, 99, 1000, 500))
        assertNull(DownloadRangeParser.parse("bytes 100-199/150", 100, 100, 1000, 500))
        assertNull(DownloadRangeParser.parse("bytes 100-1200/1500", 100, 1101, 1000, -1))
    }

    @Test fun resumedDownloadAllowsUnknownTotalButRejectsMalformedHeaders() {
        val valid = DownloadRangeParser.parse("bytes 64-127/*", 64, 64, 1024, -1)
        assertNotNull(valid)
        assertEquals(128L, valid!!.endExclusive)
        assertEquals(-1L, valid.total)
        assertNull(DownloadRangeParser.parse("bytes 64-127/128", 64, -1, 1024, 256))
        assertNull(DownloadRangeParser.parse("not-a-range", 64, 64, 1024, -1))
        assertNull(DownloadRangeParser.parse("bytes 64-63/128", 64, 0, 1024, -1))
    }

}
