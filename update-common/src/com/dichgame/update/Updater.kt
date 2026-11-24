package com.dichgame.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cập nhật ngay trong app: hỏi GitHub bản mới, tải file APK về bộ nhớ tạm của app rồi nhờ trình cài đặt của Android cài đè.
 * Không mở trình duyệt. Android luôn hỏi người dùng xác nhận một lần trước khi cài đè, và chỉ cho cài khi bản mới ký cùng khóa với bản đang chạy.
 * Dùng chung cho app điện thoại và app TV: mỗi app gọi [init] một lần với số bản dựng và tên file APK của mình.
 */
object Updater {
    const val TAG = "DG_UPD"
    internal const val ACTION = "com.dichgame.update.STATUS"

    sealed class S {
        object Idle : S()
        object Checking : S()
        class UpToDate(val dev: Boolean = false, val latest: Int = 0) : S()
        class Available(val info: Update.Info) : S()
        class Downloading(val info: Update.Info, val done: Long, val total: Long) : S()
        /** Đã tải xong nhưng lúc đó app không ở trước mặt người dùng: đợi họ quay lại rồi mới cài (Android chỉ cho hiện hộp thoại xác nhận khi app đang mở). */
        class Downloaded(val info: Update.Info) : S()
        class NeedPermission(val info: Update.Info) : S()
        class Installing(val info: Update.Info) : S()
        class Failed(val info: Update.Info?, val msg: String) : S()
    }

    fun interface Listener { fun onUpdate(s: S) }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()
    private lateinit var app: Context
    private var buildNo = 0
    private var asset = "DichGame.apk"
    private var lastCheck = 0L
    @Volatile private var cancelled = false
    @Volatile private var working = false
    @Volatile private var visible = false
    private var readyFile: File? = null

    @Volatile var state: S = S.Idle
        private set

    /** Số bản dựng đã tự hiện hộp thoại "Có bản mới" trong lần chạy này, để không hỏi lặp lại. */
    @Volatile var prompted = 0

    fun init(ctx: Context, buildNo: Int, asset: String) {
        app = ctx.applicationContext
        this.buildNo = buildNo
        this.asset = asset
        cleanup()
    }

    private fun ready() = ::app.isInitialized

    /** Màn hình chính của app gọi khi hiện/ẩn: chỉ cài khi app đang ở trước mặt người dùng. */
    fun setVisible(v: Boolean) { visible = v }

