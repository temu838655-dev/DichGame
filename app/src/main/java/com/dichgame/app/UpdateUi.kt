package com.dichgame.app

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.Toast
import com.dichgame.update.Update
import com.dichgame.update.Updater
import com.dichgame.update.Updater.S

/** Giao diện cập nhật ngay trong app: tấm "Có bản mới", thanh tiến độ tải, và thẻ nhắc trên màn hình chính. */
object UpdateUi {
    @Volatile private var open = 0

    /** Có tấm cập nhật đang mở (để không hiện chồng hai tấm). */
    val showing: Boolean get() = open > 0

    fun mb(b: Long): String = String.format(java.util.Locale("vi", "VN"), "%.1f MB", b / 1048576.0)

    /** Mở tấm cập nhật. [check] = vừa bấm "Kiểm tra bản mới": hỏi lại GitHub ngay. */
    fun show(a: Activity, check: Boolean = false) {
        Ui.sheet(a) { d ->
            val body = Ui.vbox(a)
            addView(body)
            val l = Updater.Listener { s -> if (!a.isFinishing && !a.isDestroyed) draw(a, body, d, s) }
            Updater.addListener(l)
            open++
            d.setOnDismissListener { Updater.removeListener(l); open-- }
            draw(a, body, d, Updater.state)
            if (check) Updater.check(true)
        }
    }

    private fun draw(a: Activity, body: LinearLayout, d: Dialog, s: S) {
        body.removeAllViews()
        val cur = Updater.installedBuild
        fun title(t: String) = body.addView(Ui.tv(a, t, 24f, Ui.TEXT, true))
        fun text(t: String, c: Int = Ui.SUB, top: Int = 8) = body.addView(Ui.tv(a, t, 14.5f, c), Ui.lpm(a, top, 0))
        fun btn(label: String, primary: Boolean, click: () -> Unit) = body.addView(Ui.button(a, label, primary) { click() }, Ui.lpm(a, 12, 0))
        when (s) {
            is S.Idle -> {
                title("Cập nhật")
                text("Bản đang dùng: bản dựng $cur.")
                btn("Kiểm tra bản mới", true) { Updater.check(true) }
                btn("Đóng", false) { d.dismiss() }
            }
            is S.Checking -> {
                title("Đang kiểm tra…")
                text("Đang hỏi GitHub xem có bản mới không.")
            }
            is S.UpToDate -> {
                if (s.dev) {
                    title("Bản tự build")
                    text("Bản này do bạn tự build nên không có số bản dựng để so sánh. Bản mới nhất trên GitHub là bản dựng ${s.latest}.")
                } else {
                    title("Bạn đang dùng bản mới nhất")
                    text("Bản dựng $cur" + if (s.latest > 0) " (bản mới nhất trên GitHub: ${s.latest})." else ".")
                }
                btn("Đóng", false) { d.dismiss() }
            }
            is S.Available -> {
                title("Có bản mới")
                val size = if (s.info.size > 0) " · " + mb(s.info.size) else ""
                text("Bản dựng $cur → bản dựng ${s.info.no}$size", Ui.GOLD, 6)
                if (s.info.notes.isNotEmpty()) {
                    val c = Ui.card(a, 12)
                    c.addView(Ui.tv(a, "Có gì mới", 13f, Ui.SUB, true))
                    c.addView(Ui.tv(a, s.info.notes, 14f, Ui.TEXT).apply { maxLines = 9; ellipsize = android.text.TextUtils.TruncateAt.END }, Ui.lpm(a, 4, 0))
                    body.addView(c, Ui.lpm(a, 12, 0))
                }
                text("App tải bản mới ngay tại đây rồi nhờ Android cài đè. Cài đặt, hồ sơ game và giọng đã tải được giữ nguyên.", Ui.SUB, 12)
                if (Updater.isArm64) {
                    btn("Cập nhật ngay", true) { Updater.start(s.info) }
                } else {
                    text("Máy này không phải chip arm64 nên cần tải bản phù hợp ở trang phát hành.", Ui.SUB, 8)
                    btn("Mở trang phát hành", true) { openPage(a, s.info.page) }
                }
                btn("Để sau", false) { d.dismiss() }
            }
            is S.Downloading -> {
                title("Đang tải bản dựng ${s.info.no}")
                val known = s.total > 0
                val frac = if (known) (s.done.toFloat() / s.total).coerceIn(0f, 1f) else 0f
                body.addView(bar(a, frac, known), Ui.lpm(a, 14, 0, MATCH_PARENT, Ui.dp(a, 10)))
                text(if (known) "${mb(s.done)} / ${mb(s.total)} · ${(frac * 100).toInt()}%" else mb(s.done), Ui.TEXT, 8)
                text("Giữ app mở đến khi tải xong. Mất mạng thì bấm Thử lại, phần đã tải được giữ.", Ui.SUB, 4)
                btn("Hủy", false) { Updater.cancel() }
            }
            is S.Downloaded -> {
                title("Đã tải xong")
                text("Bản dựng ${s.info.no} đã tải về máy. Bấm nút dưới để cài đặt, Android sẽ hỏi xác nhận một lần.")
                btn("Cài đặt ngay", true) { Updater.installNow() }
                btn("Để sau", false) { d.dismiss() }
            }
            is S.NeedPermission -> {
                title("Cho phép cài đặt")
                text("Android cần bạn cho phép Dịch game cài ứng dụng (làm một lần). Bấm nút dưới, bật công tắc \"Cho phép từ nguồn này\" rồi quay lại, app sẽ tự cài tiếp.")
                btn("Mở cài đặt", true) {
                    try { a.startActivity(Updater.permissionIntent()) }
                    catch (_: Exception) {
                        Toast.makeText(a, "Hãy vào Cài đặt > Ứng dụng > Dịch game > Cài ứng dụng không rõ nguồn, rồi bật lên.", Toast.LENGTH_LONG).show()
                    }
                }
                btn("Để sau", false) { d.dismiss() }
            }
            is S.Installing -> {
                title("Đang cài đặt…")
                text("Android sắp hiện hộp thoại xác nhận, hãy bấm Cập nhật (hoặc Cài đặt). Cài xong app tự đóng, bạn mở lại Dịch game là đang dùng bản mới.")
                btn("Đóng", false) { d.dismiss() }
            }
            is S.Failed -> {
                title("Chưa cập nhật được")
                text(s.msg, Color.parseColor("#FFB4A8"))
                val info = s.info
                if (info != null && !Updater.isArm64) btn("Mở trang phát hành", true) { openPage(a, info.page) }
                else btn("Thử lại", true) { if (info != null) Updater.start(info) else Updater.check(true) }
                btn("Đóng", false) { d.dismiss() }
            }
        }
    }

