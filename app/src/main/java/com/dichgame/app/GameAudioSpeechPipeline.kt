package com.dichgame.app

/**
 * Joins VAD segmentation to the PCM sequence timeline. If the bounded queue
 * drops frames, finish the speech segment before accepting audio after the gap.
 */
internal class GameAudioSpeechPipeline(
    private val segmenter: GameAudioSpeechSegmenter = GameAudioSpeechSegmenter(),
    private val sequenceTracker: GameAudioSequenceTracker = GameAudioSequenceTracker(),
) {
    fun accept(chunk: GameAudioPcmQueue.Chunk): List<GameAudioSpeechSegmenter.Segment> {
        val completed = ArrayList<GameAudioSpeechSegmenter.Segment>(1)
        if (sequenceTracker.hasGap(chunk.sequence)) {
            segmenter.flush()?.let(completed::add)
        }
        completed.addAll(segmenter.accept(chunk))
        return completed
    }

    fun flush(): GameAudioSpeechSegmenter.Segment? = segmenter.flush()
}
