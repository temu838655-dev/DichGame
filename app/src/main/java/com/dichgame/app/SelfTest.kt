package com.dichgame.app

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tự kiểm tra giọng offline, chỉ chạy ở bản debug khi được mở bằng extra "selftest" (dùng trong ci/smoke.sh):
 * tải gói giọng, nạp thư viện sherpa-onnx, đọc thử một đoạn và ghi kết quả vào nhật ký (thẻ DG_SELFTEST)
 * cùng file selftest.wav trong bộ nhớ app.
 */
object SelfTest {
    private const val TAG = "DG_SELFTEST"
    private const val ID = "vais1000"

    fun voice(ctx: Context) {
        Thread {
            try {
                val phrase = "Xin chào, tôi có 25 đồng vàng."
                if (!testSystemPlayback(ctx, phrase)) {
                    Log.e(TAG, "FAIL system_tts: engine không phát và kết thúc được câu tiếng Việt.")
                }
                if (!Voices.installed(ctx, ID)) {
                    val pack = Voices.pack(ID)!!
                    var last = -10
                    val err = Voices.download(ctx, pack, AtomicBoolean(false)) { p ->
                        if (p >= last + 10) { last = p; Log.i(TAG, "download $p%") }
                    }
                    if (err != null) { Log.e(TAG, "FAIL download: $err"); return@Thread }
                }
                val ov = OfflineVoice(ctx)
                val text = "Xin chào, tôi có 25 đồng vàng. Bạn có khỏe không?"
                val t0 = System.currentTimeMillis()
                val pcm = ov.synth(ID, text, 1f)
                val ms = System.currentTimeMillis() - t0
                var peak = 0
                var i = 0
                while (i + 1 < pcm.data.size) {
                    val v = (pcm.data[i].toInt() and 0xFF) or (pcm.data[i + 1].toInt() shl 8)
                    val a = Math.abs(v.toShort().toInt())
                    if (a > peak) peak = a
                    i += 2
                }
                val secs = pcm.data.size / 2f / pcm.rate
                writeWav(File(ctx.filesDir, "selftest.wav"), pcm)
                // lần hai: model đã nạp sẵn, đo tốc độ thật
                val t1 = System.currentTimeMillis()
                ov.synth(ID, text, 1f)
                val ms2 = System.currentTimeMillis() - t1
                ov.close()
                if (pcm.data.isEmpty() || peak < 1000) {
                    Log.e(TAG, "FAIL offline_synthesis: audio rỗng hoặc quá nhỏ: bytes=${pcm.data.size} peak=$peak")
                } else {
                    Log.i(TAG, "OK offline_synthesis audio=${"%.2f".format(secs)}s rate=${pcm.rate} peak=$peak first=${ms}ms second=${ms2}ms")
                    if (!testOfflinePlayback(ctx, phrase)) {
                        Log.e(TAG, "FAIL offline_track: AudioTrack không vào trạng thái phát.")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "FAIL " + e.javaClass.simpleName + ": " + e.message)
            }
        }.start()
    }

    private fun testSystemPlayback(ctx: Context, text: String): Boolean {
        val started = CountDownLatch(1)
        val firstEvent = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val warning = AtomicReference<String?>(null)
        val speaker = Speaker(ctx, Prefs(ctx).apply {
            voiceEngine = "system"
            rate10 = 10
            emotion = false
        })
        speaker.onSpeechStarted = { started.countDown(); firstEvent.countDown() }
        speaker.onSpeechFinished = { finished.countDown() }
        speaker.onSpeechError = { _, message ->
            failure.set(message)
            firstEvent.countDown()
            finished.countDown()
        }
        speaker.onSpeechWarning = { warning.set(it) }
        return try {
            speaker.speak(text, interrupt = true)
            firstEvent.await(15, TimeUnit.SECONDS)
            val didStart = started.count == 0L
            val didFinish = didStart && finished.await(25, TimeUnit.SECONDS)
            val ok = didStart && didFinish && failure.get() == null
            if (ok) {
                Log.i(TAG, "OK system_tts started and completed utterance; viLocale=${speaker.systemLanguageAvailable}")
                warning.get()?.let { Log.w(TAG, "WARN system_tts_locale: $it") }
            } else Log.e(TAG, "FAIL system_tts started=$didStart finished=$didFinish error=${failure.get() ?: "timeout"}")
            ok
        } finally {
            speaker.shutdown()
        }
    }

    private fun testOfflinePlayback(ctx: Context, text: String): Boolean {
        val speaker = Speaker(ctx, Prefs(ctx).apply {
            voiceEngine = "offline"
            offlineVoice = ID
            rate10 = 10
            emotion = false
        })
        return try {
            speaker.speak(text, interrupt = true)
            val deadline = System.currentTimeMillis() + 15_000L
            while (System.currentTimeMillis() < deadline && speaker.played == 0) Thread.sleep(50L)
            val playing = speaker.played > 0 && speaker.isAudioTrackPlaying()
            if (playing) Log.i(TAG, "OK offline_track AudioTrack entered PLAYSTATE_PLAYING")
            else Log.e(TAG, "FAIL offline_track played=${speaker.played} active=${speaker.isAudioTrackPlaying()}")
            playing
        } finally {
            speaker.shutdown()
        }
    }

    private fun writeWav(f: File, pcm: Pcm) {
        val n = pcm.data.size
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + n); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(pcm.rate); h.putInt(pcm.rate * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(n)
        f.outputStream().use { it.write(h.array()); it.write(pcm.data) }
    }
}
