package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * [retryAfterMs]: máy chủ bảo chờ bao lâu rồi thử lại (0 nếu không nói). [perDay]: hết hạn mức theo ngày (chờ tới hôm sau).
 * [badKey]: chính khóa này sai, hết hạn hoặc bị thu hồi.
 */
class AiException(
    val code: Int, message: String, val retryAfterMs: Long = 0L, val perDay: Boolean = false, val badKey: Boolean = false,
) : Exception(message)

/** Dịch bằng AI đám mây: Google Gemini hoặc CloudAPI (chuẩn OpenAI /chat/completions). Hàm ở đây chặn luồng, hãy gọi từ luồng nền. */
object Ai {
    fun enabled(p: Prefs): Boolean = if (p.aiAuto) p.geminiKeys().isNotEmpty() else when (p.engine) {
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
        strict: String? = null, ui: Boolean = false,
    ): String =
        if (ui) uiPrompt(p, srcName, game, text, terms, strict) else
        "Bạn dịch phụ đề game từ $srcName sang tiếng Việt." +
            (if (game.isNotEmpty()) " Game / phong cách: $game." else "") +
            " " + p.styleText() +
            glossaryLine(terms, p, text) +
            Cast.promptLine(cast, history.joinToString(" ") + " " + text + " " + (speaker ?: "")) +
            (if (speaker != null) " Người đang nói câu này: $speaker (giữ đúng giọng và cách xưng hô của người này, không đổi tên)." else "") +
            (if (emo) Emo.instruction() else "") +
            " Chọn xưng hô hợp quan hệ và ngữ cảnh. Giữ chính xác chủ ngữ, người được nói tới, ngôi xưng (I/you/we/they), phủ định, thời, câu hỏi và mệnh lệnh. Không đổi người thực hiện hành động; không biến câu hỏi thành câu khẳng định. Giữ tên riêng, số, thuật ngữ và mức độ thô tục; không tự thêm câu hoặc tình tiết. Chỉ sửa lỗi OCR khi rõ ràng." +
            (if (emo) " Chỉ trả về thẻ rồi bản dịch." else " Chỉ trả về bản dịch.") +
            (if (history.isNotEmpty()) "\nCác câu trước (chỉ để hiểu mạch truyện, không dịch lại):\n" + history.joinToString("\n") else "") +
            "\nCần dịch: $text" +
            (if (strict != null) "\nLƯU Ý NGHIÊM: bản dịch trước bị loại vì $strict. Chỉ trả về đúng một bản dịch tiếng Việt của câu cần dịch: không giải thích, không thêm hay bớt ý, giữ nguyên số và tên riêng." else "")

