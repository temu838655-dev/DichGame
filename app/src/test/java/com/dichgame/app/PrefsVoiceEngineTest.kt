package com.dichgame.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PrefsVoiceEngineTest {
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
