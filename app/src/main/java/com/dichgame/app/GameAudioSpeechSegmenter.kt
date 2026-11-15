package com.dichgame.app

import kotlin.math.sqrt

/**
 * Turns decoded Chiaki PCM frames into bounded speech segments using an energy VAD.
 *
 * Keeps a short pre-roll so the first consonant of a brief line is not clipped,
 * waits for a short silence hangover before emitting, and caps segment duration
 * below Whisper's 30-second input window. Long continuous speech segments overlap
 * slightly so a phoneme at a segment boundary is available to both inferences.
 * This class has no Android dependencies.
 */
class GameAudioSpeechSegmenter(
    private val threshold: Double = DEFAULT_RMS_THRESHOLD,
    private val silenceMs: Long = DEFAULT_SILENCE_MS,
    private val preRollMs: Long = DEFAULT_PRE_ROLL_MS,
    private val minSpeechMs: Long = DEFAULT_MIN_SPEECH_MS,
    private val maxSegmentMs: Long = DEFAULT_MAX_SEGMENT_MS,
    private val splitOverlapMs: Long = DEFAULT_SPLIT_OVERLAP_MS,
) {
    data class Segment(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long,
    )

    private data class Frame(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int,
        val voiced: Boolean,
    ) {
        val durationMs: Long
            get() = (samples.size.toLong() * 1000L / channels / sampleRate).coerceAtLeast(1L)
    }

    private val preRoll = ArrayDeque<Frame>()
    private val speech = ArrayList<Frame>()
    private var sampleRate = 0
    private var channels = 0
    private var preRollDuration = 0L
    private var speechDuration = 0L
    private var silenceDuration = 0L
    private var voicedDuration = 0L
    private var active = false

    init {
        require(threshold > 0.0 && threshold < 1.0)
        require(silenceMs > 0) { "Silence hangover must be positive to avoid ending every voiced frame." }
        require(preRollMs >= 0 && minSpeechMs >= 0 && maxSegmentMs > 0)
        require(splitOverlapMs >= 0 && splitOverlapMs < maxSegmentMs) {
            "Split overlap must be shorter than the maximum segment duration."
        }
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

        // Chiaki normally delivers short decoder frames, but never assume that:
        // split oversized callbacks into ~20 ms analysis frames so one unusually
        // large callback cannot push a segment far beyond the Whisper input limit.
        val samplesPerFrame = (chunk.sampleRate / ANALYSIS_FRAMES_PER_SECOND)
            .coerceAtLeast(1) * chunk.channels
        var offset = 0
        while (offset < chunk.samples.size) {
            val end = (offset + samplesPerFrame).coerceAtMost(chunk.samples.size)
            val frameSamples = chunk.samples.copyOfRange(offset, end)
            val voiced = rms(frameSamples) >= threshold
            processFrame(
                Frame(frameSamples, chunk.sampleRate, chunk.channels, voiced),
                output,
            )
            offset = end
        }
        return output
    }

    private fun processFrame(frame: Frame, output: MutableList<Segment>) {
        if (!active) {
            if (frame.voiced) {
                active = true
                preRoll.forEach { appendSpeech(it) }
                preRoll.clear()
                preRollDuration = 0L
                appendSpeech(frame)
                voicedDuration += frame.durationMs
                if (speechDuration >= maxSegmentMs) {
                    finishAtLimit()?.let(output::add)
                }
            } else {
                preRoll.addLast(frame)
                preRollDuration += frame.durationMs
                while (preRollDuration > preRollMs && preRoll.isNotEmpty()) {
                    preRollDuration -= preRoll.removeFirst().durationMs
                }
            }
            return
        }

        appendSpeech(frame)
        if (frame.voiced) {
            voicedDuration += frame.durationMs
            silenceDuration = 0L
        } else {
            silenceDuration += frame.durationMs
        }

        // A real pause ends a sentence; do not carry audio across that boundary.
        if (silenceDuration >= silenceMs) {
            finish()?.let(output::add)
        } else if (speechDuration >= maxSegmentMs) {
            finishAtLimit()?.let(output::add)
        }
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
        speech.add(frame)
        speechDuration += frame.durationMs
    }

    /**
     * Finish a full segment but seed the next one with up to [splitOverlapMs]
     * of contiguous voiced tail audio. The overlap protects phonemes crossing
     * an arbitrary Whisper window boundary; ordinary silence boundaries never
     * use it.
     */
    private fun finishAtLimit(): Segment? {
        val tail = ArrayList<Frame>()
        var tailDuration = 0L
        for (frame in speech.asReversed()) {
            if (!frame.voiced) break
            if (tail.isNotEmpty() && tailDuration >= splitOverlapMs) break
            tail.add(frame)
            tailDuration += frame.durationMs
        }
        val result = finish()
        if (result != null && tail.isNotEmpty() && splitOverlapMs > 0L) {
            sampleRate = result.sampleRate
            channels = result.channels
            active = true
            tail.asReversed().forEach {
                appendSpeech(it)
                voicedDuration += it.durationMs
            }
        }
        return result
    }

    private fun finish(): Segment? {
        if (!active || speech.isEmpty()) {
            reset()
            return null
        }
        val totalSamples = speech.sumOf { it.samples.size }
        val combined = ShortArray(totalSamples)
        var offset = 0
        speech.forEach { frame ->
            frame.samples.copyInto(combined, offset)
            offset += frame.samples.size
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
        private const val ANALYSIS_FRAMES_PER_SECOND = 50
        const val DEFAULT_RMS_THRESHOLD = 0.006
        const val DEFAULT_SILENCE_MS = 360L
        const val DEFAULT_PRE_ROLL_MS = 200L
        const val DEFAULT_MIN_SPEECH_MS = 120L
        const val DEFAULT_MAX_SEGMENT_MS = 25_000L
        const val DEFAULT_SPLIT_OVERLAP_MS = 200L
    }
}
