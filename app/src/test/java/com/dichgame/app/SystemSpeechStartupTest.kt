package com.dichgame.app

import android.os.Looper
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class SystemSpeechStartupTest {
    @Test fun newDialogueDuringStartupFollowsTheOlderSubtitle() {
        val app = RuntimeEnvironment.getApplication()
        val speaker = Speaker(app, Prefs(app).apply { voiceEngine = "system" })
        try {
            val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            speaker.speak("Mở cổng thành")
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)
            speaker.speak("Đưa chìa khóa")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("Mở cổng thành", "Đưa chìa khóa"), engine.spokenTextList)
        } finally {
            speaker.shutdown()
        }
    }

    @Test fun stopBeforeMainThreadDeliveryDiscardsOldDialogueAndAllowsReplay() {
        val app = RuntimeEnvironment.getApplication()
        val speaker = Speaker(app, Prefs(app).apply { voiceEngine = "system" })
        try {
            val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            speaker.speak("Mở cổng thành")
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)
            speaker.stop()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(emptyList<String>(), engine.spokenTextList)
            speaker.speak("Mở cổng thành")
            assertEquals(listOf("Mở cổng thành"), engine.spokenTextList)
            speaker.speak("Mở cổng thành")
            assertEquals(listOf("Mở cổng thành"), engine.spokenTextList)
            speaker.stop()
            speaker.speak("Mở cổng thành")
            assertEquals(listOf("Mở cổng thành", "Mở cổng thành"), engine.spokenTextList)
        } finally {
            speaker.shutdown()
        }
    }
}
