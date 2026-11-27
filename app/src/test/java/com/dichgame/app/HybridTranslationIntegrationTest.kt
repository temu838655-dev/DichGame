package com.dichgame.app

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class HybridTranslationIntegrationTest {
    private lateinit var context: Context
    private lateinit var prefs: Prefs
    private lateinit var translator: TextTranslator

    @Before fun start() {
        context = RuntimeEnvironment.getApplication()
        prefs = Prefs(context)
        prefs.aiAuto = false
        prefs.engine = Prefs.ENGINE_PACK_GEMINI
        prefs.game = "Harbor"
        translator = TextTranslator(context, prefs, "en")
        SessionStats.live.reset(1, 100)
    }

    @After fun stop() { translator.close() }

    private fun translate(text: String): TextTranslator.Tr {
        var result: TextTranslator.Tr? = null
        translator.translateFull(text, "Captain") { result = it }
        return requireNotNull(result) { "Expected immediate local result" }
    }

    private fun assertNoRequests() {
        for (counter in listOf("api_dich_gemini", "api_dich_cloud", "dich_may")) {
            assertEquals(counter, 0L, SessionStats.live.get(counter))
        }
    }

    @Test fun usesImportedDialogueAndGlossaryBeforeCallingGemini() {
        PackStore.addLines(context, prefs.profile, listOf("Open the gate." to "Mở cổng ra."))
        prefs.glossary = "Harbor Key=Chìa khóa cảng"
        for ((source, expected) in listOf("Open the gate." to "Mở cổng ra.", "Harbor Key" to "Chìa khóa cảng")) {
            val result = translate(source)
            assertEquals(expected, result.text)
            assertEquals("goi", result.via)
            assertFalse(result.uncertain)
        }
        assertNoRequests()
    }

    @Test fun missingKeyAndMissingTranslationKeepSourceAndMarkMiss() {
        var ready = false
        translator.prepareForPlayback { ready = it }
        assertTrue(ready)
        val source = "Lower the drawbridge."
        val result = translate(source)
        assertEquals(source, result.text)
        assertEquals("goi_thieu", result.via)
        assertTrue(result.uncertain)
        assertNoRequests()
    }
}
