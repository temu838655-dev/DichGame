package com.dichgame.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Private diagnostic log for translation, OCR and speech failures.
 * API credentials are never persisted; only the last four characters may be shown.
 */
object AiErrorLog {
    private const val MAX_MEMORY = 300
    private const val MAX_FILE_BYTES = 256 * 1024
    private val lock = Any()
    private val memory = ArrayDeque<String>()
    @Volatile private var logFile: File? = null

    fun initialize(context: Context) {
        logFile = File(context.applicationContext.filesDir, "ai-events.log")
    }

    fun record(
        level: String = "WARN",
        flow: String = "DỊCH",
        keyIndex: Int? = null,
        key: String? = null,
        httpCode: Int? = null,
        summary: String,
        action: String = "Tiếp tục bằng phương án dự phòng"
    ) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val keyLabel = if (keyIndex != null) {
            val suffix = key.orEmpty().filterNot { it.isWhitespace() || it.isISOControl() }.takeLast(4)
            "Gemini #$keyIndex" + if (suffix.isNotEmpty()) " (…$suffix)" else ""
        } else "—"
        val safeSummary = redact(summary).replace(Regex("[\\r\\n\\t]+"), " ").take(240)
        val safeAction = redact(action).replace(Regex("[\\r\\n\\t]+"), " ").take(160)
        val line = "[$time] | [${level.uppercase(Locale.ROOT)}] | [$flow] | [Khóa: $keyLabel] | [HTTP ${httpCode ?: "—"}] | $safeSummary | $safeAction"
        synchronized(lock) {
            memory.addFirst(line)
            while (memory.size > MAX_MEMORY) memory.removeLast()
            runCatching {
                val file = logFile ?: return@runCatching
                if (file.exists() && file.length() > MAX_FILE_BYTES) {
                    val old = File(file.parentFile, file.name + ".1")
                    if (old.exists()) old.delete()
                    file.renameTo(old)
                }
                file.parentFile?.mkdirs()
                file.appendText(line + "\n", Charsets.UTF_8)
            }
        }
    }

    fun entries(): List<String> = synchronized(lock) {
        val fileLines = runCatching { logFile?.takeIf { it.exists() }?.readLines(Charsets.UTF_8).orEmpty() }.getOrDefault(emptyList())
        (memory.toList() + fileLines.asReversed()).distinct().take(MAX_MEMORY)
    }

    fun clear() {
        synchronized(lock) {
            memory.clear()
            runCatching { logFile?.delete() }
            runCatching { logFile?.let { File(it.parentFile, it.name + ".1").delete() } }
        }
    }

    fun exportText(): String = entries().joinToString("\n")

    /** Guard used at every subtitle/TV egress to keep diagnostic text out of the game view. */
    fun isDiagnosticText(value: String): Boolean {
        val t = value.trim().lowercase(Locale.ROOT)
        if (t.isEmpty()) return true
        return Regex("(^|\\\\b)(http\\\\s*)?4(00|01|03|04|29|08)(\\\\b|:)").containsMatchIn(t) ||
            listOf("lỗi api", "lỗi gemini", "quota", "resource_exhausted", "rate limit",
                "timeout", "exception", "stacktrace", "không kết nối được", "không dịch được",
                "đang tự thử lại", "api key", "invalid_argument", "permission_denied", "không tải được mô hình", "lỗi khi đọc chữ", "chưa lấy được hình", "không chụp được khung hình", "giọng đọc lỗi", "không gửi được tiếng", "không bật được dịch").any { it in t }
    }

    private fun redact(value: String): String {
        var result = value
        // Defensive redaction for accidental provider messages containing credentials.
        result = result.replace(Regex("(?i)(AIza)[A-Za-z0-9_-]{20,}"), "\$1…[REDACTED]")
        result = result.replace(Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~-]{12,}"), "\$1[REDACTED]")
        return result
    }
}
