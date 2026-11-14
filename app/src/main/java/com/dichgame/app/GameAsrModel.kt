package com.dichgame.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Tải và quản lý mô hình Whisper tiny đa ngôn ngữ, tùy chọn, chạy hoàn toàn offline. */
object GameAsrModel {
    const val DOWNLOAD_MB = 111
    private const val MODEL_ID = "whisper-tiny-multilingual"
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"

    fun dir(ctx: Context) = File(ctx.filesDir, "models/$MODEL_ID")

    fun installed(ctx: Context): Boolean {
        val d = dir(ctx)
        return File(d, "tiny-encoder.onnx").isFile &&
            File(d, "tiny-decoder.onnx").isFile &&
            File(d, "tiny-tokens.txt").isFile &&
            File(d, "ok").isFile
    }

    /** Chạy ở luồng nền. Trả về null khi thành công, hoặc thông báo lỗi. */
    fun download(
        ctx: Context,
        cancel: AtomicBoolean,
        onProgress: (Int) -> Unit
    ): String? {
        val root = File(ctx.filesDir, "models")
        val tmp = File(root, "$MODEL_ID.tmp")
        val dst = dir(ctx)
        try {
            root.mkdirs()
            val required = DOWNLOAD_MB * 2L * 1024 * 1024
            if (root.usableSpace < required) return "Cần ít nhất khoảng 222 MB bộ nhớ trống để tải và cài mô hình."
            tmp.deleteRecursively()
            tmp.mkdirs()

            val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DichGame")
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return "Không tải được mô hình Whisper (HTTP ${conn.responseCode})."
                }
                val total = conn.contentLengthLong
                var received = 0L
                var lastProgress = -1
                val counted = object : FilterInputStream(conn.inputStream) {
                    override fun read(): Int {
                        val value = super.read()
                        if (value >= 0) update(1)
                        return value
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val count = super.read(b, off, len)
                        if (count > 0) update(count)
                        return count
                    }
                    private fun update(count: Int) {
                        if (cancel.get()) throw CancelledDownload()
                        received += count
                        if (total > 0) {
                            val progress = (received * 99 / total).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
                var encoder = false
                var decoder = false
                var tokens = false
                TarArchiveInputStream(BZip2CompressorInputStream(counted.buffered(1 shl 16))).use { tar ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        if (cancel.get()) throw CancelledDownload()
                        val entry = tar.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val name = entry.name.substringAfterLast('/')
                        val target = when (name) {
                            "tiny-encoder.onnx" -> "tiny-encoder.onnx"
                            "tiny-decoder.onnx" -> "tiny-decoder.onnx"
                            "tiny-tokens.txt" -> "tiny-tokens.txt"
                            else -> null
                        } ?: continue
                        File(tmp, target).outputStream().buffered(1 shl 16).use { output ->
                            while (true) {
                                if (cancel.get()) throw CancelledDownload()
                                val n = tar.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                            }
                        }
                        when (target) {
                            "tiny-encoder.onnx" -> encoder = true
                            "tiny-decoder.onnx" -> decoder = true
                            "tiny-tokens.txt" -> tokens = true
                        }
                    }
                }
                if (!encoder || !decoder || !tokens) {
                    tmp.deleteRecursively()
                    return "Gói Whisper thiếu encoder, decoder hoặc tokens; không cài đặt."
                }
                File(tmp, "ok").writeText("1")
                dst.deleteRecursively()
                if (!tmp.renameTo(dst)) return "Không thể lưu mô hình Whisper vào bộ nhớ ứng dụng."
                onProgress(100)
                return null
            } finally {
                conn.disconnect()
            }
        } catch (_: CancelledDownload) {
            tmp.deleteRecursively()
            return "Đã hủy tải mô hình."
        } catch (_: IOException) {
            tmp.deleteRecursively()
            return "Tải mô hình bị gián đoạn. Hãy kiểm tra mạng rồi thử lại."
        } catch (e: Exception) {
            tmp.deleteRecursively()
            return "Lỗi cài mô hình: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun createRecognizer(ctx: Context): OfflineRecognizer {
        check(installed(ctx)) { "Chưa cài mô hình Whisper." }
        val d = dir(ctx)
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = File(d, "tiny-encoder.onnx").absolutePath,
                decoder = File(d, "tiny-decoder.onnx").absolutePath,
                language = "",
                task = "transcribe",
                tailPaddings = 1000,
            ),
            tokens = File(d, "tiny-tokens.txt").absolutePath,
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = "whisper",
        )
        return OfflineRecognizer(
            null,
            OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = GamePcmAudio.ASR_SAMPLE_RATE, featureDim = 80),
                modelConfig = model,
            ),
        )
    }

    private class CancelledDownload : RuntimeException()
}

/**
 * Dedicated worker: decoder callback only copies PCM into a bounded queue.
 * Whisper inference and segmentation never run on Chiaki's real-time audio thread.
 */
class GameAudioAsr(
    private val context: Context,
    private val onTranscript: (String, String) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val queue = GameAudioPcmQueue(32)
    private val stopped = AtomicBoolean(false)
    private var worker: Thread? = null
    @Volatile private var recognizer: OfflineRecognizer? = null
    @Volatile private var activeSession: com.metallic.chiaki.lib.Session? = null

    fun start(session: com.metallic.chiaki.lib.Session) {
        check(worker == null) { "ASR đã chạy." }
        check(GameAsrModel.installed(context)) { "Chưa tải mô hình Whisper." }
        activeSession = session
        session.audioFrameCallback = { samples, sampleRate, channels ->
            if (!stopped.get()) queue.offer(samples, sampleRate, channels)
        }
        worker = Thread({
            var engine: OfflineRecognizer? = null
            try {
                val activeEngine = GameAsrModel.createRecognizer(context)
                engine = activeEngine
                recognizer = activeEngine
                val segmenter = GameAudioSegmenter(onUtterance = { samples ->
                    if (!stopped.get() && samples.isNotEmpty()) {
                        val stream = activeEngine.createStream()
                        try {
                            stream.acceptWaveform(samples, GamePcmAudio.ASR_SAMPLE_RATE)
                            activeEngine.decode(stream)
                            val result = activeEngine.getResult(stream)
                            val text = result.text.trim()
                            if (text.isNotEmpty()) onTranscript(text, result.lang)
                        } finally {
                            stream.release()
                        }
                    }
                })
                // A full queue drops its oldest PCM chunk. Never join audio from
                // opposite sides of that gap into one Whisper utterance.
                val sequenceTracker = GameAudioSequenceTracker()
                while (!stopped.get()) {
                    val chunk = queue.poll(150) ?: continue
                    if (sequenceTracker.hasGap(chunk.sequence)) {
                        segmenter.reset()
                    }
                    val mono = GamePcmAudio.toMonoFloat16k(
                        chunk.samples, chunk.sampleRate, chunk.channels
                    )
                    segmenter.accept(mono)
                }
                segmenter.flush()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Throwable) {
                if (!stopped.get()) onError(e.message ?: e.javaClass.simpleName)
            } finally {
                recognizer = null
                try { engine?.release() } catch (_: Throwable) {}
            }
        }, "DichGame-Whisper-ASR").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        activeSession?.audioFrameCallback = null
        activeSession = null
        queue.close()
        worker?.interrupt()
        worker = null
        recognizer = null
    }
}
