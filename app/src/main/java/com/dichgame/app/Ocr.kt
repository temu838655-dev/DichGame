package com.dichgame.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** Một khối chữ đọc được trên màn hình, kèm khung bao (có thể null). */
/** [conf] là độ tin cậy 0..1 do ML Kit báo (trung bình các dòng, có trọng số theo độ dài); 1 nếu bộ đọc không báo. */
class Blk(val text: String, val box: Rect?, val conf: Float = 1f)

/**
 * Đọc chữ từ ảnh chụp màn hình.
 * Chữ Latin, Nhật, Hàn, Trung, Hindi: đọc ngay trên máy bằng ML Kit.
 * Chữ Nga, Ukraina, Thái, Ả Rập, Hy Lạp, Do Thái: ML Kit không có mô hình, nên gửi ảnh cho Google Gemini đọc (cần khóa Gemini).
 * Kết quả luôn được trả về trên luồng chính.
 */
class Ocr(private val p: Prefs, private val lang: String) {
    private val main = Handler(Looper.getMainLooper())
    private val io by lazy { Executors.newSingleThreadExecutor() }
    private val local: TextRecognizer? =
        if (Prefs.isCloudOcr(lang)) null
        else TextRecognition.getClient(
            when (lang) {
                "ja" -> JapaneseTextRecognizerOptions.Builder().build()
                "ko" -> KoreanTextRecognizerOptions.Builder().build()
                "zh" -> ChineseTextRecognizerOptions.Builder().build()
                "hi" -> DevanagariTextRecognizerOptions.Builder().build()
                else -> TextRecognizerOptions.DEFAULT_OPTIONS
            }
        )

    // Khi nguồn là "auto", OCR thử nhiều bộ đọc một lần và ưu tiên bộ có chữ viết
    // phù hợp ở các khung sau. Không ép người dùng chọn tiếng Nhật/Hàn/Trung.
    private val autoReaders by lazy {
        autoStarted = true
        listOf(
            "en" to requireNotNull(local),
            "ja" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            "ko" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
            "zh" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            "hi" to TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        )
    }
    private var autoStarted = false
    private var autoPreferred = 0
    private var autoProbeAt = 0L
    private var autoEmpty = 0
    private var autoCloudAt = 0L

    /** true nếu mỗi lần đọc phải gửi ảnh lên Gemini (chậm hơn và tốn lượt dùng). */
    val isCloud: Boolean get() = local == null

    /**
     * Ocr.read nhận quyền sở hữu bitmap đầu vào: chỉ giải phóng sau khi ML Kit/Gemini
     * đã đọc xong. CaptureService tạo ảnh mới ở mỗi khung hình nên không giải phóng
     * ở đây sẽ làm bộ nhớ native tăng dần trong phiên chơi dài.
     */
    private fun recycleInput(bmp: Bitmap) {
        if (!bmp.isRecycled) runCatching { bmp.recycle() }
    }

