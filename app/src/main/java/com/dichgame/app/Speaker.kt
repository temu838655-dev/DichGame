package com.dichgame.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.LruCache
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Đọc to tiếng Việt. Hai kiểu giọng: giọng của máy, hoặc giọng AI Gemini (cần khóa, lỗi thì tự lùi về giọng máy).
 * Theo cài đặt: giọng, tốc độ và "đọc theo cảm xúc".
 */
class Speaker(ctx: Context, private val p: Prefs, private val onReady: (() -> Unit)? = null) {
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val seq = AtomicInteger(0)
    private val lock = Any()
    private var track: AudioTrack? = null
    private val cache = object : LruCache<String, Pcm>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Pcm): Int = value.data.size
    }

    @Volatile var ready = false
        private set

    /** Được gọi (ở luồng chính) khi giọng AI lỗi; Speaker tự đọc bằng giọng máy thay thế. */
    var onAiError: ((String) -> Unit)? = null
    private var aiErrorShown = false

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("vi", "VN"))
                applyVoice()
                ready = true
                onReady?.invoke()
            }
        }
    }

    /** Tên các giọng tiếng Việt đang cài trên máy. */
    fun voices(): List<String> = try {
        tts?.voices?.filter { it.locale.language == "vi" }?.map { it.name }?.sorted().orEmpty()
    } catch (_: Exception) { emptyList() }

    fun applyVoice() {
        val t = tts ?: return
        val name = p.voice
        if (name.isEmpty()) return
        try {
            val v = t.voices?.firstOrNull { it.name == name }
            if (v != null) t.voice = v
        } catch (_: Exception) {}
    }

    /** Đoán cảm xúc từ chữ: angry, sad, scared, happy hoặc rỗng. Chỉ là luật đơn giản, không dùng AI. */
    fun emotionOf(text: String): String {
        val s = text.lowercase()
        fun has(vararg w: String) = w.any { s.contains(it) }
        return when {
            has("khốn", "chết đi", "giết", "câm miệng", "biến đi", "tức", "điên rồi", "đồ ngu") || text.count { it == '!' } >= 2 -> "angry"
            has("sợ", "cứu", "chạy đi", "trời ơi", "quái vật", "nguy hiểm", "đừng lại gần") -> "scared"
            has("xin lỗi", "buồn", "khóc", "mất rồi", "đau", "nhớ", "tiếc", "chia tay", "cô đơn") -> "sad"
            has("vui", "tuyệt", "cảm ơn", "haha", "yêu", "hay quá", "thành công", "chúc mừng") -> "happy"
            else -> ""
        }
    }

    fun speak(text: String, queue: Boolean = false) {
        if (text.isBlank()) return
        if (p.aiVoiceOn) speakAi(text) else speakSystem(text, queue)
    }

    // ---------------- Giọng máy ----------------

    private fun speakSystem(text: String, queue: Boolean) {
        val t = tts ?: return
        if (!ready) return
        var rate = p.rate10 / 10f
        var pitch = 1f
        if (p.emotion) {
            when (emotionOf(text)) {
                "angry" -> { rate *= 1.12f; pitch = 1.1f }
                "scared" -> { rate *= 1.2f; pitch = 1.2f }
                "sad" -> { rate *= 0.85f; pitch = 0.85f }
                "happy" -> { rate *= 1.05f; pitch = 1.15f }
            }
        }
        t.setSpeechRate(rate.coerceIn(0.3f, 3f))
        t.setPitch(pitch)
        t.speak(text, if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "dg")
    }

    // ---------------- Giọng AI ----------------

    private fun styleFor(text: String): String {
        if (!p.emotion) return ""
        return when (emotionOf(text)) {
            "angry" -> "angry and intense"
            "scared" -> "scared and trembling"
            "sad" -> "sad and soft"
            "happy" -> "cheerful"
            else -> ""
        }
    }

    private fun speakAi(text: String) {
        val my = seq.incrementAndGet()
        stopTrack()
        try { tts?.stop() } catch (_: Exception) {}
        val style = styleFor(text)
        val key = p.aiVoice + "|" + p.ttsModel + "|" + style + "|" + text
        val speed = (p.rate10 / 10f).coerceIn(0.5f, 2.5f)
        exec.execute {
            val cached: Pcm? = cache.get(key)
            val pcm: Pcm = cached ?: try {
                AiSpeech.synth(p, text, style).also { cache.put(key, it) }
            } catch (e: Exception) {
                if (my == seq.get()) main.post { fallback(text, e) }
                return@execute
            }
            if (my == seq.get()) play(pcm, speed)
        }
    }

    private fun fallback(text: String, e: Exception) {
        if (!aiErrorShown) {
            aiErrorShown = true
            val m = if (e is AiException) "Giọng AI lỗi ${e.code}, đọc bằng giọng máy." else "Giọng AI không dùng được, đọc bằng giọng máy."
            onAiError?.invoke(m)
        }
        speakSystem(text, false)
    }

    private fun play(pcm: Pcm, speed: Float) {
        if (pcm.data.isEmpty()) return
        synchronized(lock) {
            try {
                val ch = if (pcm.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(pcm.rate)
                            .setChannelMask(ch)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.data.size.coerceAtLeast(4096))
                    .build()
                t.write(pcm.data, 0, pcm.data.size)
                try { t.playbackParams = PlaybackParams().setSpeed(speed) } catch (_: Exception) {}
                releaseLocked()
                track = t
                t.play()
            } catch (_: Exception) {}
        }
    }

    private fun releaseLocked() {
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
    }

    private fun stopTrack() = synchronized(lock) { releaseLocked() }

    fun stop() {
        seq.incrementAndGet()
        stopTrack()
        try { tts?.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        seq.incrementAndGet()
        stopTrack()
        exec.shutdownNow()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
