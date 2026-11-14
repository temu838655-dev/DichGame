package com.dichgame.app

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

class GameAudioAsrException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Offline multilingual Whisper inference for audio decoded from the PS Remote Play stream.
 *
 * Model files are deliberately installed separately from the APK. Expected files:
 * encoder.onnx, decoder.onnx and tokens.txt in the supplied directory.
 * The caller must invoke this class from a worker thread, never from Chiaki's decoder callback.
 */
class OfflineGameAsr(
    modelDirectory: File,
    language: String = "auto",
    private val numThreads: Int = 2,
) : AutoCloseable {
    private val recognizer: OfflineRecognizer
    private var closed = false

    init {
        require(numThreads > 0) { "numThreads must be positive" }
        val encoder = File(modelDirectory, "encoder.onnx")
        val decoder = File(modelDirectory, "decoder.onnx")
        val tokens = File(modelDirectory, "tokens.txt")
        if (!encoder.isFile || !decoder.isFile || !tokens.isFile) {
            throw GameAudioAsrException(
                "Thiếu model Whisper. Cần encoder.onnx, decoder.onnx và tokens.txt."
            )
        }
        try {
            recognizer = OfflineRecognizer(
                null,
                OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = TARGET_SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = encoder.absolutePath,
                            decoder = decoder.absolutePath,
                            language = language.ifBlank { "auto" },
                            task = "transcribe",
                            enableTokenTimestamps = true,
                            enableSegmentTimestamps = true,
                        ),
                        tokens = tokens.absolutePath,
                        numThreads = numThreads,
                        debug = false,
                        provider = "cpu",
                        modelType = "whisper",
                    ),
                )
            )
        } catch (e: Throwable) {
            throw GameAudioAsrException(
                "Không nạp được model nhận dạng giọng nói: ${e.message ?: e.javaClass.simpleName}", e
            )
        }
    }

    /**
     * Recognize one speech segment. Input can be mono or stereo PCM16 from Chiaki.
     * Segments are limited to Whisper's 30-second window; silence/VAD splitting belongs
     * to the session worker so short dialogue is not discarded here.
     */
    @Synchronized
    fun transcribe(samples: ShortArray, sampleRate: Int, channels: Int): String {
        check(!closed) { "ASR engine is closed" }
        if (sampleRate <= 0 || channels !in 1..2 || samples.isEmpty() ||
            samples.size % channels != 0
        ) return ""
        val mono = toMonoFloat(samples, channels)
        if (mono.size < MIN_SAMPLES) return ""
        val resampled = resample(mono, sampleRate, TARGET_SAMPLE_RATE)
        if (resampled.size < MIN_SAMPLES) return ""
        require(resampled.size <= MAX_SAMPLES) { "Speech segment exceeds 30 seconds; split it before ASR." }

        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(resampled, TARGET_SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } catch (e: Throwable) {
            throw GameAudioAsrException(
                "Nhận dạng âm thanh game thất bại: ${e.message ?: e.javaClass.simpleName}", e
            )
        } finally {
            try { stream.release() } catch (_: Throwable) {}
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            try { recognizer.release() } catch (_: Throwable) {}
        }
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        private const val MIN_SAMPLES = TARGET_SAMPLE_RATE / 5 // 200 ms; keep brief utterances.
        private const val MAX_SAMPLES = TARGET_SAMPLE_RATE * 30

        internal fun toMonoFloat(samples: ShortArray, channels: Int): FloatArray {
            require(channels in 1..2 && samples.size % channels == 0)
            if (channels == 1) return FloatArray(samples.size) { samples[it] / 32768.0f }
            return FloatArray(samples.size / 2) { i ->
                (samples[i * 2].toInt() + samples[i * 2 + 1].toInt()) / 65536.0f
            }
        }

        /** Linear resampling is adequate for speech ASR; it does not alter the original playback. */
        internal fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
            require(sourceRate > 0 && targetRate > 0)
            if (input.isEmpty()) return FloatArray(0)
            if (sourceRate == targetRate) return input.copyOf()
            val outputSize = ((input.size.toLong() * targetRate) / sourceRate)
                .coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return FloatArray(outputSize) { index ->
                val position = index.toDouble() * sourceRate / targetRate
                val left = position.toInt().coerceIn(0, input.lastIndex)
                val right = (left + 1).coerceAtMost(input.lastIndex)
                val fraction = (position - left).toFloat().coerceIn(0f, 1f)
                input[left] + (input[right] - input[left]) * fraction
            }
        }
    }
}
