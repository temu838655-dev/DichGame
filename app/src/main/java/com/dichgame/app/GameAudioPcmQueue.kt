package com.dichgame.app

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded, non-blocking handoff for PCM decoded from the Remote Play stream.
 *
 * The native audio callback must never wait for speech recognition. When the
 * consumer falls behind, the oldest chunk is discarded so recognition stays
 * close to live gameplay instead of translating stale dialogue.
 *
 * This queue is infrastructure only: it becomes active after the Chiaki JNI
 * decoder is wired to call [offer]. It does not capture the device microphone.
 */
class GameAudioPcmQueue(capacity: Int = DEFAULT_CAPACITY) : AutoCloseable {
    data class Chunk(
        val sequence: Long,
        val sampleRate: Int,
        val channels: Int,
        val samples: ShortArray
    ) {
        val frameCount: Int get() = samples.size / channels
    }

    private val queue = ArrayBlockingQueue<Chunk>(capacity.also {
        require(it > 0) { "capacity must be positive" }
    })
    private val closed = AtomicBoolean(false)
    private val nextSequence = AtomicLong(0)
    private val dropped = AtomicLong(0)

    /** Number of chunks discarded because the ASR consumer could not keep up. */
    fun droppedChunks(): Long = dropped.get()

    /**
     * Copies PCM data before returning because the native decoder may reuse its
     * output buffer immediately. Never waits for the consumer.
     */
    fun offer(samples: ShortArray, sampleRate: Int, channels: Int): Boolean {
        if (closed.get() || sampleRate <= 0 || channels !in 1..2 ||
            samples.isEmpty() || samples.size % channels != 0
        ) return false

        val chunk = Chunk(
            sequence = nextSequence.getAndIncrement(),
            sampleRate = sampleRate,
            channels = channels,
            samples = samples.copyOf()
        )
        if (closed.get()) return false
        if (queue.offer(chunk)) {
            // close() may race with the enqueue; do not leave audio in a closed queue.
            if (closed.get()) {
                queue.remove(chunk)
                return false
            }
            return true
        }

        // A consumer may have freed a slot between offer() and poll(). Count
        // a drop only when this producer actually removed an older chunk.
        if (queue.poll() != null) dropped.incrementAndGet()
        if (closed.get()) return false
        if (!queue.offer(chunk)) return false
        if (closed.get()) {
            queue.remove(chunk)
            return false
        }
        return true
    }

    /** Returns the next chunk, or null when the timeout expires / queue is closed. */
    fun poll(timeoutMs: Long = 0): Chunk? {
        if (closed.get() && queue.isEmpty()) return null
        return if (timeoutMs <= 0) queue.poll()
        else queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** Clears stale audio when a Remote Play session changes or reconnects. */
    fun clear() {
        queue.clear()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) queue.clear()
    }

    companion object {
        // At 20 ms per decoder frame this holds about 480 ms of audio.
        const val DEFAULT_CAPACITY = 24
    }
}
