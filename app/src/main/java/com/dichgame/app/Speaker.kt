package com.dichgame.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.LruCache
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Đọc to tiếng Việt. Ba kiểu giọng: giọng của máy, giọng AI Gemini (cần khóa), hoặc giọng offline đã tải về.
 * Giọng AI và giọng offline lỗi thì tự lùi về giọng máy.
 * Theo cài đặt: giọng, tốc độ và "đọc theo cảm xúc".
 */
class Speaker(ctx: Context, private val p: Prefs, private val onReady: (() -> Unit)? = null) {
    private val app = ctx.applicationContext
    private val offline = OfflineVoice(app)
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

    /** Số câu đang đọc hoặc đang chờ (giọng máy), để biết câu có đang dồn lại không. */
    private val queued = AtomicInteger(0)
    private val utt = AtomicInteger(0)
    @Volatile private var aiPlayUntil = 0L
    private fun dec() { queued.updateAndGet { if (it > 0) it - 1 else 0 } }

    /** Được gọi (ở luồng chính) khi giọng AI lỗi; Speaker tự đọc bằng giọng máy thay thế. */
    var onAiError: ((String) -> Unit)? = null
    private var aiErrorShown = false

    init {
        prepare()
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("vi", "VN"))
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { dec() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { dec() }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { dec() }
                })
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

    private fun offlineOn() = p.voiceEngine == "offline" && Voices.installed(app, p.offlineVoice)

    /** Nạp sẵn giọng offline (nếu đang dùng) để câu đầu tiên đọc ngay. */
    fun prepare() {
        if (offlineOn()) try { exec.execute { try { offline.warm(p.offlineVoice) } catch (_: Throwable) {} } } catch (_: Exception) {}
    }

    /** Giải phóng giọng offline (trước khi xóa file giọng). */
    fun release() { try { offline.close() } catch (_: Throwable) {} }

    fun speak(text: String, queue: Boolean = false) {
        if (text.isBlank()) return
        when {
            offlineOn() -> speakOffline(text)
            p.aiVoiceOn -> speakAi(text)
            else -> speakSystem(text, queue)
        }
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
        // Tự đọc nhanh: câu mới tới lúc câu cũ chưa đọc xong thì để câu cũ đọc nốt, câu mới xếp hàng và đọc nhanh hơn.
        // Dồn quá 3 câu thì bỏ hàng cũ, đọc câu mới nhất.
        val backlog = queued.get()
        val add = queue || (p.autoFast && backlog in 1..2)
        if (p.autoFast && backlog > 0) rate *= (1f + 0.3f * backlog)
        t.setSpeechRate(rate.coerceIn(0.3f, 3f))
        t.setPitch(pitch)
        if (!add) queued.set(0)
        queued.incrementAndGet()
        t.speak(text, if (add) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "dg" + utt.incrementAndGet())
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
        val backlog = p.autoFast && System.currentTimeMillis() < aiPlayUntil
        val speed = ((p.rate10 / 10f) * (if (backlog) 1.35f else 1f)).coerceIn(0.5f, 2.5f)
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

    // ---------------- Giọng offline ----------------

    private fun speedFor(text: String): Float {
        var v = p.rate10 / 10f
        if (p.emotion) {
            v *= when (emotionOf(text)) { "angry" -> 1.1f; "scared" -> 1.15f; "sad" -> 0.88f; "happy" -> 1.05f; else -> 1f }
        }
        if (p.autoFast && System.currentTimeMillis() < aiPlayUntil) v *= 1.3f
        return v.coerceIn(0.5f, 2.2f)
    }

    private fun speakOffline(text: String) {
        val my = seq.incrementAndGet()
        stopTrack()
        try { tts?.stop() } catch (_: Exception) {}
        val id = p.offlineVoice
        val speed = speedFor(text)
        exec.execute {
            if (my != seq.get()) return@execute // đã có câu mới hơn, bỏ câu này
            val key = "off|$id|${(speed * 10).toInt()}|$text"
            val cached: Pcm? = cache.get(key)
            val pcm: Pcm = cached ?: try {
                offline.synth(id, text, speed).also { if (it.data.isNotEmpty()) cache.put(key, it) }
            } catch (e: Throwable) {
                if (my == seq.get()) main.post { fallback(text, e) }
                return@execute
            }
            if (my == seq.get()) play(pcm, 1f)
        }
    }

    private val tvSeq = AtomicInteger(0)

    /**
     * Tạo âm thanh giọng offline mà không phát trên điện thoại, để gửi sang loa TV.
     * [cb] chạy ở luồng nền, nhận null nếu giọng lỗi. Câu cũ chưa kịp làm mà đã có câu mới thì bị bỏ (không gọi [cb]).
     */
    fun synthOffline(text: String, cb: (Pcm?) -> Unit) {
        if (text.isBlank()) return
        val my = tvSeq.incrementAndGet()
        val id = p.offlineVoice
        val speed = speedFor(text)
        try {
            exec.execute {
                if (my != tvSeq.get()) return@execute
                val key = "off|$id|${(speed * 10).toInt()}|$text"
                val pcm: Pcm? = cache.get(key) ?: try {
                    offline.synth(id, text, speed).also { if (it.data.isNotEmpty()) cache.put(key, it) }
                } catch (_: Throwable) { null }
                cb(pcm)
            }
        } catch (_: Exception) { cb(null) }
    }

    private fun fallback(text: String, e: Throwable) {
        if (!aiErrorShown) {
            aiErrorShown = true
            val m = when {
                e is OfflineVoiceException || e is LinkageError -> (e.message ?: "Giọng offline không dùng được") + " Đọc bằng giọng máy."
                e is AiException -> "Giọng AI lỗi ${e.code}, đọc bằng giọng máy."
                else -> "Giọng AI không dùng được, đọc bằng giọng máy."
            }
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
                val bytesPerSec = (pcm.rate * 2 * (if (pcm.channels == 2) 2 else 1)).coerceAtLeast(1)
                aiPlayUntil = System.currentTimeMillis() + (pcm.data.size * 1000L / bytesPerSec / speed).toLong()
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
        try { offline.close() } catch (_: Throwable) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