    fun read(bmp: Bitmap, fail: (String) -> Unit, ok: (List<Blk>) -> Unit) {
        if (lang == "auto") { readAuto(bmp, fail, ok); return }
        val l = local
        if (l != null) {
            try {
                l.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { r ->
                        recycleInput(bmp)
                        ok(r.textBlocks.map { Blk(it.text, it.boundingBox, confOf(it)) })
                    }
                    .addOnFailureListener {
                        recycleInput(bmp)
                        fail("")
                    }
            } catch (e: Exception) {
                recycleInput(bmp)
                fail("")
            }
            return
        }
        if (p.geminiKeys().isEmpty()) {
            recycleInput(bmp)
            fail("Chữ ${Prefs.srcNameOf(lang)} cần khóa Google Gemini. Vào Cách dịch để nhập khóa.")
            return
        }
        val model = p.geminiModel
        try {
            io.execute {
                try {
                    val blocks = cloudRead(bmp, p, model)
                    recycleInput(bmp)
                    main.post { ok(blocks) }
                } catch (e: Exception) {
                    recycleInput(bmp)
                    val m = errorText(e)
                    main.post { fail(m) }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            recycleInput(bmp)
            main.post { fail("Bộ đọc chữ đã dừng. Hãy khởi động lại dịch game.") }
        }
    }

    private fun autoScore(code: String, blocks: List<Blk>): Float {
        val text = blocks.joinToString(" ") { it.text }.trim()
        val counts = LangDetect.scriptCounts(text)
        val total = counts.values.sum()
        if (total < 2) return 0f
        val matched = when (code) {
            "ja" -> (counts['K'] ?: 0) + (counts['H'] ?: 0)
            "zh" -> counts['H'] ?: 0
            "ko" -> counts['G'] ?: 0
            "hi" -> counts['D'] ?: 0
            else -> counts['L'] ?: 0
        }
        val ratio = matched.toFloat() / total
        if (ratio < 0.7f) return 0f
        val confidence = blocks.map { it.conf }.average().toFloat().coerceIn(0f, 1f)
        val kanaBonus = if (code == "ja" && (counts['K'] ?: 0) > 0) 15f else 0f
        val chineseBonus = if (code == "zh" && (counts['K'] ?: 0) == 0) 4f else 0f
        return 100f * ratio + minOf(text.length, 60) * 0.2f + confidence * 9f + kanaBonus + chineseBonus
    }

    private fun readAuto(bmp: Bitmap, fail: (String) -> Unit, ok: (List<Blk>) -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        val readers = autoReaders
        val probe = autoProbeAt == 0L || now - autoProbeAt > 8000L || autoEmpty >= 2
        val indices = if (probe) readers.indices.toList() else listOf(autoPreferred)
        if (probe) autoProbeAt = now
        val input = try { InputImage.fromBitmap(bmp, 0) }
        catch (e: Exception) { recycleInput(bmp); fail("Không mở được ảnh để quét OCR."); return }
        try {
            val tasks = indices.map { readers[it].second.process(input) }
            Tasks.whenAllComplete(tasks).addOnCompleteListener {
                var bestIndex = -1
                var bestScore = 0f
                var best = emptyList<Blk>()
                for (j in tasks.indices) {
                    val task = tasks[j]
                    if (!task.isSuccessful) continue
                    val b = task.result?.textBlocks?.map { Blk(it.text, it.boundingBox, confOf(it)) } ?: continue
                    val score = autoScore(readers[indices[j]].first, b)
                    if (score > bestScore) {
                        bestIndex = indices[j]
                        bestScore = score
                        best = b
                    }
                }
                if (bestIndex >= 0) {
                    autoPreferred = bestIndex
                    autoEmpty = 0
                    recycleInput(bmp)
                    ok(best)
                } else {
                    autoEmpty++
                    val nowCloud = android.os.SystemClock.elapsedRealtime()
                    if (p.geminiKeys().isNotEmpty() && autoEmpty >= 2 && nowCloud - autoCloudAt > 5000L) {
                        autoCloudAt = nowCloud
                        try {
                            io.execute {
                                try {
                                    val blocks = cloudRead(bmp, p, p.geminiModel)
                                    recycleInput(bmp)
                                    main.post { ok(blocks) }
                                } catch (e: Exception) {
                                    recycleInput(bmp)
                                    main.post { fail(errorText(e)) }
                                }
                            }
                        } catch (e: Exception) {
                            recycleInput(bmp)
                            fail("Không khởi động được OCR dự phòng.")
                        }
                    } else {
                        recycleInput(bmp)
                        ok(emptyList())
                    }
                }
            }
        } catch (e: Exception) {
            recycleInput(bmp)
            fail("Bộ nhận dạng chữ không khởi động được.")
        }
    }

    fun close() {
        if (autoStarted) autoReaders.forEach { (_, reader) -> try { reader.close() } catch (_: Exception) {} }
        else local?.close()
        if (isCloud || lang == "auto") io.shutdown()
    }

    private fun cloudRead(bmp: Bitmap, prefs: Prefs, model: String): List<Blk> {
        val sc = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
        val small = if (sc < 1f)
            Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(1), (bmp.height * sc).toInt().coerceAtLeast(1), true)
        else bmp
        val b64 = try {
            val bos = ByteArrayOutputStream()
            try {
                check(small.compress(Bitmap.CompressFormat.JPEG, 80, bos)) { "Không mã hóa được ảnh OCR." }
                Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            } finally {
                bos.close()
            }
        } finally {
            if (small !== bmp && !small.isRecycled) runCatching { small.recycle() }
        }
        val instruction = if (lang == "auto") {
            "Tự xác định ngôn ngữ gốc trong ảnh rồi chép nguyên văn mọi phụ đề nhìn thấy. "
        } else {
            "Đọc toàn bộ chữ " + Prefs.srcNameOf(lang) + " trong ảnh. "
        }
        val prompt = "Đây là ảnh chụp màn hình game hoặc video. " + instruction +
            "Gộp mỗi câu thoại, mỗi đoạn văn thành một khối, xếp từ trên xuống dưới. " +
            "Không dịch, không tự viết thêm câu không có trong ảnh; bỏ qua biểu tượng và số liệu rời rạc. " +
            "Chỉ trả về JSON dạng {\"blocks\":[{\"t\":\"nội dung\",\"b\":[ymin,xmin,ymax,xmax]}]} " +
            "với b là khung bao chuẩn hóa từ 0 đến 1000. Không có chữ thì trả {\"blocks\":[]}."
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
        val keys = prefs.geminiKeys()
        if (keys.isEmpty()) throw AiException(401, "Chưa cấu hình khóa Gemini")
        val start = Prefs.geminiAt.mod(keys.size)
        var r: String? = null
        for (step in keys.indices) {
            val index = (start + step) % keys.size
            try {
                r = Ai.post(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    body, mapOf("x-goog-api-key" to keys[index])
                )
                Prefs.geminiAt = index
                break
            } catch (e: AiException) {
                if (e.code !in setOf(401, 403, 429) || step == keys.lastIndex) throw e
                Prefs.geminiAt = (index + 1) % keys.size
            }
        }
        val response = r ?: throw AiException(429, "Không còn khóa Gemini OCR dùng được")
        val txt = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text")
        return parse(txt, bmp.width, bmp.height)
    }

    private fun parse(raw: String, w: Int, h: Int): List<Blk> {
        val s = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr: JSONArray = try {
            JSONObject(s).optJSONArray("blocks") ?: return emptyList()
        } catch (_: Exception) {
            try { JSONArray(s) } catch (_: Exception) { return emptyList() }
        }
        val out = ArrayList<Blk>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = o.optString("t").trim()
            if (t.isEmpty()) continue
            val b = o.optJSONArray("b")
            val box = if (b != null && b.length() == 4) Rect(
                (b.optDouble(1) * w / 1000).toInt(), (b.optDouble(0) * h / 1000).toInt(),
                (b.optDouble(3) * w / 1000).toInt(), (b.optDouble(2) * h / 1000).toInt()
            ) else null
            out.add(Blk(t, box))
        }
        return out
    }

