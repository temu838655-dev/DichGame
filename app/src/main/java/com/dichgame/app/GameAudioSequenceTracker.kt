package com.dichgame.app

/**
 * Detects missing PCM chunks from the queue's monotonically increasing sequence.
 * A gap means audio was dropped and adjacent chunks must not be stitched together.
 */
internal class GameAudioSequenceTracker {
    private var previousSequence: Long? = null

    fun hasGap(sequence: Long): Boolean {
        val previous = previousSequence
        previousSequence = sequence
        return previous != null && sequence != previous + 1L
    }
}