    /** Lời nhắc cho chữ giao diện (menu, vật phẩm, kỹ năng, nhiệm vụ...): ngắn gọn, đúng thuật ngữ, giữ nguyên mã và số liệu. */
    private fun uiPrompt(p: Prefs, srcName: String, game: String, text: String, terms: TermMap?, strict: String?): String =
        "Bạn Việt hoá chữ trong giao diện game từ $srcName sang tiếng Việt (menu, cài đặt, vật phẩm, trang bị, kỹ năng, nhiệm vụ, mục tiêu, thông báo, hướng dẫn, mô tả)." +
            (if (game.isNotEmpty()) " Game: $game." else "") +
            glossaryLine(terms, p, text) +
            " Tên vật phẩm, kỹ năng, chiêu thức: ngắn gọn, dễ hiểu, đúng phong cách game. Mô tả: rõ ràng, giữ đúng ý và cơ chế gameplay, không dịch từng chữ máy móc." +
            " Giữ nguyên các mã dạng ⟦số⟧ (đúng vị trí tương ứng), các con số, đơn vị, ký hiệu, chữ viết tắt chỉ số (HP, MP, ATK, XP...), tên riêng và tên thương hiệu." +
            " Không thêm giải thích. Chỉ trả về bản dịch." +
            "\nCần dịch: $text" +
            (if (strict != null) "\nLƯU Ý NGHIÊM: bản dịch trước bị loại vì $strict. Chỉ trả về đúng một bản dịch tiếng Việt, giữ nguyên các mã ⟦số⟧, số và tên riêng." else "")

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
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw AiException(
                code, txt.take(200), AiRetry.retryDelayMs(c.getHeaderField("Retry-After"), txt),
                perDay = code == 429 && KeyRing.isDailyQuota(txt), badKey = KeyRing.isBadKey(code, txt),
            )
            return txt
        } finally {
            c.disconnect()
        }
    }

    /**
     * Chạy [call] với một khóa Gemini còn dùng được. Khóa hết lượt (429) hay sai thì ghi vào [KeyRing] rồi đổi ngay sang khóa kế,
     * không báo lỗi nếu còn khóa khác chạy được. Khóa hết lượt được nghỉ tới giờ hồi nên các lần gọi sau không thử lại khóa đó.
     * [scope] là model (hạn mức tính theo model). Mọi khóa đều không dùng được thì ném [AiException] 429 kèm số mili giây phải chờ
     * (hoặc lỗi sai khóa nếu mọi khóa đều sai).
     */
    fun <T> withGeminiKey(p: Prefs, scope: String, call: (String) -> T): T =
        withGeminiKeys(p.geminiKeys(), scope, call)

    internal fun <T> withGeminiKeys(keys: List<String>, scope: String, call: (String) -> T): T {
        if (keys.isEmpty()) throw AiException(401, "Chưa có khóa Gemini", badKey = true)
        var last: AiException? = null
        val remaining = keys.toMutableList()
        while (remaining.isNotEmpty()) {
            val k = KeyRing.pick(remaining, scope) ?: break
            remaining.remove(k)
            try {
                val r = call(k)
                if (KeyRing.success(k, scope)) SessionStats.live.inc("khoa_doi")
                return r
            } catch (e: AiException) {
                last = e
                if (!KeyRing.report(k, scope, e)) throw e
            }
        }
        if (KeyRing.allBad(keys)) throw last ?: AiException(401, "Mọi khóa Gemini đều sai hoặc bị thu hồi", badKey = true)
        throw AiException(429, "Mọi khóa Gemini đều hết lượt", KeyRing.waitMs(keys, scope), perDay = last?.perDay == true)
    }

    private fun geminiPost(p: Prefs, url: String, body: JSONObject): String =
        withGeminiKey(p, p.geminiModel) { key -> post(url, body, mapOf("x-goog-api-key" to key)) }

    /** Tắt bộ lọc nội dung của Gemini: lời thoại game hay có cảnh đánh nhau, chửi thề... mà bị chặn thì câu đó rơi sang dịch máy. */
    private val SAFETY_CATEGORIES = listOf(
        "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT",
    )
    /** Model nào đó từ chối trường safetySettings (lỗi 400) thì thôi không gửi nữa trong phiên này. */
    @Volatile private var safetyAccepted = true

    private fun geminiBody(prompt: String, withSafety: Boolean): JSONObject {
        val body = JSONObject().put("contents", JSONArray().put(
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        ))
        if (withSafety) body.put("safetySettings", JSONArray().apply {
            for (c in SAFETY_CATEGORIES) put(JSONObject().put("category", c).put("threshold", "BLOCK_NONE"))
        })
        return body
    }

    /** Một lần gọi AI (không thử lại). Dịch câu thì dùng [translateRetry]. */
    fun translate(p: Prefs, prompt: String): String {
        val out: String = if (!p.aiAuto && p.engine == Prefs.ENGINE_CLOUD) {
            val body = JSONObject()
                .put("model", p.cloudModel)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            val r = post(cloudEndpoint(p.cloudUrl), body, mapOf("Authorization" to "Bearer " + p.cloudKey))
            JSONObject(r).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${p.geminiModel}:generateContent"
            val r = try {
                geminiPost(p, url, geminiBody(prompt, safetyAccepted))
            } catch (e: AiException) {
                if (e.code == 400 && safetyAccepted) {
                    // 400 cũng là lỗi khi khóa sai: chỉ khi gửi lại KHÔNG có safetySettings mà được thì mới ghi nhớ là model này không nhận
                    val r2 = geminiPost(p, url, geminiBody(prompt, false))
                    safetyAccepted = false
                    r2
                } else throw e
            }
            AiRetry.geminiText(r)
        }
        val t = out.trim()
        if (t.isEmpty()) throw AiException(0, "Phản hồi rỗng")
        return t
    }

    /** Đang dịch bằng Gemini (cả chế độ AI Tự động, luôn dùng Gemini) hay bằng CloudAPI. */
    private fun usesGemini(p: Prefs): Boolean = p.aiAuto || p.engine == Prefs.ENGINE_GEMINI

    /** Dịch qua CloudAPI hết lượt: tới mốc này (mili giây) mới thử lại. Gemini thì [KeyRing] giữ mốc cho từng khóa. */
    @Volatile private var cooldownUntil = 0L

    /** Còn bao lâu nữa AI mới được thử lại sau khi hết lượt (0 nếu đang dùng được); trong lúc đó câu dịch thẳng bằng dịch máy, khỏi chờ vô ích. */
    fun coolingMs(p: Prefs): Long =
        if (usesGemini(p)) KeyRing.waitMs(p.geminiKeys(), p.geminiModel)
        else (cooldownUntil - System.currentTimeMillis()).coerceAtLeast(0L)

    /**
     * Dịch một câu, tự thử lại khi lỗi tạm thời (hết lượt ngắn hạn, 5xx, quá thời gian, mạng chập chờn). Hết lượt kéo dài thì ghi
     * mốc nghỉ [coolingMs] để các câu sau không phải chờ. Hàm chặn luồng, gọi từ luồng nền.
     */
    fun translateRetry(p: Prefs, prompt: String): String {
        try {
            val r = AiRetry.run(AiRetry.Plan(), { Thread.sleep(it) }) { translate(p, prompt) }
            if (!usesGemini(p)) cooldownUntil = 0L
            return r
        } catch (e: Exception) {
            if (!usesGemini(p) && AiRetry.kindOf(e) == AiRetry.Kind.QUOTA) {
                cooldownUntil = System.currentTimeMillis() + AiRetry.cooldownMs((e as? AiException)?.retryAfterMs ?: 0L)
            }
            throw e
        }
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
