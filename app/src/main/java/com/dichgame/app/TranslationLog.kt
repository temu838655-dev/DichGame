package com.dichgame.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Nhật ký riêng cho API/OCR/TTS. Không ghi nội dung câu thoại, payload, token hay khóa đầy đủ. */
object TranslationLog {
    enum class Health { HEALTHY, FALLBACK, FAILED }
    private const val PREFS = "dg_translation_log"
    private const val HEALTH_KEY = "health"
    private const val DETAIL_KEY = "health_detail"
    private const val FILE_NAME = "dichgame-runtime.log"
    private const val MAX_BYTES = 512 * 1024L
    @Volatile private var app: Context? = null
    @Volatile private var currentHealth = Health.HEALTHY
    @Volatile private var detail = "Chưa ghi nhận sự cố"
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "dg-translation-log").apply { isDaemon = true } }

    fun init(context: Context) {
        app = context.applicationContext
        val prefs = app!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentHealth = runCatching { Health.valueOf(prefs.getString(HEALTH_KEY, Health.HEALTHY.name)!!) }.getOrDefault(Health.HEALTHY)
        detail = prefs.getString(DETAIL_KEY, "Chưa ghi nhận sự cố") ?: "Chưa ghi nhận sự cố"
    }

    fun health(): Health = currentHealth
    fun healthDetail(): String = detail

    fun setHealth(value: Health, reason: String) {
        val changed = currentHealth != value || detail != reason
        currentHealth = value
        detail = reason.replace('\n', ' ').take(180)
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(HEALTH_KEY, value.name)?.putString(DETAIL_KEY, detail)?.apply()
        if (changed) write("INFO", "Status", null, null, null, reason)
    }

    fun write(level: String, provider: String, keyIndex: Int?, key: String?, code: Int?, action: String) {
        val safeLevel = level.uppercase(Locale.ROOT).takeIf { it == "INFO" || it == "WARN" || it == "ERROR" } ?: "INFO"
        val line = formatLine(System.currentTimeMillis(), safeLevel, provider, keyIndex, key, code, action)
        Log.println(when (safeLevel) { "ERROR" -> Log.ERROR; "WARN" -> Log.WARN; else -> Log.INFO }, "DG_TRANSLATION", line)
        val context = app ?: return
        try {
            io.execute {
                runCatching {
                    val file = File(context.filesDir, FILE_NAME)
                    file.parentFile?.mkdirs()
                    if (file.exists() && file.length() > MAX_BYTES) {
                        val old = file.readBytes()
                        file.writeBytes(old.copyOfRange((old.size / 2).coerceAtLeast(0), old.size))
                    }
                    file.appendText(line + "\n", Charsets.UTF_8)
                }
            }
        } catch (_: Exception) { }
    }

    internal fun formatLine(at: Long, level: String, provider: String, keyIndex: Int?, key: String?, code: Int?, action: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(at))
        val keyPart = if (keyIndex == null) "—" else {
            val raw = key.orEmpty().trim()
            val suffix = if (raw.length >= 4) raw.takeLast(4) else "****"
            "Key #" + keyIndex + " (****" + suffix + ")"
        }
        val codePart = if (code == null) "—" else "HTTP " + code
        val safeAction = action.replace('\n', ' ').replace('\r', ' ')
            .replace(Regex("AIza[A-Za-z0-9_-]{20,}")) { it.value.take(4) + "****" }
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._-]{12,}")) { "Bearer ****" }
            .take(240)
        return "[$stamp] | [$level] | [$provider $keyPart] | [$codePart] | [$safeAction]"
    }

    fun read(): String {
        val context = app ?: return ""
        return try {
            io.submit<String> { runCatching { File(context.filesDir, FILE_NAME).takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty() }.getOrDefault("") }
                .get(2, TimeUnit.SECONDS)
        } catch (_: Exception) { "Không đọc được nhật ký. Hãy thử làm mới." }
    }

    fun clear() {
        val context = app ?: return
        try { io.execute { runCatching { File(context.filesDir, FILE_NAME).delete() } } } catch (_: Exception) { }
    }

    fun statusLabel(): String = when (currentHealth) {
        Health.HEALTHY -> "XANH · TỐT"
        Health.FALLBACK -> "VÀNG · DỰ PHÒNG"
        Health.FAILED -> "ĐỎ · LỖI"
    }
}
