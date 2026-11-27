package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class VietsubNoApiRequestTest {
    @Test fun vietnameseSubtitleInAutomaticModeIsReturnedWithoutProviderRequests() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs(app).apply { aiAuto = true }
        val translator = TextTranslator(app, prefs, "en")
        val stats = SessionStats.live
        stats.reset(1L, 100)
        var result: TextTranslator.Tr? = null

        try {
            translator.translateFull("Dạ, em hiểu rồi.", null) { result = it }

            assertEquals("Dạ, em hiểu rồi.", result?.text)
            assertEquals("vietsub", result?.via)
            assertEquals(1L, stats.get("nguyen_van_vi"))
            assertEquals(0L, stats.get("api_dich_gemini"))
            assertEquals(0L, stats.get("api_dich_cloud"))
            assertEquals(0L, stats.get("dich_ai"))
            assertEquals(0L, stats.get("dich_may"))
        } finally {
            translator.close()
        }
    }

    @Test fun vietnameseSourceLanguageNeverStartsTranslation() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs(app).apply { aiAuto = false }
        val translator = TextTranslator(app, prefs, "vi")
        val stats = SessionStats.live
        stats.reset(1L, 100)
        var result: TextTranslator.Tr? = null

        try {
            translator.translateFull("Nội dung Việt hóa có sẵn.", null) { result = it }

            assertEquals("Nội dung Việt hóa có sẵn.", result?.text)
            assertEquals("vietsub", result?.via)
            assertTrue(stats.get("api_dich_gemini") == 0L && stats.get("api_dich_cloud") == 0L)
            assertEquals(0L, stats.get("dich_ai"))
            assertEquals(0L, stats.get("dich_may"))
        } finally {
            translator.close()
        }
    }
}
