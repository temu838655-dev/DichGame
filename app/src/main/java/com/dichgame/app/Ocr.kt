package com.dichgame.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
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
class Blk(val text: String, val box: Rect?)

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

    /** true nếu mỗi lần đọc phải gửi ảnh lên Gemini (chậm hơn và tốn lượt dùng). */
    val isCloud: Boolean get() = local == null

    fun read(bmp: Bitmap, fail: (String) -> Unit, ok: (List<Blk>) -> Unit) {
        val l = local
        if (l != null) {
            l.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { r -> ok(r.textBlocks.map { Blk(it.text, it.boundingBox) }) }
                .addOnFailureListener { fail("") }
            return
        }
        val key = p.geminiKey
        if (key.isEmpty()) { fail("Chữ ${Prefs.srcNameOf(lang)} cần khóa Google Gemini. Vào Cách dịch để nhập khóa."); return }
        val model = p.geminiModel
        io.execute {
            try {
                val blocks = cloudRead(bmp, key, model)
                main.post { ok(blocks) }
            } catch (e: Exception) {
                val m = errorText(e)
                main.post { fail(m) }
            }
        }
    }

    fun close() {
        local?.close()
        if (isCloud) io.shutdown()
    }

    private fun cloudRead(bmp: Bitmap, key: String, model: String): List<Blk> {
        val sc = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
        val small = if (sc < 1f)
            Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(1), (bmp.height * sc).toInt().coerceAtLeast(1), true)
        else bmp
        val bos = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        if (small !== bmp) small.recycle()
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        val prompt = "Đây là ảnh chụp màn hình game. Đọc toàn bộ chữ ${Prefs.srcNameOf(lang)} trong ảnh, " +
            "gộp mỗi câu thoại, mỗi đoạn văn thành một khối, xếp từ trên xuống dưới. " +
            "Không dịch, không sửa, bỏ qua chữ không phải ${Prefs.srcNameOf(lang)} nếu đó chỉ là biểu tượng hay số liệu. " +
            """Chỉ trả về JSON dạng {"blocks":[{"t":"nội dung","b":[ymin,xmin,ymax,xmax]}]} """ +
            """với b là khung bao chuẩn hóa từ 0 đến 1000. Không có chữ thì trả {"blocks":[]}."""
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
        val r = Ai.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            body, mapOf("x-goog-api-key" to key)
        )
        val candidate = JSONObject(r).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")
            ?: throw IllegalStateException("Gemini không trả về nội dung OCR.")
        val responseText = candidate.optJSONArray("parts")
            ?.let { parts -> (0 until parts.length()).asSequence()
                .mapNotNull { parts.optJSONObject(it)?.optString("text") }
                .firstOrNull { it.isNotBlank() } }
            ?: throw IllegalStateException("Gemini không trả về văn bản OCR.")
        return parse(responseText, bmp.width, bmp.height)
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
            val box = if (b != null && b.length() == 4) {
                // Gemini có thể trả tọa độ vượt miền hoặc đảo cạnh; luôn chặn trong ảnh.
                val top = (b.optDouble(0, Double.NaN) * h / 1000.0).toInt().coerceIn(0, h)
                val left = (b.optDouble(1, Double.NaN) * w / 1000.0).toInt().coerceIn(0, w)
                val bottom = (b.optDouble(2, Double.NaN) * h / 1000.0).toInt().coerceIn(0, h)
                val right = (b.optDouble(3, Double.NaN) * w / 1000.0).toInt().coerceIn(0, w)
                if (top < bottom && left < right) Rect(left, top, right, bottom) else null
            } else null
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

    companion object {
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