    private fun bar(a: Activity, frac: Float, known: Boolean): View {
        if (!known) return android.widget.ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.GOLD)
        }
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.weightSum = 1000f
        row.background = Ui.bg(a, Color.parseColor("#33FFFFFF"), 5)
        val done = (frac * 1000f).coerceIn(0f, 1000f)
        if (done > 0f) row.addView(View(a).apply { background = Ui.goldBg(a, 5) }, LinearLayout.LayoutParams(0, MATCH_PARENT, done))
        return row
    }

    private fun openPage(a: Activity, url: String) {
        try { a.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(a, "Không mở được trình duyệt.", Toast.LENGTH_SHORT).show() }
    }

    /** Loại trạng thái cần hiện thẻ nhắc trên màn hình chính ("" = không hiện). Dùng để chỉ vẽ lại màn hình khi loại đổi, không vẽ lại theo từng nhịp tải. */
    fun bannerKind(s: S): String = when (s) {
        is S.Available -> "co"
        is S.Downloading -> "tai"
        is S.Downloaded -> "xong"
        is S.NeedPermission -> "quyen"
        is S.Installing -> "cai"
        is S.Failed -> if (s.info != null) "loi" else ""
        else -> ""
    }

    /** Thẻ nhắc cập nhật trên màn hình chính, hiện cho đến khi cập nhật xong. */
    fun banner(a: Activity): View? {
        val s = Updater.state
        val info = when (s) {
            is S.Available -> s.info
            is S.Downloading -> s.info
            is S.Downloaded -> s.info
            is S.NeedPermission -> s.info
            is S.Installing -> s.info
            is S.Failed -> s.info
            else -> null
        } ?: return null
        val sub = when (s) {
            is S.Downloading -> "Đang tải bản dựng ${info.no}…"
            is S.Downloaded -> "Đã tải xong, chạm để cài đặt"
            is S.NeedPermission -> "Cần cho phép cài đặt, chạm để làm tiếp"
            is S.Installing -> "Đang cài đặt…"
            is S.Failed -> "Chưa cập nhật được, chạm để xem"
            else -> "Chạm để cập nhật ngay trong app"
        }
        val row = Ui.hbox(a)
        row.background = Ui.bg(a, Color.parseColor("#2A2108"), 18, Ui.GOLD, 1)
        row.setPadding(Ui.dp(a, 14), Ui.dp(a, 11), Ui.dp(a, 14), Ui.dp(a, 11))
        row.addView(Ui.tv(a, "⬆", 20f, Ui.GOLD, true))
        val col = Ui.vbox(a)
        col.addView(Ui.tv(a, "Có bản mới: bản dựng ${info.no}", 15f, Ui.GOLD, true))
        col.addView(Ui.tv(a, sub, 12.5f, Ui.SUB))
        row.addView(col, Ui.lp(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(a, 12) })
        row.addView(Ui.tv(a, "Cập nhật ›", 14f, Ui.GOLD, true))
        row.isClickable = true
        row.setOnClickListener { show(a) }
        return row
    }
}
