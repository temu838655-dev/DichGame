package com.dichgame.app

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray

/**
 * Các đường đưa phụ đề ra ngoài điện thoại ngoài app Dịch game TV (Android TV):
 *  - Thông báo chữ trên TV LG qua [LgTv] (đã ghép ở trang "Phụ đề trên TV");
 *  - Trang web phụ đề [WebSubServer] mở bằng trình duyệt của TV, máy tính bảng hoặc iPhone.
 * [publish] được gọi từ nơi gửi phụ đề (CaptureService, LiveSubs); mọi việc mạng chạy trên luồng nền riêng.
 */
object TvOut {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "dg-tvout").apply { isDaemon = true } }
    private val pending = AtomicReference<String?>(null)
    private val busy = AtomicBoolean(false)

    @Volatile private var lg: LgTv? = null
    @Volatile private var lgIp = ""
    @Volatile private var lgKey = ""
    private var lastToast = ""
    private var lastToastAt = 0L

    /** Lỗi gần nhất khi gửi lên TV LG (rỗng = ổn), để trang TV hiện cho người dùng. */
    @Volatile var lgError = ""

    @Volatile var web: WebSubServer? = null
        private set

    const val WEB_PORT = 8780

    /** Bật máy chủ trang web phụ đề nếu chưa chạy. Trả về cổng, hoặc null nếu không mở được. */
    @Synchronized
    fun startWeb(context: Context? = null): Int? {
        val prefs = context?.applicationContext?.getSharedPreferences("dg", Context.MODE_PRIVATE)
        val w = web?.takeIf { it.running } ?: WebSubServer(WEB_PORT).also { web = it }
        if (prefs != null && context != null) {
            val app = context.applicationContext
            val secureJson = ApiSecrets.read(app, "web_receiver_tokens_v2")
            val savedTokens = if (secureJson.isNotBlank()) {
                prefs.edit().remove("web_receiver_tokens").apply()
                runCatching {
                    val array = JSONArray(secureJson)
                    buildSet { for (i in 0 until array.length()) array.optString(i).takeIf { it.matches(Regex("[0-9a-f]{64}")) }?.let(::add) }
                }.getOrDefault(emptySet())
            } else {
                val legacy = runCatching { prefs.getStringSet("web_receiver_tokens", emptySet()).orEmpty() }.getOrDefault(emptySet())
                if (legacy.isNotEmpty()) {
                    val array = JSONArray().apply { legacy.forEach { put(it) } }
                    if (ApiSecrets.write(app, mapOf("web_receiver_tokens_v2" to array.toString())))
                        prefs.edit().remove("web_receiver_tokens").commit()
                }
                legacy
            }
            w.pairing = WebReceiverPairing(saved = savedTokens, persist = { tokens ->
                val array = JSONArray().apply { tokens.forEach { put(it) } }
                if (ApiSecrets.write(app, mapOf("web_receiver_tokens_v2" to array.toString())))
                    prefs.edit().remove("web_receiver_tokens").commit()
            })
            w.legacyEnabled = prefs.getBoolean("web_legacy", false)
            w.setStyle(WebTvStyle.read(prefs))
        } else if (prefs != null) {
            w.legacyEnabled = prefs.getBoolean("web_legacy", false)
            w.setStyle(WebTvStyle.read(prefs))
        }
        return try { w.start() } catch (_: Exception) { null }
    }

    @Synchronized
    fun stopWeb() {
        web?.stop()
        web = null
    }

    fun publish(ctx: Context, kind: String, text: String, speak: Boolean = false) {
        if (kind !in listOf("sub", "clear", "region")) return
        val t = if (kind == "clear") "" else text
        val p = ctx.applicationContext.getSharedPreferences("dg", Context.MODE_PRIVATE)
        if (p.getBoolean("websub", false)) {
            if (web?.running != true) startWeb(ctx)
            web?.setStyle(WebTvStyle.read(p))
            web?.push(if (kind == "clear" && speak) text else t, kind, speak)
        }
        val ip = p.getString("lgip", "").orEmpty()
        val key = ApiSecrets.read(ctx, "lgkey")
        if (kind == "region") return
        if (t.isBlank()) pending.set(null)
        if (ip.isNotEmpty() && key.isNotEmpty() && p.getBoolean("lgsubs", true) && t.isNotBlank()) {
            if (lgIp != ip || lgKey != key) resetLg()
            lgIp = ip
            lgKey = key
            pending.set(t)
            kick()
        }
    }

    /** Chạy vòng gửi thông báo LG nếu chưa chạy: luôn gửi câu mới nhất, cách nhau tối thiểu 0,6 giây. */
    private fun kick() {
        if (!busy.compareAndSet(false, true)) return
        exec.execute {
            try {
                while (true) {
                    val m = pending.getAndSet(null) ?: break
                    val now = System.currentTimeMillis()
                    if (m == lastToast && now - lastToastAt < 4000) continue
                    val e = sendLg(m)
                    lgError = e.orEmpty()
                    lastToast = m
                    lastToastAt = System.currentTimeMillis()
                    try { Thread.sleep(600) } catch (_: InterruptedException) { break }
                }
            } finally {
                busy.set(false)
            }
            if (pending.get() != null) kick()
        }
    }

    private fun sendLg(m: String): String? {
        val ip = lgIp
        val key = lgKey
        var tv = lg
        if (tv == null || !tv.connected) {
            tv?.close()
            tv = LgTv(ip, key)
            val e = tv.connect(8000)
            if (e != null) { lg = null; return e }
            lg = tv
        }
        var err = tv.toast(m)
        if (err != null) {
            // kết nối có thể đã đứt (TV tắt/bật lại): nối lại một lần
            tv.close()
            val n = LgTv(ip, key)
            if (n.connect(8000) == null) { lg = n; err = n.toast(m) } else { lg = null }
        }
        return err
    }

    /** Dùng khi người dùng bỏ ghép LG hoặc đổi TV. */
    fun resetLg() {
        lg?.close()
        lg = null
        pending.set(null)
    }
}
