package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTinyModelInstallerTest {
    @Test
    fun bundledManifestUsesMultilingualTinyFilesAndStableRuntimeNames() {
        val assets = WhisperTinyModelInstaller.assets()
        assertEquals(
            listOf("encoder.onnx", "decoder.onnx", "tokens.txt"),
            assets.map { it.localName },
        )
        assertEquals(3, assets.size)
        assertTrue(assets.all { it.size > 0L && it.sha256.matches(Regex("[0-9a-f]{64}")) })
        assertTrue(assets.sumOf { it.size } in 100_000_000L..110_000_000L)
    }

    @Test
    fun modelFilesArePinnedAndDoNotUseEnglishOnlyVariant() {
        val assets = WhisperTinyModelInstaller.assets()
        assertTrue(assets.all { !it.remoteName.contains(".en-") })
        assertEquals("tiny-encoder.int8.onnx", assets[0].remoteName)
        assertEquals("tiny-decoder.int8.onnx", assets[1].remoteName)
        assertEquals("tiny-tokens.txt", assets[2].remoteName)
    }
}
