package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class AiException(val code: Int, message: String) : Exception(message)

/** Dịch bằng AI đám mây: Google Gemini hoặc CloudAPI (chuẩn OpenAI /chat/completions). Hàm ở đây chặn luồng, hãy gọi từ luồng nền. */
object Ai {
    fun enabled(p: Prefs): Boolean = when (p.engine) {
        Prefs.ENGINE_GEMINI -> p.geminiKeys().isNotEmpty()
        Prefs.ENGINE_CLOUD -> p.cloudKey.isNotEmpty() && p.cloudUrl.isNotEmpty()
        else -> false
    }

    /**
     * Câu lệnh dịch một câu thoại. [terms] là bảng thuật ngữ (chỉ đưa các từ có trong câu, bắt buộc dùng đúng),
     * [cast] là thẻ nhân vật, [emo] bật dịch theo cảm xúc và tiết tấu (AI mở đầu bằng thẻ [cảm xúc|nhịp]).
     */
    fun prompt(
        p: Prefs, srcName: String, game: String, history: List<String>, text: String,
        terms: TermMap? = null, cast: List<Character> = emptyList(), emo: Boolean = false, speaker: String? = null,
        strict: String? = null,
    ): String =
        "Bạn dịch phụ đề game từ $srcName sang tiếng Việt." +
            (if (game.isNotEmpty()) " Game / phong cách: $game." else "") +
            " " + p.styleText() +
            glossaryLine(terms, p, text) +
            Cast.promptLine(cast, history.joinToString(" ") + " " + text + " " + (speaker ?: "")) +
            (if (speaker != null) " Người đang nói câu này: $speaker (giữ đúng giọng và cách xưng hô của người này, không đổi tên)." else "") +
            (if (emo) Emo.instruction() else "") +
            " Chọn xưng hô (tôi, anh, em, cậu, ông, bà...) hợp quan hệ và ngữ cảnh, sửa lỗi đọc chữ rõ ràng, giữ nguyên tên riêng." +
            (if (emo) " Chỉ trả về thẻ rồi bản dịch." else " Chỉ trả về bản dịch.") +
            (if (history.isNotEmpty()) "\nCác câu trước (chỉ để hiểu mạch truyện, không dịch lại):\n" + history.joinToString("\n") else "") +
            "\nCần dịch: $text" +
            (if (strict != null) "\nLƯU Ý NGHIÊM: bản dịch trước bị loại vì $strict. Chỉ trả về đúng một bản dịch tiếng Việt của câu cần dịch: không giải thích, không thêm hay bớt ý, giữ nguyên số và tên riêng." else "")

    private fun glossaryLine(terms: TermMap?, p: Prefs, text: String): String {
        val list = terms?.relevant(text, 30)
            ?: p.glossary.lines().map { it.trim() }.filter { it.contains("=") }.take(40).map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        if (list.isEmpty()) return ""
        return " Bắt buộc dùng đúng các thuật ngữ sau (gốc = tiếng Việt): " + list.joinToString("; ") { it.first + " = " + it.second } + "."
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

    /** Gọi Gemini; hết lượt (429) thì tự đổi sang khóa dự phòng kế tiếp (nếu có) rồi thử lại. */
    private fun geminiPost(p: Prefs, url: String, body: JSONObject): String {
        var left = maxOf(1, p.geminiKeys().size)
        while (true) {
            try {
                return post(url, body, mapOf("x-goog-api-key" to p.geminiActive()))
            } catch (e: AiException) {
                left--
                if (e.code == 429 && left > 0 && p.geminiRotate()) continue
                throw e
            }
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
            val r = geminiPost(p, "https://generativelanguage.googleapis.com/v1beta/models/${p.geminiModel}:generateContent", body)
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
