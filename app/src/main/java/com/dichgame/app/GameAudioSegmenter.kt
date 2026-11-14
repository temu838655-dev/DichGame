package com.dichgame.app

import kotlin.math.sqrt

/**
 * Splits normalized 16 kHz mono PCM into speech-like utterances.
 *
 * Feed this only from a dedicated worker thread, never from Chiaki's decoder callback.
 * Primitive FloatArray buffers avoid boxing every sample into Float objects on low-memory TVs.
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

    private val pending = FloatArray(frameSamples)
    private var pendingSize = 0
    private val preRoll = ArrayDeque<FloatArray>()
    private var utterance = FloatArray(frameSamples * 4)
    private var utteranceSize = 0
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
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(frameSamples - pendingSize, samples.size - offset)
            for (i in 0 until count) pending[pendingSize + i] = samples[offset + i].coerceIn(-1f, 1f)
            pendingSize += count
            offset += count
            if (pendingSize == frameSamples) {
                processFrame(pending)
                pendingSize = 0
            }
        }
    }

    /** Processes the final incomplete frame instead of silently discarding it. */
    @Synchronized
    fun flush() {
        val finalFrameWasUnvoiced = if (pendingSize > 0) {
            val finalFrame = pending.copyOf(pendingSize)
            pendingSize = 0
            !processFrame(finalFrame)
        } else {
            false
        }
        if (active) emit(keepTrailing = finalFrameWasUnvoiced)
        pendingSize = 0
        preRoll.clear()
    }

    @Synchronized
    fun reset() {
        pendingSize = 0
        preRoll.clear()
        utteranceSize = 0
        active = false
        voicedSamples = 0
        silenceFrames = 0
    }

    /** Returns whether this frame crossed the speech-energy threshold. */
    private fun processFrame(frame: FloatArray): Boolean {
        if (frame.isEmpty()) return false
        var energy = 0.0
        for (sample in frame) energy += sample.toDouble() * sample.toDouble()
        val rms = sqrt(energy / frame.size).toFloat()
        val voiced = rms >= speechRmsThreshold

        if (!active) {
            if (voiced) {
                active = true
                preRoll.forEach(::appendUtterance)
                appendUtterance(frame)
                voicedSamples += frame.size
                silenceFrames = 0
                preRoll.clear()
            } else {
                rememberPreRoll(frame)
            }
            return voiced
        }

        appendUtterance(frame)
        if (voiced) {
            voicedSamples += frame.size
            silenceFrames = 0
        } else {
            silenceFrames++
        }

        if (utteranceSize >= maxUtteranceSamples) {
            emit(keepTrailing = true)
        } else if (silenceFrames >= trailingSilenceFrames) {
            emit(keepTrailing = true)
        }
        return voiced
    }

    private fun appendUtterance(values: FloatArray) {
        val required = utteranceSize + values.size
        if (required > utterance.size) {
            var capacity = utterance.size.coerceAtLeast(1)
            while (capacity < required) capacity *= 2
            utterance = utterance.copyOf(capacity)
        }
        values.copyInto(utterance, destinationOffset = utteranceSize)
        utteranceSize = required
    }

    private fun rememberPreRoll(frame: FloatArray) {
        if (preRollFrames == 0) return
        preRoll.addLast(frame.copyOf())
        while (preRoll.size > preRollFrames) preRoll.removeFirst()
    }

    private fun emit(keepTrailing: Boolean) {
        val trailingToRemove = if (keepTrailing) {
            (silenceFrames - keptTrailingFrames).coerceAtLeast(0) * frameSamples
        } else {
            silenceFrames * frameSamples
        }
        val end = (utteranceSize - trailingToRemove).coerceAtLeast(0)
        if (voicedSamples >= minSpeechSamples && end > 0) {
            onUtterance(utterance.copyOf(end))
        }
        utteranceSize = 0
        preRoll.clear()
        active = false
        voicedSamples = 0
        silenceFrames = 0
    }
}
