package com.dichgame.app

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Ghi lại vì sao app đóng đột ngột lần trước, để lần mở sau còn biết đường sửa.
 *
 * - Lỗi Java: bắt ở bộ xử lý lỗi toàn app, lưu dòng đầu của dấu vết.
 * - Lỗi native (thư viện Remote Play bị sập, Java không bắt được): Android 11 trở lên có sẵn lý do thoát của lần chạy trước.
 * - "Dấu chân": vài dòng ghi nhanh mỗi bước quan trọng (bắt đầu kết nối, có hình, bật dịch...) vào tệp, ghi trực tiếp
 *   xuống hệ thống tệp nên vẫn còn sau khi tiến trình chết đột ngột.
 */
object CrashLog {
    private const val TRAIL = "dg-trail.txt"
    private const val PREF = "dg"
    private const val KEY_SEEN = "exit_seen_ts"
    private const val KEY_JAVA = "java_crash"
    private const val MAX_TRAIL_BYTES = 6000

    private val lock = Any()
    @Volatile private var appCtx: Context? = null
    private var shownThisProcess = false

    fun install(app: Application) {
        appCtx = app.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder()
                sb.append(stamp()).append(" · luồng ").append(t.name).append('\n')
                sb.append(e.javaClass.name).append(": ").append(e.message ?: "").append('\n')
                e.stackTrace.take(12).forEach { sb.append("  at ").append(it).append('\n') }
                var c = e.cause
                var n = 0
                while (c != null && n < 2) {
                    sb.append("Gây ra bởi ").append(c.javaClass.name).append(": ").append(c.message ?: "").append('\n')
                    c.stackTrace.take(5).forEach { sb.append("  at ").append(it).append('\n') }
                    c = c.cause; n++
                }
                app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_JAVA, sb.toString().take(3500)).commit()
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun stamp(): String = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    /** Ghi một dấu chân. Không bao giờ ném lỗi. */
    fun mark(step: String) {
        val c = appCtx ?: return
        try {
            synchronized(lock) {
                val f = File(c.filesDir, TRAIL)
                if (f.length() > MAX_TRAIL_BYTES) {
                    val keep = f.readLines().takeLast(25)
                    f.writeText(keep.joinToString("\n", postfix = "\n"))
                }
                f.appendText(stamp() + " " + step + "\n")
            }
        } catch (_: Throwable) {
        }
    }

    /** Dấu chân còn lại (các dòng cuối), rỗng nếu không có. */
    fun trail(max: Int = 12): List<String> {
        val c = appCtx ?: return emptyList()
        return try {
            synchronized(lock) {
                val f = File(c.filesDir, TRAIL)
                if (f.exists()) f.readLines().takeLast(max) else emptyList()
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun clearTrail() {
        val c = appCtx ?: return
        try { synchronized(lock) { File(c.filesDir, TRAIL).delete() } } catch (_: Throwable) {}
    }

    /** Lý do thoát đáng báo. Bị hệ thống dọn khi chạy nền (hết bộ nhớ, người dùng vuốt tắt...) thì không báo. */
    private fun reasonName(r: Int, dirty: Boolean): String? = when (r) {
        4 -> "Lỗi chương trình (Java) làm app dừng"
        5 -> "Thư viện gốc (native) bị sập"
        6 -> "App không phản hồi (ANR)"
        7 -> "App không khởi động được"
        9 -> "App dùng quá nhiều tài nguyên"
        2 -> if (dirty) "Tiến trình bị chấm dứt bằng tín hiệu khi đang chơi/dịch (thường do thư viện gốc sập)" else null
        else -> null
    }

    /** Lần chạy trước còn đang trong màn chơi hoặc đang dịch khi bị đóng (dấu chân cuối không phải dấu kết thúc). */
    private fun wasBusy(): Boolean {
        val m = trail(60).lastOrNull { it.contains("choi:") || it.contains("dich:") || it.contains("capture:") } ?: return false
        return !(m.contains("choi:xong") || m.contains("dich:tat"))
    }

    /**
     * Nội dung báo lỗi cho lần chạy trước, hoặc null nếu lần trước thoát bình thường. Mỗi sự cố chỉ báo một lần.
     */
    fun pending(ctx: Context): String? {
        if (shownThisProcess) return null
        shownThisProcess = true
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val sb = StringBuilder()
        val javaCrash = sp.getString(KEY_JAVA, null)
        if (!javaCrash.isNullOrBlank()) {
            sb.append("Lỗi chương trình lần trước:\n").append(javaCrash.trim()).append("\n\n")
        }
        val seen = sp.getLong(KEY_SEEN, 0L)
        var latest = seen
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val dirty = wasBusy()
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val list = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 8)
                for (i in list) {
                    if (i.timestamp <= seen) continue
                    latest = maxOf(latest, i.timestamp)
                    val name = reasonName(i.reason, dirty)
                    if (name != null) {
                        sb.append("Hệ thống ghi nhận: ").append(name)
                        val d = i.description
                        if (!d.isNullOrBlank()) sb.append(" — ").append(d.take(300))
                        sb.append("\n\n")
                        break
                    }
                }
            } catch (_: Throwable) {
            }
        } else if (javaCrash.isNullOrBlank()) {
            // Android cũ không có lý do thoát: nếu màn chơi đã bắt đầu mà không có dấu kết thúc thì nghi là bị đóng đột ngột
            val t = trail(40).lastOrNull { it.contains("choi:") }
            if (t != null && !t.contains("choi:xong")) sb.append("Lần trước màn chơi có thể đã đóng đột ngột.\n\n")
        }
        val had = sb.isNotEmpty()
        if (had) {
            val tr = trail(12)
            if (tr.isNotEmpty()) sb.append("Các bước cuối trước khi đóng:\n").append(tr.joinToString("\n")).append('\n')
        }
        sp.edit().remove(KEY_JAVA).putLong(KEY_SEEN, maxOf(latest, System.currentTimeMillis() - 1)).apply()
        clearTrail()
        return if (had) sb.toString().trim() else null
    }

    fun showIfAny(a: Activity) {
        val msg = try { pending(a) } catch (_: Throwable) { null } ?: return
        try {
            AlertDialog.Builder(a, R.style.DgDialog)
                .setTitle("Lần trước app bị đóng đột ngột")
                .setMessage(msg)
                .setPositiveButton("Đóng", null)
                .setNegativeButton("Sao chép") { _, _ ->
                    try {
                        (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("dichgame-loi", msg))
                    } catch (_: Throwable) {}
                }
                .show()
        } catch (_: Throwable) {
        }
    }
}

/** Lớp Application: cài bộ ghi lỗi trước khi bất kỳ màn hình nào chạy. */
class DgApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Cập nhật ngay trong app (số bản dựng và tên file APK trong bản phát hành của app điện thoại)
        com.dichgame.update.Updater.init(this, BuildConfig.BUILD_NO, "DichGame.apk")
    }
}
