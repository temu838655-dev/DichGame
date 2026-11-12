package com.dichgame.app

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
                // Đường đọc thật của app: Speaker chọn giọng offline, tạo âm thanh và phát bằng AudioTrack
                val pr = Prefs(ctx)
                val oldEngine = pr.voiceEngine
                pr.voiceEngine = "offline"
                var spErr: String? = null
                val holder = arrayOfNulls<Speaker>(1)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    holder[0] = Speaker(ctx, pr).also { it.onAiError = { m -> spErr = m } }
                }
                var waited = 0
                while (holder[0] == null && waited < 50) { Thread.sleep(100); waited++ }
                val sp = holder[0]
                sp?.speak("Chúng ta phải rời khỏi đây ngay, trước khi quá muộn.")
                waited = 0
                while (sp != null && sp.played == 0 && spErr == null && waited < 100) { Thread.sleep(100); waited++ }
                val spOk = sp != null && sp.played > 0 && spErr == null
                Log.i(TAG, "speaker played=${sp?.played} err=$spErr ok=$spOk")
                try { sp?.shutdown() } catch (_: Throwable) {}
                pr.voiceEngine = oldEngine
                if (!spOk) { Log.e(TAG, "FAIL Speaker khong phat duoc giong offline"); return@Thread }
                if (pcm.data.isEmpty() || peak < 1000) Log.e(TAG, "FAIL audio rong hoac qua nho: bytes=${pcm.data.size} peak=$peak")
                else Log.i(TAG, "OK audio=${"%.2f".format(secs)}s rate=${pcm.rate} peak=$peak first=${ms}ms second=${ms2}ms")
            } catch (e: Throwable) {
                Log.e(TAG, "FAIL " + e.javaClass.simpleName + ": " + e.message)
            }
        }.start()
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
