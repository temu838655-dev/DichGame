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
    private val dropMonitor = GameAudioQueueDropMonitor()
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

    /**
     * Stop and wait until the worker has drained queued PCM, flushed its final segment,
     * and released the recognizer. Intended for a background thread during session teardown.
     * Interruption is deferred until cleanup completes so callers never close the queue early.
     */
    fun stopAndJoinUntilStopped() {
        running.set(false)
        val thread = workerThread ?: return
        if (thread === Thread.currentThread()) return
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    override fun close() { stopAndJoinUntilStopped() }

    private fun runWorker() {
        var ownedTranscriber: GameAudioTranscriber? = null
        val pipeline = GameAudioSpeechPipeline()
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
                processSegments(pipeline.accept(chunk), activeTranscriber)
                reportQueueDrops()
            }

            // Drain PCM queued just before the native callback was detached.
            while (true) {
                val pending = queue.poll() ?: break
                processSegments(pipeline.accept(pending), activeTranscriber)
                reportQueueDrops()
            }
            pipeline.flush()?.let { processSegment(it, activeTranscriber) }
            reportQueueDrops()
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

    /** Report overload away from Chiaki's real-time callback; never block PCM production. */
    private fun reportQueueDrops() {
        val nowMs = System.nanoTime() / 1_000_000L
        if (dropMonitor.shouldWarn(queue.droppedChunks(), nowMs)) {
            reportError("Âm thanh game đến nhanh hơn tốc độ nhận dạng; một số khung thoại đã bị bỏ qua. Hãy giảm tải ứng dụng hoặc kiểm tra thiết bị.")
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

    companion object { private const val POLL_TIMEOUT_MS = 100L }
}
