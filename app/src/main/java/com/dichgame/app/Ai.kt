package com.dichgame.app

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * [retryAfterMs]: máy chủ bảo chờ bao lâu rồi thử lại (0 nếu không nói). [perDay]: hết hạn mức theo ngày (chờ tới hôm sau).
 * [badKey]: chính khóa này sai, hết hạn hoặc bị thu hồi.
 */
class AiException(
    val code: Int, message: String, val retryAfterMs: Long = 0L, val perDay: Boolean = false, val badKey: Boolean = false, val billingLimit: Boolean = false,
) : Exception(message)

/** Dịch bằng AI đám mây: Google Gemini hoặc CloudAPI (chuẩn OpenAI /chat/completions). Hàm ở đây chặn luồng, hãy gọi từ luồng nền. */
object Ai {
    private val requestDeduplicator = RequestDeduplicator<String, String>()

    /** Fingerprint request scope and active credentials without retaining secrets in the map key. */
    internal fun canonicalRequestText(prompt: String): String = OcrGuard.key(prompt)

    private fun translationRequestKey(p: Prefs, prompt: String): String {
        val providerScope = when {
            p.aiAuto -> "auto|fallback:${p.autoCloudFallback}|gemini:${p.geminiModel}|cloud:${cloudEndpoint(p.cloudUrl)}|${p.cloudModel}"
            p.engine == Prefs.ENGINE_GEMINI -> "gemini|${p.geminiModel}"
            p.engine == Prefs.ENGINE_CLOUD -> "cloud|${cloudEndpoint(p.cloudUrl)}|${p.cloudModel}"
            else -> p.engine
        }
        val credentials = when {
            p.aiAuto -> p.geminiKeys().joinToString("\u0000") + "\u0001" + p.cloudKey
            p.engine == Prefs.ENGINE_GEMINI -> p.geminiKeys().joinToString("\u0000")
            p.engine == Prefs.ENGINE_CLOUD -> p.cloudKey
            else -> ""
        }
        val material = providerScope + "\u0000" + credentials + "\u0000" + canonicalRequestText(prompt)
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    fun enabled(p: Prefs): Boolean = if (p.aiAuto) p.geminiKeys().isNotEmpty() || autoCloud(p) else when (p.engine) {
        Prefs.ENGINE_GEMINI -> p.geminiKeys().isNotEmpty()
        Prefs.ENGINE_CLOUD -> p.cloudKey.isNotEmpty() && p.cloudUrl.isNotEmpty()
        else -> false
    }

    /**
     * Câu lệnh dịch một câu thoại. [terms] là bảng thuật ngữ (chỉ đưa các từ có trong câu, bắt buộc dùng đúng),
     * [cast] là thẻ nhân vật, [emo] bật dịch theo cảm xúc và tiết tấu (AI mở đầu bằng thẻ [cảm xúc|nhịp]).
     */
    internal fun cleanRequestText(value: String, maxChars: Int = 6000): String =
        value.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), "")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .trim()
            .take(maxChars)

    internal fun boundedHistory(history: List<String>, budget: Int = 1200): List<String> {
        var remaining = budget.coerceAtLeast(0)
        val kept = ArrayList<String>()
        for (line in history.asReversed()) {
            val clean = line.trim()
            if (clean.isEmpty()) continue
            if (clean.length + 1 > remaining) break
            kept.add(clean)
            remaining -= clean.length + 1
        }
        return kept.asReversed()
    }

    fun prompt(
        p: Prefs, srcName: String, game: String, history: List<String>, text: String,
        terms: TermMap? = null, cast: List<Character> = emptyList(), emo: Boolean = false, speaker: String? = null,
        strict: String? = null, ui: Boolean = false,
    ): String {
        val recent = boundedHistory(history)
        return if (ui) uiPrompt(p, srcName, game, text, terms, strict) else
        "Bạn dịch phụ đề game từ $srcName sang tiếng Việt." +
            (if (game.isNotEmpty()) " Game / phong cách: $game." else "") +
            " " + p.styleText() +
            glossaryLine(terms, p, text) +
            Cast.promptLine(cast, recent.joinToString(" ") + " " + text, speaker = speaker) +
            (if (speaker != null) " Người đang nói câu này: $speaker (giữ đúng giọng và cách xưng hô của người này, không đổi tên)." else "") +
            (if (emo) Emo.instruction() else "") +
            " Chọn xưng hô (tôi, anh, em, cậu, ông, bà...) hợp quan hệ và ngữ cảnh, sửa lỗi đọc chữ rõ ràng, giữ nguyên tên riêng." +
            (if (emo) " Chỉ trả về thẻ rồi bản dịch." else " Chỉ trả về bản dịch.") +
            (if (recent.isNotEmpty()) "\nCác câu trước (chỉ để hiểu mạch truyện, không dịch lại):\n" + recent.joinToString("\n") else "") +
            "\nCần dịch: $text" +
            (if (strict != null) "\nLƯU Ý NGHIÊM: bản dịch trước bị loại vì $strict. Chỉ trả về đúng một bản dịch tiếng Việt của câu cần dịch: không giải thích, không thêm hay bớt ý, giữ nguyên số và tên riêng." else "")

    }

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

    private fun autoCloud(p: Prefs): Boolean = p.autoCloudFallback &&
        p.cloudUrl.startsWith("https://", ignoreCase = true) && p.cloudKey.isNotBlank() && p.cloudModel.isNotBlank()

    private fun cloudScope(p: Prefs) = "cloud:" + cloudEndpoint(p.cloudUrl) + ":" + p.cloudModel.trim()
    private fun cloudWait(p: Prefs): Long {
        val key = p.cloudKey.filterNot { it.isWhitespace() || it.isISOControl() }
        return if (key.isBlank()) 0L else KeyRing.waitMs(listOf(key), cloudScope(p))
    }

    fun cloudEndpoint(base: String): String {
        val b = base.filterNot { it.isISOControl() }.trim().trimEnd('/')
        return if (b.endsWith("/chat/completions", ignoreCase = true)) b else "$b/chat/completions"
    }

    internal fun post(url: String, body: JSONObject, headers: Map<String, String>, metric: String = "api_dich_gemini"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            SessionStats.live.inc(metric)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw AiException(
                code, AiRetry.providerErrorMessage(txt), AiRetry.retryDelayMs(c.getHeaderField("Retry-After"), txt),
                perDay = code == 429 && KeyRing.isDailyQuota(txt), badKey = KeyRing.isBadKey(code, txt),
                billingLimit = code == 429 && metric == "api_dich_cloud" && KeyRing.isBillingLimit(txt),
            )
            runCatching {
                val json = JSONObject(txt)
                val usage = json.optJSONObject("usageMetadata") ?: json.optJSONObject("usage")
                if (usage != null) {
                    val input = usage.optLong("promptTokenCount", usage.optLong("prompt_tokens", -1))
                    val output = usage.optLong("candidatesTokenCount", usage.optLong("completion_tokens", -1))
                    SessionStats.live.recordTokens(metric, input, output)
                }
            }
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
    fun <T> withGeminiKey(p: Prefs, scope: String, flow: String = "DỊCH", call: (String) -> T): T {
        val keys = p.geminiKeys()
        if (keys.isEmpty()) throw AiException(401, "Chưa có khóa Gemini", badKey = true)
        var last: AiException? = null
        while (true) {
            val k = KeyRing.acquire(keys, scope) ?: break
            val index = keys.indexOf(k) + 1
            try {
                val r = call(k)
                if (KeyRing.success(k, scope)) {
                    SessionStats.live.inc("khoa_doi")
                    AiErrorLog.record("INFO", flow, index, k, null, "Khóa Gemini hoạt động trở lại", "Tiếp tục dùng khóa #$index")
                }
                return r
            } catch (e: AiException) {
                last = e
                val rotated = KeyRing.report(k, scope, e)
                AiErrorLog.record(
                    if (rotated) "WARN" else "ERROR", flow, index, k, e.code,
                    e.message.orEmpty(),
                    if (rotated && !e.billingLimit) "Đổi sang khóa kế tiếp" else "Dừng gọi AI và dùng phương án dự phòng"
                )
                if (!rotated) throw e
                if (e.billingLimit) throw e
            } finally {
                KeyRing.release(k, scope)
            }
        }
        if (KeyRing.allBad(keys)) throw last ?: AiException(401, "Mọi khóa Gemini đều sai hoặc bị thu hồi", badKey = true)
        val billingWait = KeyRing.billingWaitMs(scope)
        if (billingWait > 0L) {
            throw AiException(429, "Tài khoản/dự án AI đã chạm giới hạn thanh toán hoặc chi tiêu.", billingWait, billingLimit = true)
        }
        if (KeyRing.hasInFlight(keys, scope)) {
            throw AiException(503, "Các khóa API đang được request khác sử dụng; thử lại sau.", retryAfterMs = 500L)
        }
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

    internal fun geminiBody(prompt: String, withSafety: Boolean): JSONObject {
        val body = JSONObject().put("contents", JSONArray().put(
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        ))
        body.put("generationConfig", JSONObject().put("maxOutputTokens", 512))
        if (withSafety) body.put("safetySettings", JSONArray().apply {
            for (c in SAFETY_CATEGORIES) put(JSONObject().put("category", c).put("threshold", "BLOCK_NONE"))
        })
        return body
    }

    /** Một lần gọi AI (không thử lại). Dịch câu thì dùng [translateRetry]. */
    fun translate(p: Prefs, prompt: String, forceCloud: Boolean = false): String {
        val safePrompt = cleanRequestText(prompt)
        if (safePrompt.isBlank()) throw AiException(400, "Nội dung yêu cầu trống hoặc chỉ chứa ký tự OCR không hợp lệ.")
        val out: String = if (forceCloud || (!p.aiAuto && p.engine == Prefs.ENGINE_CLOUD)) {
            val endpoint = cloudEndpoint(p.cloudUrl)
            val model = cleanRequestText(p.cloudModel, 100)
            val key = p.cloudKey.filterNot { it.isWhitespace() || it.isISOControl() }
            if (!endpoint.startsWith("https://", true) || !model.matches(Regex("[A-Za-z0-9._:/-]{1,100}")) || key.isBlank())
                throw AiException(400, "Cấu hình CloudAPI không hợp lệ.")
            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", safePrompt)))
            val r = post(endpoint, body, mapOf("Authorization" to "Bearer " + key), metric = "api_dich_cloud")
            JSONObject(r).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            val model = p.geminiModel.trim()
            if (!model.matches(Regex("[A-Za-z0-9._-]{1,100}"))) throw AiException(400, "Tên model Gemini không hợp lệ. Chỉ dùng mã model, không thêm URL hoặc tham số.")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val r = try {
                geminiPost(p, url, geminiBody(safePrompt, safetyAccepted))
            } catch (e: AiException) {
                if (safetyAccepted && AiRetry.shouldRetryWithoutSafetySettings(e.code, e.message.orEmpty())) {
                    // Chỉ fallback khi response nêu rõ safetySettings không tương thích; không retry lỗi key/model/request 400 chung chung.
                    val r2 = geminiPost(p, url, geminiBody(safePrompt, false))
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
        if (p.aiAuto && autoCloud(p)) {
            if (p.geminiKeys().isEmpty()) cloudWait(p)
            else minOf(KeyRing.waitMs(p.geminiKeys(), p.geminiModel), cloudWait(p))
        } else if (usesGemini(p)) KeyRing.waitMs(p.geminiKeys(), p.geminiModel)
        else (cooldownUntil - System.currentTimeMillis()).coerceAtLeast(0L)

    /**
     * Dịch một câu, tự thử lại khi lỗi tạm thời (hết lượt ngắn hạn, 5xx, quá thời gian, mạng chập chờn). Hết lượt kéo dài thì ghi
     * mốc nghỉ [coolingMs] để các câu sau không phải chờ. Hàm chặn luồng, gọi từ luồng nền.
     */
    fun translateRetry(p: Prefs, prompt: String): String {
        val safePrompt = cleanRequestText(prompt)
        if (safePrompt.isBlank()) throw AiException(400, "Nội dung yêu cầu trống.")
        return requestDeduplicator.execute(translationRequestKey(p, safePrompt)) {
            if (p.aiAuto && autoCloud(p)) {
                var primaryError: Exception? = null
                if (p.geminiKeys().isNotEmpty() && KeyRing.waitMs(p.geminiKeys(), p.geminiModel) == 0L) {
                    try { return@execute translate(p, safePrompt) }
                    catch (e: Exception) {
                        if (!AiRetry.canFallback(e)) throw e
                        primaryError = e
                    }
                }
                if (cloudWait(p) > 0L) throw primaryError ?: AiException(429, "CloudAPI đang nghỉ", cloudWait(p))
                try {
                    return@execute translate(p, safePrompt, forceCloud = true)
                } catch (e: AiException) {
                    val key = p.cloudKey.filterNot { it.isWhitespace() || it.isISOControl() }
                    val marked = if (key.isBlank()) false else KeyRing.report(key, cloudScope(p), e)
                    AiErrorLog.record(
                        if (marked) "WARN" else "ERROR", "DỊCH", httpCode = e.code,
                        summary = "CloudAPI …${key.takeLast(4)}: ${e.message.orEmpty()}",
                        action = if (marked) "Tạm khóa CloudAPI và lùi về dịch máy" else "Lùi về dịch máy, không retry payload lỗi"
                    )
                    throw e
                }
            }
            try {
                val r = AiRetry.run(AiRetry.Plan(), { Thread.sleep(it) }) { translate(p, safePrompt) }
                if (!usesGemini(p)) cooldownUntil = 0L
                r
            } catch (e: Exception) {
                if (!usesGemini(p) && AiRetry.kindOf(e) in setOf(AiRetry.Kind.QUOTA, AiRetry.Kind.BILLING)) {
                    val apiError = e as? AiException
                    val cloudKey = p.cloudKey.filterNot { it.isWhitespace() || it.isISOControl() }
                    if (apiError != null && cloudKey.isNotBlank()) {
                        val marked = KeyRing.report(cloudKey, cloudScope(p), apiError)
                        if (marked || apiError.code in 400..499) {
                            AiErrorLog.record(
                                if (marked) "WARN" else "ERROR", "DỊCH", httpCode = apiError.code,
                                summary = "CloudAPI …${cloudKey.takeLast(4)}: ${apiError.message.orEmpty()}",
                                action = if (marked) "Đánh dấu CloudAPI và dùng dịch máy" else "Không retry payload; dùng dịch máy"
                            )
                        }
                    }
                    val cooldown = if (AiRetry.kindOf(e) == AiRetry.Kind.BILLING) {
                        maxOf(60 * 60_000L, apiError?.retryAfterMs ?: 0L)
                    } else {
                        AiRetry.cooldownMs(apiError?.retryAfterMs ?: 0L)
                    }
                    cooldownUntil = System.currentTimeMillis() + cooldown
                }
                throw e
            }
        }
    }

    /** Thử dịch một câu để kiểm tra khóa. Trả về chuỗi bắt đầu bằng "OK" nếu chạy được, ngược lại là lý do lỗi. */
    fun test(p: Prefs, forceCloud: Boolean = false): String = try {
        "OK: " + translate(p, prompt(p, "tiếng Anh", "", emptyList(), "Hello, how are you?"), forceCloud = forceCloud)
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