    private fun errorText(e: Exception): String =
        if (e is AiException) when (e.code) {
            400 -> "Gemini lỗi 400: sai tên model. Kiểm tra lại ở Cách dịch."
            401, 403 -> "Gemini lỗi ${e.code}: khóa API sai hoặc không có quyền."
            429 -> "Gemini hết lượt dùng (429). Chờ một lúc hoặc đổi khóa."
            else -> "Gemini lỗi ${e.code}."
        } else "Không kết nối được Gemini để đọc chữ."

    private fun confOf(b: com.google.mlkit.vision.text.Text.TextBlock): Float = try {
        var sum = 0f
        var w = 0
        for (l in b.lines) {
            val n = l.text.length.coerceAtLeast(1)
            sum += l.confidence * n
            w += n
        }
        if (w == 0) 1f else (sum / w).coerceIn(0f, 1f)
    } catch (_: Throwable) { 1f }

    companion object {
        /**
         * Ảnh thu nhỏ 64x32 (độ sáng) để quyết định có bỏ qua một lần đọc chữ hay không. Đủ mịn để một chữ mới hiện lên
         * làm ít nhất một ô đổi rõ rệt: dùng với [sameStrict], thà đọc thừa còn hơn bỏ sót câu thoại.
         */
        fun thumbFine(b: Bitmap): IntArray {
            val t = Bitmap.createScaledBitmap(b, 64, 32, true)
            val a = IntArray(64 * 32)
            t.getPixels(a, 0, 64, 0, 0, 64, 32)
            if (t !== b) t.recycle()
            for (i in a.indices) {
                val c = a[i]
                a[i] = ((c shr 16 and 255) * 3 + (c shr 8 and 255) * 6 + (c and 255)) / 10
            }
            return a
        }

        /** Hai ảnh coi là y hệt chỉ khi không ô nào lệch quá [cell] mức sáng và độ lệch trung bình rất nhỏ (nhiễu nén video vẫn qua). */
        fun sameStrict(a: IntArray?, b: IntArray?, cell: Int = 14): Boolean {
            if (a == null || b == null || a.size != b.size) return false
            var sum = 0
            for (i in a.indices) {
                val d = Math.abs(a[i] - b[i])
                if (d > cell) return false
                sum += d
            }
            return sum / a.size < 3
        }

        /** Ảnh thu nhỏ 24x12 để biết khung hình có đổi không, tránh gửi Gemini khi hình đứng yên. */
        fun thumb(b: Bitmap): IntArray {
            val t = Bitmap.createScaledBitmap(b, 24, 12, true)
            val a = IntArray(24 * 12)
            t.getPixels(a, 0, 24, 0, 0, 24, 12)
            if (t !== b) t.recycle()
            for (i in a.indices) {
                val c = a[i]
                a[i] = ((c shr 16 and 255) * 3 + (c shr 8 and 255) * 6 + (c and 255)) / 10
            }
            return a
        }

        fun same(a: IntArray?, b: IntArray?): Boolean {
            if (a == null || b == null || a.size != b.size) return false
            var d = 0
            for (i in a.indices) d += Math.abs(a[i] - b[i])
            return d / a.size < 6
        }
    }
}
