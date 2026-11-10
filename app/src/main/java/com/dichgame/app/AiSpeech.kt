package com.dichgame.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Âm thanh thô 16 bit. */
class Pcm(val data: ByteArray, val rate: Int, val channels: Int)

/**
 * Giọng đọc AI của Google Gemini. Thử giao diện "Interactions" trước (theo tài liệu mới), lỗi 400/404 thì thử kiểu generateContent cũ.
 * Hàm chặn luồng, gọi từ luồng nền.
 */
object AiSpeech {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    fun synth(p: Prefs, text: String, style: String): Pcm {
        return try {
            viaInteractions(p, text, style)
        } catch (e: AiException) {
            if (e.code == 400 || e.code == 404) {
                try { viaGenerate(p, text, style) } catch (e2: AiException) { throw e2 }
            } else throw e
        }
    }

    private fun viaInteractions(p: Prefs, text: String, style: String): Pcm {
        val item = JSONObject().put("type", "text").put("text", text)
        if (style.isNotEmpty()) {
            item.put("annotations", JSONArray().put(JSONObject().put("type", "speech_metadata").put("style", style)))
        }
        val body = JSONObject()
            .put("model", p.ttsModel)
            .put("input", JSONArray().put(JSONObject().put("type", "user_input").put("content", JSONArray().put(item))))
            .put("response_format", JSONObject().put("type", "audio"))
            .put("generation_config", JSONObject().put("speech_config", JSONArray().put(JSONObject().put("voice", p.aiVoice))))
        val r = Ai.post("$BASE/interactions", body, mapOf("x-goog-api-key" to p.geminiKey))
        val steps = JSONObject(r).optJSONArray("steps") ?: throw AiException(0, "Phản hồi không có âm thanh")
        var data = ""
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            if (s.optString("type") != "model_output") continue
            val c = s.optJSONArray("content") ?: continue
            for (j in 0 until c.length()) {
                val x = c.getJSONObject(j)
                if (x.optString("type") == "audio") data = x.optString("data")
            }
        }
        if (data.isEmpty()) throw AiException(0, "Phản hồi không có âm thanh")
        return parse(Base64.decode(data, Base64.DEFAULT))
    }

    private fun viaGenerate(p: Prefs, text: String, style: String): Pcm {
        val prompt = if (style.isEmpty()) text else "Say in a $style way: $text"
        val voice = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", p.aiVoice))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", voice)))
        val r = Ai.post("$BASE/models/${p.ttsModel}:generateContent", body, mapOf("x-goog-api-key" to p.geminiKey))
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
            429 -> "Lỗi 429: hết lượt dùng, chờ rồi thử lại."
            else -> "Lỗi ${e.code}: ${e.message.orEmpty()}"
        }
    } catch (e: Exception) {
        "Không kết nối được: ${e.message.orEmpty()}"
    }
}
