package com.dichgame.app

import android.os.Looper
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class SystemSpeechStartupTest {
    @Test fun stopBeforeMainThreadDeliveryDiscardsOldDialogueAndAllowsReplay() {
        val app = RuntimeEnvironment.getApplication()
        val speaker = Speaker(app, Prefs(app).apply { voiceEngine = "system" })
        try {
            SessionStats.live.reset(1, 100)
            speaker.speak("Mở cổng thành")
            speaker.onSystemInitialized(TextToSpeech.SUCCESS)
            speaker.stop()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0L, SessionStats.live.get("doc_doc"))
            speaker.speak("Mở cổng thành")
            assertEquals(1L, SessionStats.live.get("doc_doc"))
            speaker.speak("Mở cổng thành")
            assertEquals(1L, SessionStats.live.get("doc_doc"))
            speaker.stop()
            speaker.speak("Mở cổng thành")
            assertEquals(2L, SessionStats.live.get("doc_doc"))
        } finally {
            speaker.shutdown()
        }
    }
}
