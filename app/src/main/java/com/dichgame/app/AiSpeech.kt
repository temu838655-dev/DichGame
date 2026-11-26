package com.dichgame.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Âm thanh thô 16 bit. */
class Pcm(val data: ByteArray, val rate: Int, val channels: Int)

/**
 * Giọng đọc AI Gemini qua generateContent — endpoint và JSON theo tài liệu Gemini TTS.
 * Hàm chặn luồng, chỉ gọi từ luồng nền.
 */
object AiSpeech {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    /** Đọc nguyên văn [text]. Chỉ dẫn phong cách nằm trong speechMetadata, không trộn vào transcript. */
    fun synth(p: Prefs, text: String, style: String, voiceName: String = p.aiVoice): Pcm {
        val model = p.ttsModel.trim()
        if (!model.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
            throw AiException(400, "Tên model giọng Gemini không hợp lệ. Chỉ dùng mã model, không thêm URL hoặc tham số.")
        }
        return viaGenerate(p, model, text, style, voiceName)
    }

    /** Request schema chính thức: transcript nguyên văn + speechMetadata tùy chọn ở cùng TextPart. */
    internal fun generateBody(text: String, style: String, voiceName: String): JSONObject {
        val part = JSONObject().put("text", text)
        if (style.isNotBlank()) part.put("speechMetadata", JSONObject().put("style", style))
        val voice = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(part))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", voice)))
    }

    private fun viaGenerate(p: Prefs, model: String, text: String, style: String, voiceName: String): Pcm {
        val body = generateBody(text, style, voiceName)
        val r = Ai.withGeminiKey(p, model) { key ->
            Ai.post("$BASE/models/$model:generateContent", body, mapOf("x-goog-api-key" to key), metric = "api_giong_gemini")
        }
        val b64 = JSONObject(r).getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("data")
        return parse(Base64.decode(b64, Base64.DEFAULT))
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) = le16(b, o) or (le16(b, o + 2) shl 16)

    /** WAV (có đầu RIFF) thì bóc phần dữ liệu; không có đầu thì coi là PCM 24 kHz, mono, 16 bit. */
    fun parse(b: ByteArray): Pcm {
        val riff = b.size > 44 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte()
        if (!riff) return Pcm(b, 24000, 1)
        var rate = 24000
        var ch = 1
        var pos = 12
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val len = le32(b, pos + 4)
            if (id == "fmt " && pos + 16 <= b.size) {
                ch = le16(b, pos + 10).coerceIn(1, 2)
                rate = le32(b, pos + 12)
            }
            if (id == "data") {
                val start = pos + 8
                val end = if (len <= 0 || start + len > b.size) b.size else start + len
                return Pcm(b.copyOfRange(start, end), rate, ch)
            }
            if (len < 0) break
            pos += 8 + len + (len and 1)
        }
        return Pcm(b.copyOfRange(44, b.size), rate, ch)
    }

    /** Thử đọc một câu ngắn. Trả về null nếu chạy được, ngược lại là lý do lỗi. */
    fun test(p: Prefs): String? = try {
        val r = synth(p, "Xin chào.", "")
        if (r.data.isEmpty()) "Không nhận được âm thanh." else null
    } catch (e: AiException) {
        when (e.code) {
            401, 403 -> "Lỗi ${e.code}: khóa Gemini sai hoặc không có quyền dùng giọng AI."
            404 -> "Lỗi 404: sai tên model giọng AI (${p.ttsModel})."
            429 -> "Mọi khóa Gemini đang hết lượt giọng AI (429)" + (if (e.retryAfterMs > 0) ", khóa sớm nhất dùng lại sau ${AiRetry.humanWait(e.retryAfterMs)}" else "") + "."
            else -> "Lỗi ${e.code}: ${e.message.orEmpty()}"
        }
    } catch (e: Exception) {
        "Không kết nối được: ${e.message.orEmpty()}"
    }
}
