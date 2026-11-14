package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class GamePcmAudioTest {
    @Test fun keepsMonoSamplesAndNormalizesToFloat() {
        val out = GamePcmAudio.toMonoFloat16k(shortArrayOf(0, 16384, -32768), 16_000, 1)
        assertEquals(3, out.size)
        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
        assertEquals(-1f, out[2], 0.0001f)
    }

    @Test fun downmixesStereoBeforeResampling() {
        val input = shortArrayOf(
            1000, 3000, 2000, 4000, 3000, 5000,
            4000, 6000, 5000, 7000, 6000, 8000
        )
        val out = GamePcmAudio.toMonoFloat16k(input, 48_000, 2)
        assertEquals(2, out.size)
        assertEquals(2000 / 32768f, out[0], 0.0001f)
        assertEquals(5000 / 32768f, out[1], 0.0001f)
    }

    @Test fun rejectsUnknownOrMalformedPcm() {
        assertEquals(0, GamePcmAudio.toMonoFloat16k(shortArrayOf(), 48_000, 2).size)
        assertEquals(0, GamePcmAudio.toMonoFloat16k(shortArrayOf(1), 48_000, 2).size)
        assertEquals(0, GamePcmAudio.toMonoFloat16k(shortArrayOf(1, 2), 0, 1).size)
        assertEquals(0, GamePcmAudio.toMonoFloat16k(shortArrayOf(1, 2), 48_000, 3).size)
    }
}
