package com.dichgame.app

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consumes decoded Chiaki PCM away from the native decoder thread, segments speech,
 * and runs offline ASR sequentially. The owner must detach the Chiaki callback first,
 * then call stopAndJoin(), and only then close the PCM queue.
 */
internal interface GameAudioTranscriber : AutoCloseable {
    fun transcribe(samples: ShortArray, sampleRate: Int, channels: Int): String
}

private class OfflineGameAudioTranscriber(
    modelDirectory: File,
    language: String,
) : GameAudioTranscriber {
    private val engine = OfflineGameAsr(modelDirectory, language)
    override fun transcribe(samples: ShortArray, sampleRate: Int, channels: Int): String =
        engine.transcribe(samples, sampleRate, channels)
    override fun close() = engine.close()
}

internal class GameAudioAsrWorker(
    private val queue: GameAudioPcmQueue,
    private val modelDirectory: File,
    private val language: String = "auto",
    private val onTranscript: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val engineFactory: (File, String) -> GameAudioTranscriber =
        { directory, lang -> OfflineGameAudioTranscriber(directory, lang) },
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    @Volatile private var workerThread: Thread? = null

    @Synchronized
    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return false
        running.set(true)
        workerThread = Thread(::runWorker, "DichGame-Audio-ASR").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /** Stop, drain queued frames, flush the last speech segment, and close the model. */
    fun stopAndJoin(timeoutMs: Long = 5_000L): Boolean {
        require(timeoutMs >= 0L)
        running.set(false)
        val thread = workerThread ?: return true
        if (thread !== Thread.currentThread()) thread.join(timeoutMs)
        return !thread.isAlive
    }

    override fun close() {
        stopAndJoin()
    }

    private fun runWorker() {
        var ownedTranscriber: GameAudioTranscriber? = null
        val segmenter = GameAudioSpeechSegmenter()
        try {
            val activeTranscriber = try {
                engineFactory(modelDirectory, language).also { ownedTranscriber = it }
            } catch (error: Throwable) {
                reportError("Chưa thể khởi động dịch giọng nói: ${error.message ?: error.javaClass.simpleName}")
                return
            }

            while (running.get()) {
                val chunk = try {
                    queue.poll(POLL_TIMEOUT_MS)
                } catch (error: InterruptedException) {
                    if (!running.get()) break
                    Thread.currentThread().interrupt()
                    reportError("Luồng nhận âm thanh bị gián đoạn.")
                    break
                } ?: continue
                processSegments(segmenter.accept(chunk), activeTranscriber)
            }

            // Drain PCM queued just before the native callback was detached.
            while (true) {
                val pending = queue.poll() ?: break
                processSegments(segmenter.accept(pending), activeTranscriber)
            }
            segmenter.flush()?.let { processSegment(it, activeTranscriber) }
        } catch (error: Throwable) {
            reportError("Worker dịch giọng nói gặp lỗi: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            try {
                ownedTranscriber?.close()
            } catch (error: Throwable) {
                reportError("Không thể giải phóng model giọng nói: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun processSegments(
        segments: List<GameAudioSpeechSegmenter.Segment>,
        transcriber: GameAudioTranscriber,
    ) {
        segments.forEach { processSegment(it, transcriber) }
    }

    private fun processSegment(
        segment: GameAudioSpeechSegmenter.Segment,
        transcriber: GameAudioTranscriber,
    ) {
        try {
            val text = transcriber.transcribe(segment.samples, segment.sampleRate, segment.channels).trim()
            if (text.isNotEmpty()) {
                try { onTranscript(text) } catch (_: Throwable) {
                    // UI/consumer callback failures must not kill audio recognition.
                }
            }
        } catch (error: Throwable) {
            reportError("Không nhận dạng được một đoạn thoại: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun reportError(message: String) {
        try { onError(message) } catch (_: Throwable) {
            // Error reporting must never terminate the PCM consumer.
        }
    }

    companion object {
        private const val POLL_TIMEOUT_MS = 100L
    }
}
