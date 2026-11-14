package com.dichgame.app

import kotlin.math.sqrt

/**
 * Splits normalized 16 kHz mono PCM into speech-like utterances.
 *
 * Feed this only from a dedicated worker thread, never from Chiaki's decoder callback.
 * The detector deliberately keeps pre-roll and accepts short voiced segments so brief
 * dialogue such as "No!", "Stop!" or "Help!" is not discarded by a long minimum duration.
 */
class GameAudioSegmenter(
    private val sampleRate: Int = GamePcmAudio.ASR_SAMPLE_RATE,
    private val frameDurationMs: Int = 20,
    private val speechRmsThreshold: Float = 0.008f,
    private val minSpeechDurationMs: Int = 80,
    private val trailingSilenceMs: Int = 440,
    private val keptTrailingSilenceMs: Int = 120,
    private val preRollMs: Int = 160,
    private val maxUtteranceMs: Int = 12_000,
    private val onUtterance: (FloatArray) -> Unit,
) {
    private val frameSamples = (sampleRate * frameDurationMs / 1000).coerceAtLeast(1)
    private val minSpeechSamples = sampleRate * minSpeechDurationMs / 1000
    private val trailingSilenceFrames = (trailingSilenceMs / frameDurationMs).coerceAtLeast(1)
    private val keptTrailingFrames = (keptTrailingSilenceMs / frameDurationMs).coerceAtLeast(0)
    private val preRollFrames = (preRollMs / frameDurationMs).coerceAtLeast(0)
    private val maxUtteranceSamples = sampleRate * maxUtteranceMs / 1000

    private val pending = ArrayList<Float>(frameSamples * 2)
    private val preRoll = ArrayDeque<FloatArray>()
    private val utterance = ArrayList<Float>()
    private var active = false
    private var voicedSamples = 0
    private var silenceFrames = 0

    init {
        require(sampleRate > 0)
        require(frameDurationMs > 0)
        require(speechRmsThreshold > 0f)
        require(minSpeechDurationMs >= 0)
        require(maxUtteranceMs > minSpeechDurationMs)
    }

    @Synchronized
    fun accept(samples: FloatArray) {
        if (samples.isEmpty()) return
        samples.forEach { pending.add(it.coerceIn(-1f, 1f)) }
        while (pending.size >= frameSamples) {
            val frame = FloatArray(frameSamples) { pending[it] }
            pending.subList(0, frameSamples).clear()
            processFrame(frame)
        }
    }

    /**
     * Processes the final incomplete frame instead of silently discarding it.
     * This matters when the audio stream stops between the normal 20 ms frame boundaries.
     */
    @Synchronized
    fun flush() {
        val finalFrameWasUnvoiced = if (pending.isNotEmpty()) {
            val finalFrame = pending.toFloatArray()
            pending.clear()
            !processFrame(finalFrame)
        } else {
            false
        }
        if (active) emit(keepTrailing = finalFrameWasUnvoiced)
        pending.clear()
        preRoll.clear()
    }

    @Synchronized
    fun reset() {
        pending.clear()
        preRoll.clear()
        utterance.clear()
        active = false
        voicedSamples = 0
        silenceFrames = 0
    }

    /** Returns whether this frame crossed the speech-energy threshold. */
    private fun processFrame(frame: FloatArray): Boolean {
        val rms = sqrt(frame.sumOf { (it * it).toDouble() } / frame.size).toFloat()
        val voiced = rms >= speechRmsThreshold

        if (!active) {
            if (voiced) {
                active = true
                preRoll.forEach { utterance.addAll(it.toList()) }
                utterance.addAll(frame.toList())
                voicedSamples += frame.size
                silenceFrames = 0
                preRoll.clear()
            } else {
                rememberPreRoll(frame)
            }
            return voiced
        }

        utterance.addAll(frame.toList())
        if (voiced) {
            voicedSamples += frame.size
            silenceFrames = 0
        } else {
            silenceFrames++
        }

        if (utterance.size >= maxUtteranceSamples) {
            emit(keepTrailing = true)
        } else if (silenceFrames >= trailingSilenceFrames) {
            emit(keepTrailing = true)
        }
        return voiced
    }

    private fun rememberPreRoll(frame: FloatArray) {
        if (preRollFrames == 0) return
        preRoll.addLast(frame)
        while (preRoll.size > preRollFrames) preRoll.removeFirst()
    }

    private fun emit(keepTrailing: Boolean) {
        val trailingToRemove = if (keepTrailing) {
            (silenceFrames - keptTrailingFrames).coerceAtLeast(0) * frameSamples
        } else {
            silenceFrames * frameSamples
        }
        val end = (utterance.size - trailingToRemove).coerceAtLeast(0)
        if (voicedSamples >= minSpeechSamples && end > 0) {
            onUtterance(utterance.take(end).toFloatArray())
        }
        utterance.clear()
        preRoll.clear()
        active = false
        voicedSamples = 0
        silenceFrames = 0
    }
}
