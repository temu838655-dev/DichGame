package com.dichgame.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import kotlin.math.abs

/**
 * Thu âm thanh của game đang chạy trên chính điện thoại để đưa sang bộ nhận dạng giọng nói offline.
 *
 * 1. Ưu tiên thu âm thanh trong của máy (Android 10 trở lên, qua quyền ghi màn hình đã cấp): sạch, không lẫn tiếng ồn xung quanh.
 *    Game được phép chặn kiểu thu này; khi bị chặn Android vẫn trả về im lặng tuyệt đối chứ không báo lỗi,
 *    nên nếu máy đang phát âm thanh mà bản thu im lặng hoàn toàn liên tục [BLOCKED_AFTER_MS] thì coi là bị chặn.
 * 2. Không thu được (bị chặn, Android 9 trở xuống, thiếu quyền ghi màn hình) thì, nếu người dùng cho phép, nghe qua micro:
 *    chất lượng kém hơn và có thể thu cả giọng người chơi. Luôn báo cho người dùng biết đang dùng nguồn nào qua [onMode].
 * 3. Không có quyền ghi âm hoặc người dùng không cho dùng micro thì không thu gì; phụ đề vẫn chạy bằng chữ trên màn hình.
 *
 * Mọi lỗi được bắt lại; không bao giờ làm sập luồng dịch chữ. Âm thanh chỉ đi vào [queue], không lưu ra file.
 */
class PhoneAudioCapture(
    private val ctx: Context,
    private val projection: MediaProjection?,
    private val queue: GameAudioPcmQueue,
    private val allowMic: Boolean,
    /** "system", "mic", "mic-fallback", "none:permission", "none:system", "none:mic". Gọi từ luồng thu âm. */
    private val onMode: (String) -> Unit,
) {
    companion object {
        const val RATE = 16_000
        private const val FRAME = 640 // 40 ms
        const val BLOCKED_AFTER_MS = 12_000L
    }

    @Volatile private var running = false
    @Volatile var mode = "none"
        private set
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::run, "DichGame-Audio-Capture").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        val t = thread ?: return
        if (t !== Thread.currentThread()) runCatching { t.join(2_000L) }
    }

    private fun granted() = Build.VERSION.SDK_INT < 23 ||
        ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun run() {
        var rec: AudioRecord? = null
        try {
            if (!granted()) { mode = "none"; onMode("none:permission"); return }
            var system = true
            rec = openSystem()
            if (rec == null) {
                if (!allowMic) { onMode("none:system"); return }
                rec = openMic()
                if (rec == null) { onMode("none:mic"); return }
                system = false
                mode = "mic"; onMode("mic")
            } else {
                mode = "system"; onMode("system")
            }
            var blocked = loop(rec, system)
            while (blocked && running) {
                // âm thanh trong bị game chặn: chuyển sang micro nếu được phép
                runCatching { rec!!.stop() }; runCatching { rec!!.release() }
                rec = null
                if (!allowMic) { onMode("none:system"); return }
                rec = openMic()
                if (rec == null) { onMode("none:mic"); return }
                mode = "mic"; onMode("mic-fallback")
                blocked = loop(rec, false)
            }
        } catch (_: Throwable) {
            // lỗi âm thanh không được làm hỏng phần dịch chữ
        } finally {
            mode = "none"
            runCatching { rec?.stop() }
            runCatching { rec?.release() }
        }
    }

    /** Đọc liên tục tới khi dừng. Trả true nếu âm thanh trong bị chặn (đang phát mà bản thu im lặng hoàn toàn). */
    private fun loop(rec: AudioRecord, system: Boolean): Boolean {
        val buf = ShortArray(FRAME)
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        rec.startRecording()
        var silentPlayingMs = 0L
        var frames = 0
        var playing = false
        while (running) {
            val n = rec.read(buf, 0, buf.size)
            if (n < 0) { if (!running) break; try { Thread.sleep(50) } catch (_: InterruptedException) { break }; if (n == AudioRecord.ERROR_DEAD_OBJECT) break; continue }
            if (n == 0) continue
            queue.offer(if (n == buf.size) buf else buf.copyOf(n), RATE, 1)
            if (!system) continue
            var peak = 0
            for (i in 0 until n) { val a = abs(buf[i].toInt()); if (a > peak) peak = a }
            if (frames++ % 25 == 0) playing = try { am?.isMusicActive == true } catch (_: Throwable) { false }
            if (peak == 0 && playing) {
                silentPlayingMs += n * 1000L / RATE
                if (silentPlayingMs >= BLOCKED_AFTER_MS) return true
            } else if (peak > 0) silentPlayingMs = 0L
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun openSystem(): AudioRecord? {
        val p = projection ?: return null
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val cfg = AudioPlaybackCaptureConfiguration.Builder(p)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val r = AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(maxOf(min, FRAME * 2 * 8))
                .setAudioPlaybackCaptureConfig(cfg)
                .build()
            if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
        } catch (_: Throwable) { null }
    }

    @SuppressLint("MissingPermission")
    private fun openMic(): AudioRecord? = try {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 2 * 8))
        if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
    } catch (_: Throwable) { null }
}
