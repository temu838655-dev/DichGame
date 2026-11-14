package com.dichgame.app

import kotlin.math.roundToInt

/**
 * Converts decoded Chiaki PCM into mono 16 kHz float samples for sherpa-onnx.
 * Run this on an ASR worker thread, never on Chiaki's decoder callback thread.
 */
object GamePcmAudio {
    const val ASR_SAMPLE_RATE = 16_000

    /**
     * Downmixes interleaved signed 16-bit PCM to mono and linearly resamples it.
     * Returns an empty array for invalid input rather than guessing its format.
     */
    fun toMonoFloat16k(samples: ShortArray, sampleRate: Int, channels: Int): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0 || channels !in 1..2 ||
            samples.size % channels != 0
        ) return FloatArray(0)

        val inputFrames = samples.size / channels
        if (inputFrames == 0) return FloatArray(0)

        val mono = FloatArray(inputFrames) { frame ->
            val offset = frame * channels
            val value = if (channels == 1) samples[offset].toInt()
            else (samples[offset].toInt() + samples[offset + 1].toInt()) / 2
            value / 32768.0f
        }
        if (sampleRate == ASR_SAMPLE_RATE) return mono

        val outputFrames = (inputFrames.toDouble() * ASR_SAMPLE_RATE / sampleRate)
            .roundToInt().coerceAtLeast(1)
        return FloatArray(outputFrames) { outIndex ->
            val sourcePosition = outIndex.toDouble() * sampleRate / ASR_SAMPLE_RATE
            val left = sourcePosition.toInt().coerceIn(0, inputFrames - 1)
            val right = (left + 1).coerceAtMost(inputFrames - 1)
            val fraction = (sourcePosition - left).toFloat().coerceIn(0f, 1f)
            mono[left] + (mono[right] - mono[left]) * fraction
        }
    }
}
