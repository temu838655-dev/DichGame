package com.dichgame.app

import kotlin.math.sqrt

/**
 * Turns decoded Chiaki PCM frames into bounded speech segments using an energy VAD.
 *
 * Keeps a short pre-roll so the first consonant of a brief line is not clipped,
 * waits for a short silence hangover before emitting, and caps segment duration
 * below Whisper's 30-second input window. This class has no Android dependencies.
 */
class GameAudioSpeechSegmenter(
    private val threshold: Double = DEFAULT_RMS_THRESHOLD,
    private val silenceMs: Long = DEFAULT_SILENCE_MS,
    private val preRollMs: Long = DEFAULT_PRE_ROLL_MS,
    private val minSpeechMs: Long = DEFAULT_MIN_SPEECH_MS,
    private val maxSegmentMs: Long = DEFAULT_MAX_SEGMENT_MS,
) {
    data class Segment(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long,
    )

    private data class Frame(val samples: ShortArray, val sampleRate: Int, val channels: Int) {
        val durationMs: Long
            get() = (samples.size.toLong() * 1000L / channels / sampleRate).coerceAtLeast(1L)
    }

    private val preRoll = ArrayDeque<Frame>()
    private val speech = ArrayList<ShortArray>()
    private var sampleRate = 0
    private var channels = 0
    private var preRollDuration = 0L
    private var speechDuration = 0L
    private var silenceDuration = 0L
    private var voicedDuration = 0L
    private var active = false

    init {
        require(threshold > 0.0 && threshold < 1.0)
        require(silenceMs >= 0 && preRollMs >= 0 && minSpeechMs >= 0 && maxSegmentMs > 0)
        require(maxSegmentMs <= 29_000L) { "Keep segments below Whisper's 30-second window." }
    }

    /** Feed one copied PCM chunk. Returns zero or more completed speech segments. */
    @Synchronized
    fun accept(chunk: GameAudioPcmQueue.Chunk): List<Segment> {
        if (chunk.sampleRate <= 0 || chunk.channels !in 1..2 ||
            chunk.samples.isEmpty() || chunk.samples.size % chunk.channels != 0
        ) return emptyList()

        val output = ArrayList<Segment>(1)
        if (sampleRate != 0 && (sampleRate != chunk.sampleRate || channels != chunk.channels)) {
            finish()?.let(output::add)
            reset()
        }
        sampleRate = chunk.sampleRate
        channels = chunk.channels
        val frame = Frame(chunk.samples.copyOf(), chunk.sampleRate, chunk.channels)
        val voiced = rms(frame.samples) >= threshold

        if (!active) {
            if (voiced) {
                active = true
                preRoll.forEach { appendSpeech(it) }
                preRoll.clear()
                preRollDuration = 0L
                appendSpeech(frame)
                voicedDuration += frame.durationMs
                if (speechDuration >= maxSegmentMs) finish()?.let(output::add)
            } else {
                preRoll.addLast(frame)
                preRollDuration += frame.durationMs
                while (preRollDuration > preRollMs && preRoll.isNotEmpty()) {
                    preRollDuration -= preRoll.removeFirst().durationMs
                }
            }
            return output
        }

        appendSpeech(frame)
        if (voiced) {
            voicedDuration += frame.durationMs
            silenceDuration = 0L
        } else {
            silenceDuration += frame.durationMs
        }

        if (speechDuration >= maxSegmentMs || silenceDuration >= silenceMs) {
            finish()?.let(output::add)
        }
        return output
    }

    /** Flush remaining speech when the Remote Play session stops. */
    @Synchronized
    fun flush(): Segment? = finish()

    @Synchronized
    fun reset() {
        preRoll.clear()
        speech.clear()
        sampleRate = 0
        channels = 0
        preRollDuration = 0L
        speechDuration = 0L
        silenceDuration = 0L
        voicedDuration = 0L
        active = false
    }

    private fun appendSpeech(frame: Frame) {
        speech.add(frame.samples)
        speechDuration += frame.durationMs
    }

    private fun finish(): Segment? {
        if (!active || speech.isEmpty()) {
            reset()
            return null
        }
        val totalSamples = speech.sumOf { it.size }
        val combined = ShortArray(totalSamples)
        var offset = 0
        speech.forEach { samples ->
            samples.copyInto(combined, offset)
            offset += samples.size
        }
        val result = if (voicedDuration >= minSpeechMs) {
            Segment(combined, sampleRate, channels, speechDuration)
        } else null
        reset()
        return result
    }

    private fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            val normalized = sample / 32768.0
            sum += normalized * normalized
        }
        return sqrt(sum / samples.size)
    }

    companion object {
        const val DEFAULT_RMS_THRESHOLD = 0.012
        const val DEFAULT_SILENCE_MS = 360L
        const val DEFAULT_PRE_ROLL_MS = 200L
        const val DEFAULT_MIN_SPEECH_MS = 120L
        const val DEFAULT_MAX_SEGMENT_MS = 25_000L
    }
}
