package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityLocalStoreTest {
    @Test fun draftRoundTripPreservesAttributionAndGameScope() {
        val item = CommunityDraft(
            id = "draft-1", profile = "Mặc định", game = "Elden Ring",
            source = "You Died", translation = "Bạn đã gục ngã",
            contributor = "player-01", createdAt = 123456L
        )
        val decoded = CommunityLocalStore.decodeDrafts(CommunityLocalStore.encodeDrafts(listOf(item)))
        assertEquals(listOf(item), decoded)
        assertEquals("player-01", decoded.single().contributor)
        assertEquals("Elden Ring", decoded.single().game)
    }

    @Test fun reportRoundTripPreservesReasonAndScope() {
        val item = CommunityReport("report-1", "Mặc định", "Hades", "Fated List", "Sai thuật ngữ", 987L)
        val decoded = CommunityLocalStore.decodeReports(CommunityLocalStore.encodeReports(listOf(item)))
        assertEquals(listOf(item), decoded)
    }

    @Test fun malformedOrIncompleteStoredRowsAreIgnored() {
        assertTrue(CommunityLocalStore.decodeDrafts("not-json").isEmpty())
        assertTrue(CommunityLocalStore.decodeReports("""[{},{"id":"x","game":"G"}]""").isEmpty())
    }

    @Test fun draftValidationRejectsMissingGameAndBlankTranslation() {
        assertFalse(runCatching {
            CommunityContributionRules.validateDraft("", "Source text", "Bản dịch", "")
        }.isSuccess)
        assertFalse(runCatching {
            CommunityContributionRules.validateDraft("Game", "Source text", " ", "")
        }.isSuccess)
        assertTrue(runCatching {
            CommunityContributionRules.validateDraft("Game", "Source text", "Bản dịch", "player")
        }.isSuccess)
    }

    @Test fun reportValidationRequiresUsefulReason() {
        assertFalse(runCatching {
            CommunityContributionRules.validateReport("Game", "Quest complete", "")
        }.isSuccess)
        assertTrue(runCatching {
            CommunityContributionRules.validateReport("Game", "Quest complete", "Dịch sai ngữ cảnh")
        }.isSuccess)
    }

    @Test fun cleanRemovesNullBytesAndTrimsWhitespace() {
        assertEquals("A B", CommunityContributionRules.clean("  A\u0000 B  "))
    }
}
