package com.dichgame.app

import android.content.Context
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

class OfflineVoiceException(message: String) : Exception(message)

/**
 * Đọc bằng giọng offline: chữ -> phiên âm (ViPhon) -> giọng Piper chạy bằng sherpa-onnx -> âm thanh.
 * Thư viện native chỉ được nạp khi dùng đến lần đầu, nên bật hay tắt tính năng này không ảnh hưởng lúc mở app.
 */
class OfflineVoice(private val ctx: Context) {
    private var tts: OfflineTts? = null
    private var phon: ViPhon? = null
    private var loadedId: String? = null

    /** Giọng đã tải về máy chưa. */
    fun available(id: String): Boolean = Voices.installed(ctx, id)

    @Synchronized
    private fun load(id: String) {
        if (tts != null && loadedId == id && phon != null) return
        close()
        val dir = Voices.dir(ctx, id)
        val model = File(dir, "model.onnx")
        val tokens = File(dir, "tokens.txt")
        if (!model.exists() || !tokens.exists()) throw OfflineVoiceException("Chưa tải giọng offline.")
        try {
            if (phon == null) phon = ctx.assets.open("vi_phon.txt").use { ViPhon(it) }
            val cfg = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(model = model.absolutePath, tokens = tokens.absolutePath),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
                silenceScale = 0.15f,
            )
            tts = OfflineTts(null, cfg)
            loadedId = id
        } catch (e: Throwable) {
            tts = null
            loadedId = null
            throw OfflineVoiceException("Không nạp được giọng offline: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** Nạp sẵn giọng để câu đầu tiên không bị trễ. */
    fun warm(id: String) { load(id) }

    /** Đọc [text] thành âm thanh PCM 16 bit. [speed] 1 là bình thường, lớn hơn là nhanh hơn. */
    @Synchronized
    fun synth(id: String, text: String, speed: Float): Pcm {
        load(id)
        val t = tts ?: throw OfflineVoiceException("Giọng offline chưa sẵn sàng.")
        val sentences = phon?.sentences(text).orEmpty()
        if (sentences.isEmpty()) return Pcm(ByteArray(0), 22050, 1)
        try {
            val gen = GenerationConfig(
                silenceScale = 0.15f,
                speed = speed.coerceIn(0.5f, 2.2f),
                sid = 0,
                phonemeCodepoints = sentences.toTypedArray(),
            )
            val audio = t.generateWithConfig("", gen)
            val f = audio.samples
            if (f.isEmpty()) throw OfflineVoiceException("Giọng offline không tạo ra âm thanh.")
            val out = ByteArray(f.size * 2)
            for (i in f.indices) {
                val v = (f[i].coerceIn(-1f, 1f) * 32767f).toInt()
                out[2 * i] = (v and 0xFF).toByte()
                out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            return Pcm(out, audio.sampleRate, 1)
        } catch (e: OfflineVoiceException) {
            throw e
        } catch (e: Throwable) {
            throw OfflineVoiceException("Giọng offline lỗi: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    @Synchronized
    fun close() {
        try { tts?.release() } catch (_: Throwable) {}
        tts = null
        loadedId = null
    }
}
