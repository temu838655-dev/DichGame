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
/** Một câu có người nói riêng, để không ép nhiều nhân vật dùng chung một giọng. */
data class VoiceLine(val text: String, val speaker: String?, val delivery: Delivery?)

class Speaker(ctx: Context, private val p: Prefs, private val onReady: (() -> Unit)? = null) {
    private val app = ctx.applicationContext
    private val offline = OfflineVoice(app)
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val seq = AtomicInteger(0)
    @Volatile private var disposed = false
    private val lock = Any()
    private var track: AudioTrack? = null
    private val cache = object : LruCache<String, Pcm>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Pcm): Int = value.data.size
    }

    private val diskCache = VoiceDiskCache(java.io.File(app.cacheDir, "ai-voice-v1"))

    private fun aiPcm(text: String, style: String, voice: String): Pcm {
        val key = VoiceDiskCache.key("gemini-v1", voice, p.ttsModel, style, text)
        cache.get(key)?.let { return it }
        diskCache.get(key)?.let { cache.put(key, it); return it }
        return AiSpeech.synth(p, text, style, voice).also {
            if (it.data.isNotEmpty()) {
                cache.put(key, it)
                diskCache.put(key, it)
            }
        }
    }

    @Volatile var ready = false
        private set

    /** Số câu đang đọc hoặc đang chờ (giọng máy), để biết câu có đang dồn lại không. */
    private val utterances = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val utt = AtomicInteger(0)
    @Volatile private var aiPlayUntil = 0L
    /** Số lần đã bắt đầu phát một đoạn âm thanh (giọng AI hoặc giọng offline); dùng cho bản tự kiểm tra. */
    @Volatile var played = 0
        private set
    private fun finished(id: String?) { if (id != null) utterances.remove(id) }
    private fun submit(task: () -> Unit) {
        if (disposed) return
        try { exec.execute { if (!disposed) task() } } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    /** Được gọi (ở luồng chính) khi giọng AI lỗi; Speaker tự đọc bằng giọng máy thay thế. */
    var onAiError: ((String) -> Unit)? = null
    private var aiErrorShown = false

    init {
        prepare()
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (!disposed && status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("vi", "VN"))
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { finished(utteranceId) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { finished(utteranceId) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { finished(utteranceId) }
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

    /** Cách đọc: theo [d] (từ bước dịch) nếu có, không thì tự đoán từ chữ. Rỗng khi tắt "Đọc theo cảm xúc". */
    private fun deliveryOf(text: String, d: Delivery?): Delivery? = if (p.emotion) (d ?: Emo.detect("", text)) else null

    private fun offlineOn() = p.voiceEngine == "offline" && Voices.installed(app, p.offlineVoice)

    /** Nạp sẵn giọng offline (nếu đang dùng) để câu đầu tiên đọc ngay. */
    fun prepare() {
        if (offlineOn()) try { exec.execute { try { offline.warm(p.offlineVoice) } catch (_: Throwable) {} } } catch (_: Exception) {}
    }

    /** Giải phóng giọng offline (trước khi xóa file giọng). */
    fun release() { try { offline.close() } catch (_: Throwable) {} }

    fun speak(text: String, queue: Boolean = false, d: Delivery? = null, speakerName: String? = null) {
        if (disposed || text.isBlank()) return
        when {
            offlineOn() -> speakOffline(text, d)
            p.aiVoiceOn -> speakAi(text, d, speakerName)
            else -> {
                seq.incrementAndGet()
                stopTrack()
                speakSystem(text, queue, d)
            }
        }
    }

    /** Đọc nhiều câu trong một khung thoại bằng đúng giọng của từng nhân vật. */
    fun speakScene(lines: List<VoiceLine>) {
        if (disposed) return
        val items = lines.filter { it.text.isNotBlank() }
        if (items.isEmpty()) return
        val all = items.joinToString(" ") { it.text }
        val last = items.last()
        val multipleVoices = p.aiVoiceOn && p.characterVoices &&
            items.map { p.aiVoiceFor(it.speaker, it.text) }.distinct().size > 1
        if (!multipleVoices) {
            speak(all, false, last.delivery, last.speaker)
            return
        }
        val my = seq.incrementAndGet()
        stopTrack()
        try { tts?.stop() } catch (_: Exception) {}
        submit {
            if (my != seq.get()) return@submit
            try {
                val pcm = synthScenePcm(items, { !disposed && my == seq.get() })
                play(pcm, (p.rate10 / 10f).coerceIn(0.5f, 2.5f), my)
            } catch (e: Exception) {
                if (my == seq.get()) main.post { if (!disposed && my == seq.get()) fallback(all, last.delivery, e) }
            }
        }
    }

    /** Hợp nhất PCM 24 kHz theo thứ tự, thêm nhịp nghỉ ngắn để tách lời hai người. */
    private fun synthScenePcm(items: List<VoiceLine>, current: () -> Boolean): Pcm {
        val chunks = items.map { line ->
            if (!current()) throw java.util.concurrent.CancellationException()
            val voiceName = p.aiVoiceFor(line.speaker, line.text)
            val style = styleFor(line.text, line.delivery)
            aiPcm(line.text, style, voiceName)
        }
        val first = chunks.first()
        if (chunks.any { it.rate != first.rate || it.channels != first.channels })
            throw AiException(0, "Các đoạn giọng AI có tần số âm thanh khác nhau")
        val pause = first.rate * first.channels * 2 / 8 // 125 ms PCM16 im lặng
        val size = chunks.sumOf { it.data.size.toLong() } + pause.toLong() * (chunks.size - 1)
        if (size > 16_000_000L) throw AiException(0, "Đoạn thoại quá dài")
        val audio = ByteArray(size.toInt())
        var pos = 0
        chunks.forEachIndexed { i, pcm ->
            pcm.data.copyInto(audio, pos)
            pos += pcm.data.size
            if (i < chunks.lastIndex) pos += pause
        }
        return Pcm(audio, first.rate, first.channels)
    }

    /** TV nhận một đoạn PCM duy nhất, nhưng từng lời thoại vẫn được tạo bằng giọng riêng. */
    fun synthForTvScene(lines: List<VoiceLine>, cb: (Pcm?, Float) -> Unit) {
        val items = lines.filter { it.text.isNotBlank() }
        if (disposed || items.isEmpty()) { cb(null, 1f); return }
        val last = items.last()
        val multipleVoices = p.aiVoiceOn && p.characterVoices &&
            items.map { p.aiVoiceFor(it.speaker, it.text) }.distinct().size > 1
        if (!multipleVoices) {
            synthForTv(items.joinToString(" ") { it.text }, last.delivery, last.speaker, cb)
            return
        }
        val my = tvSeq.incrementAndGet()
        submit {
            if (my != tvSeq.get()) return@submit
            val pcm = try { synthScenePcm(items, { !disposed && my == tvSeq.get() }) } catch (_: Exception) { null }
            if (!disposed && my == tvSeq.get()) cb(pcm, (p.rate10 / 10f).coerceIn(0.5f, 2.5f))
        }
    }

    // ---------------- Giọng máy ----------------

    private fun speakSystem(text: String, queue: Boolean, d: Delivery?) {
        val t = tts ?: return
        if (!ready) return
        var rate = p.rate10 / 10f
        var pitch = 1f
        deliveryOf(text, d)?.let { rate *= it.rate; pitch = it.pitch }
        // Tự đọc nhanh: câu mới tới lúc câu cũ chưa đọc xong thì để câu cũ đọc nốt, câu mới xếp hàng và đọc nhanh hơn.
        // Dồn quá 3 câu thì bỏ hàng cũ, đọc câu mới nhất.
        val backlog = utterances.size
        val add = queue || (p.autoFast && backlog in 1..2)
        if (p.autoFast && backlog > 0) rate *= (1f + 0.3f * backlog)
        t.setSpeechRate(rate.coerceIn(0.3f, 3f))
        t.setPitch(pitch)
        if (!add) utterances.clear()
        val id = "dg" + utt.incrementAndGet()
        utterances.add(id)
        try {
            if (t.speak(text, if (add) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finished(id)
        } catch (_: Exception) { finished(id) }
    }

    // ---------------- Giọng AI ----------------

    private fun styleFor(text: String, d: Delivery?): String = deliveryOf(text, d)?.style.orEmpty()

    private fun speakAi(text: String, d: Delivery?, speakerName: String?) {
        val my = seq.incrementAndGet()
        stopTrack()
        try { tts?.stop() } catch (_: Exception) {}
        val style = styleFor(text, d)
        val voiceName = p.aiVoiceFor(speakerName, text)
        val backlog = p.autoFast && System.currentTimeMillis() < aiPlayUntil
        val speed = ((p.rate10 / 10f) * (if (backlog) 1.35f else 1f)).coerceIn(0.5f, 2.5f)
        submit {
            if (my != seq.get()) return@submit
            val pcm: Pcm = try {
                aiPcm(text, style, voiceName)
            } catch (e: Exception) {
                if (my == seq.get()) main.post { if (!disposed && my == seq.get()) fallback(text, d, e) }
                return@submit
            }
            play(pcm, speed, my)
        }
    }

    // ---------------- Giọng offline ----------------

    private fun speedFor(text: String, d: Delivery?): Float {
        var v = p.rate10 / 10f
        deliveryOf(text, d)?.let { v *= it.rate }
        if (p.autoFast && System.currentTimeMillis() < aiPlayUntil) v *= 1.3f
        return v.coerceIn(0.5f, 2.2f)
    }

    private fun speakOffline(text: String, d: Delivery?) {
        val my = seq.incrementAndGet()
        stopTrack()
        try { tts?.stop() } catch (_: Exception) {}
        val id = p.offlineVoice
        val speed = speedFor(text, d)
        submit {
            if (my != seq.get()) return@submit // đã có câu mới hơn, bỏ câu này
            val key = "off|$id|${(speed * 10).toInt()}|$text"
            val cached: Pcm? = cache.get(key)
            val pcm: Pcm = cached ?: try {
                offline.synth(id, text, speed).also { if (it.data.isNotEmpty()) cache.put(key, it) }
            } catch (e: Throwable) {
                if (my == seq.get()) main.post { if (!disposed && my == seq.get()) fallback(text, d, e) }
                return@submit
            }
            play(pcm, 1f, my)
        }
    }

    private val tvSeq = AtomicInteger(0)

    /** Giọng offline hoặc giọng AI đang dùng được: loại giọng mà điện thoại tự tạo âm thanh (khác giọng máy). */
    fun makesAudio(): Boolean = offlineOn() || p.aiVoiceOn

    /**
     * Tạo âm thanh (giọng offline hoặc giọng AI) mà không phát trên điện thoại, để gửi sang loa TV.
     * [cb] chạy ở luồng nền, nhận âm thanh và tốc độ cần phát; âm thanh null nếu giọng lỗi.
     * Câu cũ chưa kịp làm mà đã có câu mới thì bị bỏ (không gọi [cb]).
     */
    fun synthForTv(text: String, d: Delivery? = null, speakerName: String? = null, cb: (Pcm?, Float) -> Unit) {
        if (disposed || text.isBlank()) return
        val my = tvSeq.incrementAndGet()
        val useOffline = offlineOn()
        val id = p.offlineVoice
        val speed = if (useOffline) speedFor(text, d) else ((p.rate10 / 10f) * (deliveryOf(text, d)?.rate ?: 1f)).coerceIn(0.5f, 2.5f)
        val style = styleFor(text, d)
        try {
            exec.execute {
                if (my != tvSeq.get()) return@execute
                if (useOffline) {
                    val key = "off|$id|${(speed * 10).toInt()}|$text"
                    val pcm: Pcm? = cache.get(key) ?: try {
                        offline.synth(id, text, speed).also { if (it.data.isNotEmpty()) cache.put(key, it) }
                    } catch (_: Throwable) { null }
                    if (!disposed && my == tvSeq.get()) cb(pcm, 1f) // tốc độ đã nằm trong âm thanh
                } else {
                    val voiceName = p.aiVoiceFor(speakerName, text)
                    val pcm: Pcm? = try {
                        aiPcm(text, style, voiceName)
                    } catch (_: Exception) { null }
                    if (!disposed && my == tvSeq.get()) cb(pcm, speed)
                }
            }
        } catch (_: Exception) { cb(null, 1f) }
    }

    private fun fallback(text: String, d: Delivery?, e: Throwable) {
        if (!aiErrorShown) {
            aiErrorShown = true
            val m = when {
                e is OfflineVoiceException || e is LinkageError -> (e.message ?: "Giọng offline không dùng được") + " Đọc bằng giọng máy."
                e is AiException -> "Giọng AI lỗi ${e.code}, đọc bằng giọng máy."
                else -> "Giọng AI không dùng được, đọc bằng giọng máy."
            }
            onAiError?.invoke(m)
        }
        speakSystem(text, false, d)
    }

    private fun play(pcm: Pcm, speed: Float, generation: Int) {
        if (pcm.data.isEmpty()) return
        synchronized(lock) {
            if (disposed || generation != seq.get()) return
            var candidate: AudioTrack? = null
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
                candidate = t
                if (t.write(pcm.data, 0, pcm.data.size) != pcm.data.size) throw IllegalStateException("PCM write failed")
                try { t.playbackParams = PlaybackParams().setSpeed(speed) } catch (_: Exception) {}
                releaseLocked()
                t.play()
                track = t
                candidate = null
                played++
                val bytesPerSec = (pcm.rate * 2 * (if (pcm.channels == 2) 2 else 1)).coerceAtLeast(1)
                aiPlayUntil = System.currentTimeMillis() + (pcm.data.size * 1000L / bytesPerSec / speed).toLong()
            } catch (_: Exception) {
                try { candidate?.release() } catch (_: Exception) {}
            }
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
        tvSeq.incrementAndGet()
        utterances.clear()
        aiPlayUntil = 0L
        stopTrack()
        try { tts?.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        disposed = true
        utterances.clear()
        tvSeq.incrementAndGet()
        seq.incrementAndGet()
        stopTrack()
        exec.shutdownNow()
        try { offline.close() } catch (_: Throwable) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