    fun addListener(l: Listener) { listeners.addIfAbsent(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun set(s: S) {
        state = s
        Log.i(TAG, "trang-thai " + describe(s))
        for (l in listeners) l.onUpdate(s)
    }

    private fun post(s: S) { main.post { set(s) } }

    private fun describe(s: S): String = when (s) {
        is S.Idle -> "idle"
        is S.Checking -> "dang-kiem-tra"
        is S.UpToDate -> "moi-nhat" + if (s.dev) " (ban tu build)" else ""
        is S.Available -> "co-ban-moi build=${s.info.no} size=${s.info.size}"
        is S.Downloading -> "dang-tai ${s.done}/${s.total}"
        is S.Downloaded -> "da-tai-xong build=${s.info.no}"
        is S.NeedPermission -> "can-quyen-cai-dat"
        is S.Installing -> "dang-cai build=${s.info.no}"
        is S.Failed -> "loi: ${s.msg}"
    }

    val installedBuild: Int get() = buildNo
    val isArm64: Boolean get() = Update.testApi != null || Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

    // ---------------- Kiểm tra ----------------

    /**
     * Hỏi GitHub bản mới nhất. [manual] = người dùng tự bấm: luôn hỏi lại và báo cả khi lỗi.
     * Tự kiểm tra (manual = false) cách nhau ít nhất 15 phút và im lặng khi lỗi mạng.
     */
    fun check(manual: Boolean) {
        if (!ready() || working) return
        val s = state
        if (s is S.Downloading || s is S.Installing || s is S.Checking) return
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheck < 15 * 60_000L && s !is S.Idle) return
        if (buildNo <= 0 && Update.testApi == null && !manual) return  // bản tự build: không có số để so
        working = true
        set(S.Checking)
        Thread({
            var info: Update.Info? = null
            var err: Exception? = null
            try { info = Update.latest(asset) } catch (e: Exception) { err = e }
            main.post {
                working = false
                if (err != null) {
                    Log.w(TAG, "khong kiem tra duoc: $err")
                    set(if (manual) S.Failed(null, "Không kiểm tra được bản mới. Kiểm tra mạng rồi thử lại.") else S.Idle)
                } else {
                    lastCheck = System.currentTimeMillis()
                    val got = info
                    when {
                        got == null -> set(S.UpToDate())
                        buildNo <= 0 && Update.testApi == null -> set(S.UpToDate(dev = true, latest = got.no))
                        got.no > buildNo -> set(S.Available(got))
                        else -> set(S.UpToDate(latest = got.no))
                    }
                }
            }
        }, "dg-update-check").start()
    }

    // ---------------- Tải và cài ----------------

    private fun dir() = File(app.cacheDir, "update").apply { mkdirs() }

    /** Xóa file đã tải của các bản không còn mới hơn bản đang chạy (sau khi cập nhật xong), và file tạm quá cũ. */
    private fun cleanup() {
        try {
            val d = File(app.cacheDir, "update")
            d.listFiles()?.forEach { f ->
                val n = Regex("(\\d+)").find(f.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (n <= buildNo || System.currentTimeMillis() - f.lastModified() > 14L * 24 * 3600 * 1000) f.delete()
            }
        } catch (_: Exception) {}
    }

    /** Tải file APK rồi cài. Gọi từ luồng giao diện. */
    fun start(info: Update.Info) {
        if (!ready() || working) return
        if (!isArm64) {
            set(S.Failed(info, "Máy này không phải chip arm64 nên không cập nhật trong app được. Hãy tải bản phù hợp ở trang phát hành."))
            return
        }
        working = true
        cancelled = false
        set(S.Downloading(info, 0, info.size))
        Thread({
            try {
                val f = download(info)
                if (f == null) {
                    // người dùng bấm Hủy: giữ phần đã tải để lần sau tải tiếp
                    main.post { working = false; set(S.Available(info)) }
                } else {
                    main.post {
                        working = false
                        readyFile = f
                        if (visible) install(info, f) else set(S.Downloaded(info))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "tai loi: $e")
                main.post { working = false; set(S.Failed(info, "Tải file lỗi (${e.message ?: e.javaClass.simpleName}). Kiểm tra mạng rồi bấm Thử lại, phần đã tải sẽ được giữ.")) }
            }
        }, "dg-update-download").start()
    }

    fun cancel() { cancelled = true }

    /** Trả về file đã tải đủ, hoặc null nếu người dùng hủy giữa chừng. */
    private fun download(info: Update.Info): File? {
        val maxBytes = 512L * 1024 * 1024
        if (Update.testApi == null && !Update.validAssetUrl(info.apk, asset)) throw IOException("Đường dẫn APK không hợp lệ")
        if (info.size > maxBytes) throw IOException("APK quá lớn")
        val dir = dir()
        dir.listFiles()?.forEach { if (!it.name.contains("-${info.no}.")) it.delete() }
        val file = File(dir, "${asset.removeSuffix(".apk")}-${info.no}.apk")
        val part = File(dir, file.name + ".part")
        if (file.exists() && file.length() in 1..maxBytes && (info.size <= 0 || file.length() == info.size) && Update.verifyDigest(file, info.sha256)) return file
        file.delete()
        var attempt = 0
        while (true) {
            attempt++
            var have = if (part.exists()) part.length() else 0L
            if (have > maxBytes || (info.size > 0 && have > info.size)) { part.delete(); have = 0L }
            if (info.size > 0 && have == info.size) break
            val c = URL(info.apk).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "DichGame-updater")
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                val code = c.responseCode
                if (code == 416 && attempt < 3) { part.delete(); continue }
                if (code != 200 && code != 206) throw IOException("HTTP $code")
                val append = code == 206
                if (append) {
                    val range = c.getHeaderField("Content-Range").orEmpty()
                    val start = Regex("^bytes (\\d+)-\\d+/(?:\\d+|\\*)$").matchEntire(range)?.groupValues?.get(1)?.toLongOrNull()
                    if (start != have) {
                        part.delete()
                        if (attempt < 3) continue
                        throw IOException("Máy chủ trả sai vị trí tải tiếp")
                    }
                }
                if (!append) have = 0L
                val total = if (info.size > 0) info.size else c.contentLengthLong.let { if (it > 0) it + have else -1L }
                var done = have
                var lastTick = 0L
                FileOutputStream(part, append).use { out ->
                    c.inputStream.use { inp ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled) return null
                            val n = inp.read(buf)
                            if (n < 0) break
                            if (done + n > maxBytes || (info.size > 0 && done + n > info.size))
                                throw IOException("APK vượt kích thước công bố")
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 250) { lastTick = now; post(S.Downloading(info, done, total)) }
                        }
                    }
                }
                post(S.Downloading(info, done, total))
            } finally {
                c.disconnect()
            }
            break
        }
        if (info.size > 0 && part.length() != info.size) {
            part.delete()
            throw IOException("file tải về không đủ dung lượng")
        }
        if (cancelled) return null
        if (!Update.verifyDigest(part, info.sha256)) {
            part.delete()
            throw IOException("Mã SHA-256 của APK không khớp bản phát hành")
        }
        if (!part.renameTo(file)) throw IOException("không lưu được file")
        return file
    }

    /** Gọi khi màn hình mở lại: cài tiếp nếu đã tải xong lúc app ở nền, hoặc người dùng vừa bật quyền "cài ứng dụng không rõ nguồn". */
    fun onResume() {
        val s = state
        val f = readyFile
        if (!ready() || f == null) return
        if (s is S.Downloaded) install(s.info, f)
        else if (s is S.NeedPermission && app.packageManager.canRequestPackageInstalls()) install(s.info, f)
    }

    /** Bấm "Cài đặt ngay" khi file đã tải xong. */
    fun installNow() = onResume()

    /** Mở trang cài đặt quyền "cài ứng dụng không rõ nguồn" của app này. */
    fun permissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + app.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun archiveVersion(path: String): Pair<String, Long>? {
        val pi = app.packageManager.getPackageArchiveInfo(path, 0) ?: return null
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        return (pi.packageName ?: "") to code
    }

    private fun install(info: Update.Info, f: File) {
        if (!app.packageManager.canRequestPackageInstalls()) {
            set(S.NeedPermission(info))
            return
        }
        // Kiểm tra file tải về đúng là bản mới hơn của chính app này trước khi nhờ Android cài
        val av = archiveVersion(f.path)
        val cur = app.packageManager.getPackageInfo(app.packageName, 0)
        val curCode = if (Build.VERSION.SDK_INT >= 28) cur.longVersionCode else @Suppress("DEPRECATION") cur.versionCode.toLong()
        if (av == null || av.first != app.packageName) {
            f.delete()
            set(S.Failed(info, "File tải về không đúng là bản của app này nên không cài. Bấm Thử lại để tải lại."))
            return
        }
        if (buildNo > 0 && av.second != curCode + (info.no - buildNo).toLong()) {
            f.delete()
            set(S.Failed(info, "Mã phiên bản APK không khớp bản phát hành."))
            return
        }
        if (av.second <= curCode) {
            set(S.Failed(info, "File tải về không mới hơn bản đang cài (mã ${av.second} so với $curCode)."))
            return
        }
        try {
            val pi = app.packageManager.packageInstaller
            val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            p.setAppPackageName(app.packageName)
            p.setSize(f.length())
            if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            val id = pi.createSession(p)
            pi.openSession(id).use { s ->
                f.inputStream().use { inp ->
                    s.openWrite("update.apk", 0, f.length()).use { out ->
                        inp.copyTo(out)
                        s.fsync(out)
                    }
                }
                val i = Intent(app, UpdateReceiver::class.java).setAction(ACTION)
                val pend = PendingIntent.getBroadcast(app, id, i, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
                set(S.Installing(info))
                s.commit(pend.intentSender)
            }
            Log.i(TAG, "da-giao-cho-trinh-cai-dat session=$id")
        } catch (e: Exception) {
            Log.w(TAG, "cai loi: $e")
            set(S.Failed(info, "Không bắt đầu cài được (${e.message ?: e.javaClass.simpleName})."))
        }
    }

    /** Kết quả từ trình cài đặt của Android (do [UpdateReceiver] chuyển tới). */
    internal fun installStatus(status: Int, msg: String) {
        Log.i(TAG, "trinh-cai-dat status=$status msg=$msg")
        if (!ready()) return
        val info = when (val s = state) {
            is S.Installing -> s.info
            is S.Failed -> s.info
            else -> null
        }
        val text = when (status) {
            PackageInstaller.STATUS_SUCCESS -> return
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Bạn đã hủy cài đặt. Bấm Thử lại khi sẵn sàng."
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Android báo xung đột với bản đang cài (thường do khác chữ ký). Gỡ app cũ rồi cài bản mới từ trang phát hành."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Bản mới không tương thích với máy này."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Máy hết bộ nhớ. Giải phóng dung lượng rồi thử lại."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android đang chặn việc cài đặt (chưa cho phép cài ứng dụng từ nguồn này, hoặc thiết bị bị khóa)."
            PackageInstaller.STATUS_FAILURE_INVALID -> "File cài đặt không hợp lệ. Bấm Thử lại để tải lại."
            else -> "Cài đặt không thành công" + if (msg.isNotEmpty()) " ($msg)." else "."
        }
        if (status == PackageInstaller.STATUS_FAILURE_INVALID) readyFile?.delete()
        post(S.Failed(info, text))
    }

    /** Dành cho kiểm thử: đưa trạng thái về ban đầu. */
    fun resetForTest() {
        lastCheck = 0L
        working = false
        prompted = 0
        state = S.Idle
    }
}
