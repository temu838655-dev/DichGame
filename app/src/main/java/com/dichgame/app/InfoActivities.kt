package com.dichgame.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

/** Hướng dẫn dùng từng bước (5 bước). */
class GuideActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val steps = listOf(
            "Chọn chế độ" to "Ở màn hình chính chọn Dịch (game tiếng Anh, Nhật, Hàn, Trung, Pháp, Đức, Nga, Thái, Ả Rập... 28 thứ tiếng) hoặc Thuyết minh (game, phim đã có phụ đề tiếng Việt, chỉ đọc to).",
            "Bấm Bắt đầu" to "Chọn chơi trên TV hay trên điện thoại. Cấp quyền \"Hiển thị trên các ứng dụng khác\" và cho phép ghi màn hình khi app hỏi.",
            "Đặt khung phụ đề" to "Mở game. Kéo khung xanh tới đúng dòng phụ đề, kéo ô vuông ở góc dưới phải để đổi cỡ khung.",
            "Khóa khung" to "Kéo thông báo xuống, bấm \"Chỉnh/Khóa khung\". Từ đó app tự đọc chữ trong khung và hiện bản dịch ở phía đối diện khung. Vị trí khung được nhớ theo từng hồ sơ game.",
            "Menu, nhiệm vụ, thư" to "Chữ nằm yên (menu, bảng nhiệm vụ, mô tả vật phẩm): bấm \"Dịch menu\" hoặc \"Vùng dịch\" trong thông báo. Xong thì bấm Dừng ở app hoặc chạm thông báo."
        )
        screen("Hướng dẫn") {
            steps.forEachIndexed { i, s ->
                val c = Ui.card(this@GuideActivity)
                val row = Ui.hbox(this@GuideActivity)
                row.gravity = Gravity.TOP
                val num = Ui.tv(this@GuideActivity, (i + 1).toString(), 16f, Color.parseColor("#2A1A08"), true)
                num.gravity = Gravity.CENTER
                num.background = Ui.goldBg(this@GuideActivity, 20)
                row.addView(num, Ui.lp(Ui.dp(this@GuideActivity, 34), Ui.dp(this@GuideActivity, 34)))
                val col = Ui.vbox(this@GuideActivity)
                col.addView(Ui.tv(this@GuideActivity, s.first, 17f, Ui.TEXT, true))
                col.addView(Ui.tv(this@GuideActivity, s.second, 14f, Ui.SUB).apply {
                    setPadding(0, Ui.dp(this@GuideActivity, 4), 0, 0)
                    setLineSpacing(0f, 1.1f)
                })
                row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@GuideActivity, 12) })
                c.addView(row)
                put(c, 0, 10)
            }
        }
    }
}

/** Danh sách tính năng (thay cho màn hình Premium: app này không có phần trả phí). */
class FeaturesActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // (tên, mô tả, đã có?)
        val items = listOf(
            Triple("Phụ đề trên TV", "Phụ đề nổi trên hình game, không trễ. Android TV / Google TV.", true),
            Triple("Giọng đọc qua loa TV", "Điện thoại im lặng, tiếng phát ra từ TV.", true),
            Triple("Dịch vùng hiện trên TV", "Bảng nhiệm vụ, vật phẩm, lựa chọn hội thoại.", true),
            Triple("Dịch AI", "CloudAPI hoặc Google Gemini: giữ đúng tên, xưng hô, nhớ mạch truyện.", true),
            Triple("Hồ sơ game không giới hạn", "Lưu khung quét, tên game và phong cách cho từng game.", true),
            Triple("Dịch video trên web", "Mở trang video, dịch phụ đề ngay khi xem.", true),
            Triple("28 thứ tiếng trong game", "Chữ Latin, Nhật, Hàn, Trung, Hindi đọc trên máy; Nga, Thái, Ả Rập... nhờ Gemini.", true),
            Triple("Từ điển riêng cho từng game", "Ép AI dịch đúng tên nhân vật, chiêu thức, địa danh.", true),
            Triple("Mở app chơi từ xa", "Mở PS Remote Play, Chiaki, Moonlight, Steam Link... rồi hiện bản dịch đè lên.", true),
            Triple("Kiểm tra bản mới, báo lỗi, yêu cầu game", "Ở cuối màn Cài đặt.", true),
            Triple("Điều khiển PS4/PS5 ngay trong app", "Chưa có, hiện dùng app chơi từ xa riêng.", false),
            Triple("App cho TV LG, Samsung", "Chưa có trong bản này.", false),
            Triple("App iOS, Windows, Mac", "Chưa có, hiện chỉ có Android.", false)
        )
        screen("Tính năng") {
            put(Ui.tv(this@FeaturesActivity, "Mọi tính năng đều dùng miễn phí, không gia hạn, không quảng cáo.", 14f, Ui.SUB), 0, 12)
            val c = Ui.card(this@FeaturesActivity)
            items.forEachIndexed { i, it ->
                val row = Ui.hbox(this@FeaturesActivity)
                row.setPadding(0, Ui.dp(this@FeaturesActivity, 10), 0, Ui.dp(this@FeaturesActivity, 10))
                val col = Ui.vbox(this@FeaturesActivity)
                col.addView(Ui.tv(this@FeaturesActivity, it.first, 16f, if (it.third) Ui.TEXT else Ui.SUB, true))
                col.addView(Ui.tv(this@FeaturesActivity, it.second, 13f, Ui.SUB))
                row.addView(col, Ui.lp(0, WRAP_CONTENT, 1f))
                row.addView(Ui.tv(this@FeaturesActivity, if (it.third) "✓" else "–", 20f, if (it.third) Ui.GOLD else Ui.SUB, true))
                c.addView(row)
                if (i < items.size - 1) c.addView(Ui.divider(this@FeaturesActivity), Ui.lp(MATCH_PARENT, 1))
            }
            put(c, 0, 12)
        }
    }
}
