package com.dichgame.app

/**
 * Detects discontinuities in the monotonically increasing PCM callback sequence.
 * A gap means the bounded queue discarded audio (or a producer handoff failed).
 */
internal class GameAudioSequenceTracker {
    private var expectedSequence: Long? = null

    fun hasGap(sequence: Long): Boolean {
        val expected = expectedSequence
        expectedSequence = sequence + 1
        return expected != null && sequence != expected
    }

    fun reset() {
        expectedSequence = null
    }
}
