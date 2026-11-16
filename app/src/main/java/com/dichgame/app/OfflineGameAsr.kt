package com.dichgame.app

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
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
    private val speechVad: Vad?
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

        // Optional Silero gate rejects likely music/SFX before Whisper.
        val vadFile = File(modelDirectory, SileroVadModelInstaller.FILE_NAME)
        speechVad = if (vadFile.isFile) {
            try {
                Vad(null, VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = vadFile.absolutePath, threshold = SILERO_THRESHOLD,
                        minSilenceDuration = 0.36f, minSpeechDuration = 0.12f,
                        windowSize = VAD_WINDOW_SAMPLES, maxSpeechDuration = 25.0f
                    ),
                    sampleRate = TARGET_SAMPLE_RATE, numThreads = 1, provider = "cpu", debug = false
                ))
            } catch (_: Throwable) { null }
        } else null
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
        if (mono.isEmpty()) return ""
        // MIN_SAMPLES is measured at the 16 kHz Whisper rate, never against source-rate PCM.
        val resampled = resample(mono, sampleRate, TARGET_SAMPLE_RATE)
        if (resampled.size < MIN_SAMPLES) return ""
        if (speechVad != null && !passesSpeechGate(resampled)) return ""
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

    private fun passesSpeechGate(samples: FloatArray): Boolean {
        val vad = speechVad ?: return true
        return try {
            // Reset recurrent VAD state between independent Whisper segments.
            vad.reset()
            val probabilities = ArrayList<Float>((samples.size + VAD_WINDOW_SAMPLES - 1) / VAD_WINDOW_SAMPLES)
            var offset = 0
            while (offset < samples.size) {
                val window = FloatArray(VAD_WINDOW_SAMPLES)
                val count = minOf(VAD_WINDOW_SAMPLES, samples.size - offset)
                samples.copyInto(window, 0, offset, offset + count)
                probabilities += vad.compute(window)
                offset += count
            }
            hasSpeechConfidence(probabilities)
        } catch (_: Throwable) {
            // A VAD runtime problem must not silently discard real game dialogue.
            true
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            try { speechVad?.release() } catch (_: Throwable) {}
            try { recognizer.release() } catch (_: Throwable) {}
        }
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        internal const val MIN_SAMPLES = TARGET_SAMPLE_RATE * 120 / 1000 // Match VAD's 120 ms floor; retain short dialogue.
        private const val MAX_SAMPLES = TARGET_SAMPLE_RATE * 30
        private const val VAD_WINDOW_SAMPLES = 512
        private const val SILERO_THRESHOLD = 0.5f
        private const val MIN_SILERO_VOICED_WINDOWS = 3
        private const val MIN_SILERO_VOICED_RATIO = 0.18

        /** Keeps brief replies: three confident 32 ms windows are sufficient. */
        internal fun hasSpeechConfidence(probabilities: List<Float>): Boolean {
            if (probabilities.isEmpty()) return false
            val voiced = probabilities.count { it >= SILERO_THRESHOLD }
            return voiced >= MIN_SILERO_VOICED_WINDOWS &&
                voiced.toDouble() / probabilities.size >= MIN_SILERO_VOICED_RATIO
        }

        internal fun toMonoFloat(samples: ShortArray, channels: Int): FloatArray {
            require(channels in 1..2 && samples.size % channels == 0)
            if (channels == 1) return FloatArray(samples.size) { samples[it] / 32768.0f }
            // Normal downmix preserves the center image. If channels are nearly
            // opposite-phase, averaging can erase dialogue; in that exceptional
            // case use the channel with the stronger RMS instead.
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            var mixedEnergy = 0.0
            for (i in 0 until samples.size step 2) {
                val left = samples[i].toInt() / 32768.0
                val right = samples[i + 1].toInt() / 32768.0
                val mixed = (left + right) * 0.5
                leftEnergy += left * left
                rightEnergy += right * right
                mixedEnergy += mixed * mixed
            }
            val frameCount = samples.size / 2
            val strongestEnergy = maxOf(leftEnergy, rightEnergy) / frameCount
            val mixedRms = kotlin.math.sqrt(mixedEnergy / frameCount)
            val strongestRms = kotlin.math.sqrt(strongestEnergy)
            return if (strongestRms > 0.0 && mixedRms < strongestRms * 0.25) {
                val useLeft = leftEnergy >= rightEnergy
                FloatArray(frameCount) { i ->
                    samples[i * 2 + if (useLeft) 0 else 1] / 32768.0f
                }
            } else {
                FloatArray(frameCount) { i ->
                    (samples[i * 2].toInt() + samples[i * 2 + 1].toInt()) / 65536.0f
                }
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
