package com.dichgame.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 *
 * Lời thoại dồn lại thì xếp hàng đọc lần lượt, KHÔNG cắt câu đang đọc dở (xem [SpeechPolicy]): dồn quá nhiều hoặc câu chờ quá cũ
 * thì mới bỏ, và mỗi lần cắt/bỏ đều được đếm vào thống kê phiên. Với giọng AI/offline, câu kế tiếp được tạo âm thanh sẵn trong lúc câu hiện tại còn đang phát.
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
    @Volatile private var systemInitFailed = false
    private val lock = Any()
    private data class PendingSystem(val text: String, val queue: Boolean, val delivery: Delivery?, val interrupt: Boolean, val speaker: String?)
    private val pendingSystemLock = Any()
    private val pendingSystem = java.util.ArrayDeque<PendingSystem>()
    private val maxPendingSystem = 5
    /** Một đoạn chờ đọc bằng âm thanh tự tạo (giọng AI hoặc offline). */
    private class Clip(
        val gen: Int, val text: String, val norm: String, val speaker: String?, val d: Delivery?, val baseSpeed: Float, val at: Long,
        val synth: (alive: () -> Boolean) -> Pcm,
    ) {
        @Volatile var alive = true
        @Volatile var track: AudioTrack? = null   // đã dựng sẵn và nạp âm thanh, chờ tới lượt
        @Volatile var durMs = 0L
        @Volatile var failed: Throwable? = null
    }

    // Trạng thái hàng chờ âm thanh: chỉ đụng tới khi đang giữ [lock].
    private val clips = SpeechQueue<Clip>()
    private var nowPlaying: Clip? = null
    private var nowEnds = 0L
    private var track: AudioTrack? = null
    private val retired = ArrayList<AudioTrack>()
    private val advance = Runnable { tryPlay() }

    private fun count(key: String, n: Long = 1) { try { SessionStats.live.inc(key, n) } catch (_: Throwable) {} }

    private val cache = object : LruCache<String, Pcm>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Pcm): Int = value.data.size
    }

    private val diskCache = VoiceDiskCache(java.io.File(app.cacheDir, "ai-voice-v1"))

    private fun offlinePcm(id: String, text: String, speed: Float): Pcm {
        val actualSpeed = speed.coerceIn(0.5f, 2.2f)
        val key = VoiceDiskCache.key("piper-v1", id, actualSpeed.toBits().toString(), text)
        cache.get(key)?.let { return it }
        diskCache.get(key)?.let { cache.put(key, it); return it }
        return offline.synth(id, text, actualSpeed).also {
            if (it.data.isNotEmpty()) {
                cache.put(key, it)
                diskCache.put(key, it)
            }
        }
    }

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
    @Volatile var systemLanguageAvailable = false
        private set

    /** Số câu đang đọc hoặc đang chờ (giọng máy), để biết câu có đang dồn lại không. */
    private val utterances = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val utt = AtomicInteger(0)
    /** Số lần đã bắt đầu phát một đoạn âm thanh (giọng AI hoặc giọng offline); dùng cho bản tự kiểm tra. */
    @Volatile var played = 0
        private set

    /** Trạng thái phát thật của AudioTrack, dùng cho kiểm thử smoke trên Android. */
    internal fun isAudioTrackPlaying(): Boolean = synchronized(lock) {
        track?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    private val systemSpeechText = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Engine callbacks: dùng cho thống kê độ trễ và kiểm thử phát giọng thật trên máy Android. */
    var onSpeechStarted: ((String) -> Unit)? = null
    var onSpeechFinished: ((String) -> Unit)? = null
    var onSpeechError: ((String, String) -> Unit)? = null
    var onSpeechWarning: ((String) -> Unit)? = null

    private fun finished(id: String?) { if (id != null) utterances.remove(id) }

    private fun systemStarted(id: String?) {
        val text = id?.let { systemSpeechText[it] } ?: return
        main.post { if (!disposed) onSpeechStarted?.invoke(text) }
    }

    private fun systemFinished(id: String?) {
        if (id == null) return
        utterances.remove(id)
        val text = systemSpeechText.remove(id) ?: return
        main.post { if (!disposed) onSpeechFinished?.invoke(text) }
    }

    private fun systemFailed(id: String?, reason: String) {
        if (id == null) return
        utterances.remove(id)
        val text = systemSpeechText.remove(id) ?: return
        main.post { if (!disposed) onSpeechError?.invoke(text, reason) }
    }

    private fun submit(task: () -> Unit) {
        if (disposed) return
        try { exec.execute { if (!disposed) task() } } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    /** Được gọi (ở luồng chính) khi giọng AI lỗi; Speaker tự đọc bằng giọng máy thay thế. */
    var onAiError: ((String) -> Unit)? = null
    private var aiErrorShown = false

    init {
        prepare()
        tts = TextToSpeech(ctx.applicationContext, ::onSystemInitialized)
    }

    private fun onSystemInitialized(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            systemInitFailed = true
            main.post {
                synchronized(pendingSystemLock) { pendingSystem.clear() }
                if (!disposed) onSpeechError?.invoke("", "Không khởi động được giọng hệ thống (mã $status).")
            }
            return
        }
        systemInitFailed = false
        main.post {
            synchronized(pendingSystemLock) {
                val engine = tts ?: return@post
                if (disposed || ready) return@post
                val languageStatus = engine.setLanguage(Locale("vi", "VN"))
                systemLanguageAvailable = languageStatus >= TextToSpeech.LANG_AVAILABLE
                if (!systemLanguageAvailable) {
                    main.post {
                        if (!disposed) onSpeechWarning?.invoke("Thiết bị chưa có dữ liệu giọng tiếng Việt; giọng hệ thống có thể dùng ngôn ngữ mặc định.")
                    }
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { systemStarted(utteranceId) }
                    override fun onDone(utteranceId: String?) { systemFinished(utteranceId) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { systemFailed(utteranceId, "Giọng hệ thống gặp lỗi khi đọc câu.") }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        systemFailed(utteranceId, "Giọng hệ thống gặp lỗi $errorCode khi đọc câu.")
                    }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (interrupted) {
                            if (utteranceId != null) {
                                utterances.remove(utteranceId)
                                systemSpeechText.remove(utteranceId)
                            }
                        } else systemFailed(utteranceId, "Giọng hệ thống dừng trước khi đọc hết câu.")
                    }
                })
                applyVoice()
                ready = true
                while (pendingSystem.isNotEmpty()) {
                    val item = pendingSystem.removeFirst()
                    speakSystemLocked(item.text, item.queue, item.delivery, item.interrupt, item.speaker)
                }
            }
            onReady?.invoke()
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

    /**
     * Đọc [text]. Câu mới xếp hàng sau câu đang đọc, không cắt ([interrupt] = true thì dừng hết và đọc ngay, dùng cho nút nghe thử).
     * [queue] = true: giọng máy không bao giờ xóa hàng dù dồn nhiều.
     */
    fun speak(text: String, queue: Boolean = false, d: Delivery? = null, speakerName: String? = null, interrupt: Boolean = false) {
        if (disposed) return
        val spoken = LineClean.clean(text)
        if (spoken.isEmpty()) return
        when {
            offlineOn() -> speakOffline(spoken, d, interrupt, speakerName)
            p.aiVoiceOn -> speakAi(spoken, d, speakerName, interrupt)
            else -> speakSystem(spoken, queue, d, interrupt, speakerName)
        }
    }

    /** Đọc nhiều câu trong một khung thoại bằng đúng giọng của từng nhân vật. */
    fun speakScene(lines: List<VoiceLine>) {
        if (disposed) return
        val items = lines.mapNotNull { line ->
            val spoken = LineClean.clean(line.text)
            if (spoken.isEmpty()) null else line.copy(text = spoken)
        }
        if (items.isEmpty()) return
        val all = items.joinToString(" ") { it.text }
        val last = items.last()
        val multipleVoices = p.aiVoiceOn && p.characterVoices &&
            items.map { p.aiVoiceFor(it.speaker, it.text) }.distinct().size > 1
        if (!multipleVoices) {
            speak(all, false, last.delivery, last.speaker)
            return
        }
        enqueue(all, last.delivery, (p.rate10 / 10f).coerceIn(0.5f, 2.5f), false, org.json.JSONArray(items.map { it.speaker.orEmpty() }).toString()) { alive -> synthScenePcm(items, alive) }
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
        val items = lines.mapNotNull { line ->
            val spoken = LineClean.clean(line.text)
            if (spoken.isEmpty()) null else line.copy(text = spoken)
        }
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

    @Volatile private var lastSysSpeaker: String? = null
    @Volatile private var lastSysNorm = ""
    @Volatile private var lastSysAt = 0L

    private fun speakSystem(text: String, queue: Boolean, d: Delivery?, interrupt: Boolean = false, speaker: String? = null) {
        synchronized(pendingSystemLock) { speakSystemLocked(text, queue, d, interrupt, speaker) }
    }

    private fun speakSystemLocked(text: String, queue: Boolean, d: Delivery?, interrupt: Boolean, speaker: String?) {
        if (disposed) return
        if (systemInitFailed) {
            main.post { if (!disposed) onSpeechError?.invoke(text, "Giọng hệ thống không khởi động được trên thiết bị.") }
            return
        }
        val t = tts
        if (t == null || !ready) {
            if (interrupt) pendingSystem.clear()
            val norm = SpeechPolicy.norm(text)
            if (pendingSystem.peekLast()?.let { SpeechPolicy.sameSpeaker(it.speaker, speaker) && SpeechPolicy.norm(it.text) == norm } == true) return
            while (pendingSystem.size >= maxPendingSystem) pendingSystem.removeFirst()
            pendingSystem.addLast(PendingSystem(text, queue, d, interrupt, speaker))
            return
        }
        // Cùng một câu gửi lại liền nhau (chữ OCR nháy, bản dịch được sửa lại) thì chỉ đọc một lần.
        val norm = SpeechPolicy.norm(text)
        val now = SystemClock.elapsedRealtime()
        if (!interrupt && norm.isNotEmpty() && norm == lastSysNorm && SpeechPolicy.sameSpeaker(lastSysSpeaker, speaker) && now - lastSysAt < SpeechPolicy.DUPLICATE_MS) return
        lastSysSpeaker = speaker
        lastSysNorm = norm
        lastSysAt = now
        var rate = p.rate10 / 10f
        var pitch = 1f
        deliveryOf(text, d)?.let { rate *= it.rate; pitch = it.pitch }
        // Câu mới tới lúc câu cũ chưa đọc xong thì xếp hàng đọc sau (không cắt). Dồn tới SYSTEM_MAX_BACKLOG câu mới xóa hàng, đọc câu mới nhất.
        val backlog = utterances.size
        val flush = interrupt || (!queue && backlog >= SpeechPolicy.SYSTEM_MAX_BACKLOG)
        if (flush && !interrupt && backlog > 0) {
            count("doc_cat")
            if (backlog > 1) count("doc_bo", backlog - 1L)
        } else if (!flush && backlog > 0) count("doc_cho")
        rate *= SpeechPolicy.speedFactor(if (flush) 0 else backlog, p.autoFast)
        t.setSpeechRate(rate.coerceIn(0.3f, 3f))
        t.setPitch(pitch)
        if (flush) {
            utterances.clear()
            systemSpeechText.clear()
        }
        val id = "dg" + utt.incrementAndGet()
        utterances.add(id)
        systemSpeechText[id] = text
        count("doc_doc")
        try {
            if (t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) {
                utterances.remove(id)
                systemSpeechText.remove(id)
                main.post { if (!disposed) onSpeechError?.invoke(text, "Engine TTS từ chối phát câu.") }
            }
        } catch (e: Exception) {
            utterances.remove(id)
            systemSpeechText.remove(id)
            main.post { if (!disposed) onSpeechError?.invoke(text, e.message ?: "Không gửi được câu tới engine TTS.") }
        }
    }

    // ---------------- Giọng AI ----------------

    private fun styleFor(text: String, d: Delivery?): String = deliveryOf(text, d)?.style.orEmpty()

    private fun speakAi(text: String, d: Delivery?, speakerName: String?, interrupt: Boolean) {
        val style = styleFor(text, d)
        val voiceName = p.aiVoiceFor(speakerName, text)
        enqueue(text, d, (p.rate10 / 10f).coerceIn(0.5f, 2.5f), interrupt, speakerName) { aiPcm(text, style, voiceName) }
    }

    // ---------------- Giọng offline ----------------

    /** Tốc độ tạo giọng offline (đã nằm trong âm thanh): tốc độ cài đặt và nhịp của cảm xúc. Việc đọc nhanh khi dồn làm lúc phát. */
    private fun speedFor(text: String, d: Delivery?): Float {
        var v = p.rate10 / 10f
        deliveryOf(text, d)?.let { v *= it.rate }
        return v.coerceIn(0.5f, 2.2f)
    }

    private fun speakOffline(text: String, d: Delivery?, interrupt: Boolean, speaker: String?) {
        val id = p.offlineVoice
        val speed = speedFor(text, d)
        enqueue(text, d, 1f, interrupt, speaker) { offlinePcm(id, text, speed) }
    }

    // ---------------- Hàng chờ âm thanh (giọng AI và offline) ----------------

    /** Xếp một đoạn vào hàng. Câu trùng câu vừa/đang đọc thì bỏ; bản dở của câu mới (chữ hiện dần) đang chờ thì được thay. */
    private fun enqueue(text: String, d: Delivery?, baseSpeed: Float, interrupt: Boolean, speaker: String?, synth: (alive: () -> Boolean) -> Pcm) {
        if (disposed) return
        if (interrupt) stop()
        val norm = SpeechPolicy.norm(text)
        val now = SystemClock.elapsedRealtime()
        val clip = Clip(seq.get(), text, norm, speaker, d, baseSpeed, now, synth)
        var dropped: List<Clip> = emptyList()
        var waited = false
        synchronized(lock) {
            if (disposed) return
            val last = clips.newest() ?: nowPlaying
            if (last != null && last.norm == norm && SpeechPolicy.sameSpeaker(last.speaker, speaker) && now - last.at < SpeechPolicy.DUPLICATE_MS) return
            for (c in clips.removeAll { SpeechPolicy.sameSpeaker(it.speaker, speaker) && SpeechPolicy.isDraftOf(it.norm, norm) }) discard(c)
            waited = nowPlaying != null || !clips.isEmpty()
            dropped = clips.add(clip, now)
            for (c in dropped) discard(c)
        }
        if (waited) count("doc_cho")
        if (dropped.isNotEmpty()) count("doc_bo", dropped.size.toLong())
        submit { prepareClips() }
    }

    private fun discard(c: Clip) {
        synchronized(lock) {
            c.alive = false
            val t = c.track
            c.track = null
            if (t != null) try { t.release() } catch (_: Exception) {}
        }
    }

    /** Luồng nền: tạo âm thanh cho các câu đang chờ theo thứ tự, dựng sẵn AudioTrack, rồi báo luồng chính phát. */
    private fun prepareClips() {
        while (!disposed) {
            val clip = synchronized(lock) { clips.firstOrNull { it.alive && it.track == null && it.failed == null } } ?: return
            var built: AudioTrack? = null
            try {
                val pcm = clip.synth { clip.alive && !disposed && clip.gen == seq.get() }
                if (clip.alive && !disposed) {
                    if (pcm.data.isEmpty()) throw IllegalStateException("Âm thanh rỗng")
                    built = buildTrack(pcm)
                    clip.durMs = pcm.data.size * 1000L / (pcm.rate * 2L * (if (pcm.channels == 2) 2 else 1)).coerceAtLeast(1)
                }
            } catch (_: java.util.concurrent.CancellationException) {
                clip.alive = false   // đã bị bỏ hoặc dừng trong lúc tạo
            } catch (e: Throwable) {
                clip.failed = e
            }
            val t = built
            if (t != null) {
                var keep = false
                synchronized(lock) { if (!disposed && clip.alive) { clip.track = t; keep = true } }
                if (!keep) try { t.release() } catch (_: Exception) {}
            }
            main.post { tryPlay() }
        }
    }

    private fun buildTrack(pcm: Pcm): AudioTrack {
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
        try {
            if (t.write(pcm.data, 0, pcm.data.size) != pcm.data.size) throw IllegalStateException("PCM write failed")
        } catch (e: Throwable) {
            try { t.release() } catch (_: Exception) {}
            throw e
        }
        return t
    }

    /**
     * Luồng chính: nếu không còn câu nào đang phát thì phát câu đầu hàng (đã có âm thanh sẵn). Gọi lại khi một câu tạo xong,
     * khi câu đang phát hết giờ, hoặc khi có câu mới. Câu chờ quá cũ bị bỏ; câu tạo lỗi thì đọc bằng giọng máy.
     */
    private fun tryPlay() {
        if (disposed) return
        var fb: Clip? = null
        var wait = 0L
        synchronized(lock) {
            if (disposed) return
            val now = SystemClock.elapsedRealtime()
            if (nowPlaying != null && now < nowEnds) return
            nowPlaying = null
            val stale = clips.dropStale(now)
            for (c in stale) discard(c)
            if (stale.isNotEmpty()) count("doc_bo", stale.size.toLong())
            while (true) {
                val head = clips.peek() ?: return
                if (!head.alive) { clips.poll(); continue }
                if (head.failed != null) { clips.poll(); fb = head; break }
                val t = head.track ?: return   // chưa tạo xong: prepareClips sẽ gọi lại
                clips.poll()
                wait = startLocked(head, t, now)
                if (head.failed != null) fb = head
                break
            }
        }
        val f = fb
        if (f != null) {
            fallback(f.text, f.d, f.speaker, f.failed ?: IllegalStateException("Giọng đọc lỗi"))
            wait = maxOf(1_200L, f.text.length * 75L)
            synchronized(lock) { nowPlaying = f; nowEnds = SystemClock.elapsedRealtime() + wait }
        }
        if (wait > 0) {
            main.removeCallbacks(advance)
            main.postDelayed(advance, wait + 40)
        } else if (f == null && synchronized(lock) { !clips.isEmpty() && nowPlaying == null }) {
            main.post(advance)   // câu vừa rồi không phát được: thử câu kế tiếp, không để hàng đứng yên
        }
    }

    /** Phát [c] (cần giữ [lock]). Trả về thời gian phát ước tính (ms), 0 nếu không phát được. Câu trước không bị dừng giữa chừng. */
    private fun startLocked(c: Clip, t: AudioTrack, now: Long): Long {
        val speed = (c.baseSpeed * SpeechPolicy.speedFactor(clips.size, p.autoFast)).coerceIn(0.5f, 2.5f)
        val appliedSpeed = try {
            t.playbackParams = PlaybackParams().setSpeed(speed)
            speed
        } catch (_: Exception) {
            1f
        }
        try {
            t.play()
        } catch (e: Exception) {
            c.failed = e
            try { t.release() } catch (_: Exception) {}
            c.track = null
            return 0L
        }
        c.track = null
        retireLocked(track)
        track = t
        nowPlaying = c
        val dur = SpeechPolicy.playbackDurationMs(c.durMs, appliedSpeed)
        nowEnds = now + dur + 30
        played++
        count("doc_doc")
        onSpeechStarted?.invoke(c.text)
        return dur
    }

    /** Track của câu trước đã tới giờ hết: để nó tự phát nốt phần đuôi rồi mới giải phóng, không dừng ngang. */
    private fun retireLocked(old: AudioTrack?) {
        if (old == null) return
        retired.add(old)
        main.postDelayed({
            synchronized(lock) {
                if (retired.remove(old)) {
                    try { old.stop() } catch (_: Exception) {}
                    try { old.release() } catch (_: Exception) {}
                }
            }
        }, 2_500L)
    }

    private fun releaseAllLocked() {
        for (r in retired) { try { r.stop() } catch (_: Exception) {}; try { r.release() } catch (_: Exception) {} }
        retired.clear()
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
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
        if (disposed) return
        val spoken = LineClean.clean(text)
        if (spoken.isEmpty()) return
        val my = tvSeq.incrementAndGet()
        val useOffline = offlineOn()
        val id = p.offlineVoice
        val speed = if (useOffline) speedFor(spoken, d) else ((p.rate10 / 10f) * (deliveryOf(spoken, d)?.rate ?: 1f)).coerceIn(0.5f, 2.5f)
        val style = styleFor(spoken, d)
        try {
            exec.execute {
                if (my != tvSeq.get()) return@execute
                if (useOffline) {
                    val pcm: Pcm? = try {
                        offlinePcm(id, spoken, speed)
                    } catch (_: Throwable) { null }
                    if (!disposed && my == tvSeq.get()) cb(pcm, 1f) // tốc độ đã nằm trong âm thanh
                } else {
                    val voiceName = p.aiVoiceFor(speakerName, spoken)
                    val pcm: Pcm? = try {
                        aiPcm(spoken, style, voiceName)
                    } catch (_: Exception) { null }
                    if (!disposed && my == tvSeq.get()) cb(pcm, speed)
                }
            }
        } catch (_: Exception) { cb(null, 1f) }
    }

    private fun fallback(text: String, d: Delivery?, speaker: String?, e: Throwable) {
        if (!aiErrorShown) {
            aiErrorShown = true
            val m = when {
                e is OfflineVoiceException || e is LinkageError -> (e.message ?: "Giọng offline không dùng được") + " Đọc bằng giọng máy."
                e is AiException -> "Giọng AI lỗi ${e.code}, đọc bằng giọng máy."
                else -> "Giọng AI không dùng được, đọc bằng giọng máy."
            }
            onAiError?.invoke(m)
        }
        speakSystem(text, false, d, speaker = speaker)
    }

    fun stop() {
        synchronized(pendingSystemLock) {
            pendingSystem.clear()
            lastSysNorm = ""
            utterances.clear()
            systemSpeechText.clear()
            try { tts?.stop() } catch (e: RuntimeException) {
                android.util.Log.w("DG_TTS", "Không dừng được giọng hệ thống", e)
            }
        }
        seq.incrementAndGet()
        tvSeq.incrementAndGet()
        synchronized(lock) {
            for (c in clips.clear()) discard(c)
            nowPlaying = null
            nowEnds = 0L
            releaseAllLocked()
        }
        main.removeCallbacks(advance)
    }

    fun shutdown() {
        disposed = true
        synchronized(pendingSystemLock) { pendingSystem.clear() }
        utterances.clear()
        systemSpeechText.clear()
        tvSeq.incrementAndGet()
        seq.incrementAndGet()
        synchronized(lock) {
            for (c in clips.clear()) discard(c)
            nowPlaying = null
            releaseAllLocked()
        }
        main.removeCallbacks(advance)
        exec.shutdownNow()
        try { offline.close() } catch (_: Throwable) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
