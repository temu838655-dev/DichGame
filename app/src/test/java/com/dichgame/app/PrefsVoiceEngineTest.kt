package com.dichgame.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PrefsVoiceEngineTest {

    @Test fun freshInstallUsesRequestedDefaultsAndExistingSettingsSurviveReload() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("dg", Context.MODE_PRIVATE).edit().clear().commit()
        val fresh = Prefs(app)

        assertTrue(fresh.aiAuto)
        assertEquals("en", fresh.lang)
        assertEquals(0, fresh.style)
        assertEquals(4, fresh.lookaheadCount)
        assertEquals(333, fresh.scanMs)
        assertEquals(OcrQuality.CAREFUL, fresh.ocrQuality)
        assertEquals(100, fresh.subScale)
        assertEquals(0, fresh.lastSubtitleSeconds)

        fresh.aiAuto = false
        fresh.lookaheadCount = 6
        fresh.scanMs = 500
        fresh.lastSubtitleSeconds = 5
        val existing = Prefs(app)
        assertFalse(existing.aiAuto)
        assertEquals(6, existing.lookaheadCount)
        assertEquals(500, existing.scanMs)
        assertEquals(5, existing.lastSubtitleSeconds)
    }

    @Test fun switchingBasicAndAiCategoriesPreservesTheLastAiEngine() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("dg", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = Prefs(app)

        assertEquals("system", prefs.voiceEngine)
        assertEquals("ai", prefs.lastAiVoiceEngine)

        prefs.voiceEngine = "offline"
        assertEquals("offline", prefs.lastAiVoiceEngine)
        prefs.voiceEngine = "system"
        assertEquals("offline", prefs.lastAiVoiceEngine)

        val reloaded = Prefs(app)
        reloaded.voiceEngine = reloaded.lastAiVoiceEngine
        assertEquals("offline", reloaded.voiceEngine)

        reloaded.voiceEngine = "ai"
        reloaded.voiceEngine = "system"
        assertEquals("ai", reloaded.lastAiVoiceEngine)
    }
}
