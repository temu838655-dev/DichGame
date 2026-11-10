package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class AiException(val code: Int, message: String) : Exception(message)

/** Dịch bằng AI đám mây: Google Gemini hoặc CloudAPI (chuẩn OpenAI /chat/completions). Hàm ở đây chặn luồng, hãy gọi từ luồng nền. */
object Ai {
    fun enabled(p: Prefs): Boolean = when (p.engine) {
        Prefs.ENGINE_GEMINI -> p.geminiKey.isNotEmpty()
        Prefs.ENGINE_CLOUD -> p.cloudKey.isNotEmpty() && p.cloudUrl.isNotEmpty()
        else -> false
    }

    fun prompt(p: Prefs, srcName: String, game: String, history: List<String>, text: String): String =
        "Bạn dịch phụ đề game từ $srcName sang tiếng Việt." +
            (if (game.isNotEmpty()) " Game / phong cách: $game." else "") +
            " " + Prefs.TR_STYLES[p.trStyle].second +
            glossaryLine(p) +
            " Chọn xưng hô (tôi, anh, em, cậu, ông, bà...) hợp quan hệ và ngữ cảnh, sửa lỗi đọc chữ rõ ràng, giữ nguyên tên riêng. Chỉ trả về bản dịch." +
            (if (history.isNotEmpty()) "\nCác câu trước (chỉ để hiểu mạch truyện, không dịch lại):\n" + history.joinToString("\n") else "") +
            "\nCần dịch: $text"

    private fun glossaryLine(p: Prefs): String {
        val lines = p.glossary.lines().map { it.trim() }.filter { it.contains("=") }.take(40)
        if (lines.isEmpty()) return ""
        return " Bắt buộc dùng đúng các thuật ngữ sau (gốc = tiếng Việt): " + lines.joinToString("; ") + "."
    }

    fun cloudEndpoint(base: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    internal fun post(url: String, body: JSONObject, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 15000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw AiException(code, txt.take(200))
            return txt
        } finally {
            c.disconnect()
        }
    }

    fun translate(p: Prefs, prompt: String): String {
        val out: String = if (p.engine == Prefs.ENGINE_CLOUD) {
            val body = JSONObject()
                .put("model", p.cloudModel)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            val r = post(cloudEndpoint(p.cloudUrl), body, mapOf("Authorization" to "Bearer " + p.cloudKey))
            JSONObject(r).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            val body = JSONObject().put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            val r = post(
                "https://generativelanguage.googleapis.com/v1beta/models/${p.geminiModel}:generateContent",
                body, mapOf("x-goog-api-key" to p.geminiKey)
            )
            JSONObject(r).getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
        }
        val t = out.trim()
        if (t.isEmpty()) throw AiException(0, "Phản hồi rỗng")
        return t
    }

    /** Thử dịch một câu để kiểm tra khóa. Trả về chuỗi bắt đầu bằng "OK" nếu chạy được, ngược lại là lý do lỗi. */
    fun test(p: Prefs): String = try {
        "OK: " + translate(p, prompt(p, "tiếng Anh", "", emptyList(), "Hello, how are you?"))
    } catch (e: AiException) {
        when (e.code) {
            400 -> "Lỗi 400: sai tên model hoặc yêu cầu không hợp lệ. ${e.message.orEmpty()}"
            401, 403 -> "Lỗi ${e.code}: khóa API sai hoặc không có quyền."
            404 -> "Lỗi 404: sai địa chỉ hoặc tên model."
            429 -> "Lỗi 429: hết lượt dùng, chờ rồi thử lại hoặc đổi khóa."
            else -> "Lỗi ${e.code}: ${e.message.orEmpty()}"
        }
    } catch (e: Exception) {
        "Không kết nối được: ${e.message.orEmpty()}"
    }
}
