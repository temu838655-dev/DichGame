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
            "Bấm Bắt đầu" to "Chọn chơi trên TV hay trên điện thoại. Chơi ngay trong app (ghép PS5/PS4) thì không cần quyền gì thêm: dịch lấy hình thẳng từ màn chơi, bấm 🔲 để kéo khung tới dòng phụ đề. Chơi bằng app khác (PS Remote Play, Moonlight...) thì cấp quyền \"Hiển thị trên các ứng dụng khác\" và cho phép ghi màn hình khi app hỏi.",
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
            Triple("Lọc chữ rác, tách người nói", "Chữ do nền phức tạp, hiệu ứng, số liệu đọc ra không được dịch; hai người nói khác nhau không bị ghép thành một câu; tên người nói giữ nguyên và có màu riêng.", true),
            Triple("Kiểm tra bản dịch AI", "Bản dịch thiếu số, mất tên riêng, lẫn chữ lạ, lặp từ, từ chối trả lời... bị loại và dịch lại; vẫn nghi ngờ thì đánh dấu (?) chứ không đoán bừa.", true),
            Triple("Nghe âm thanh game bổ sung chữ", "Trùng với chữ trên màn hình thì không hiện hai lần; màn hình thiếu chữ thì lấy lời nghe được; chữ đọc dở thì ghép với phần nghe được. PS Remote Play dùng âm thanh của luồng chơi; game trên điện thoại là tùy chọn thử nghiệm (tắt mặc định), game chặn thu âm thanh thì báo rõ và chỉ dùng micro nếu bạn cho phép.", true),
            Triple("Sửa bản dịch sai", "Sửa các câu vừa dịch rồi lưu vào gói Việt hoá của game: lần sau gặp đúng câu đó dùng bản bạn sửa. Mở từ thông báo hoặc Cài đặt › Phụ đề.", true),
            Triple("Phụ đề không nháy, hiện đủ lâu để đọc", "Thời gian hiện theo độ dài câu và tốc độ nói; câu dồn thì gộp, không bỏ. Hình đứng yên thì không đọc lại để đỡ pin; có chế độ tiết kiệm pin và chất lượng cao.", true),
            Triple("Số đo mỗi lần dịch", "Cài đặt › Phụ đề › Số đo lần dịch gần nhất: số khung bỏ qua, chữ rác bị loại, câu trùng, độ trễ, pin. Chỉ đếm, không lưu lời thoại.", true),
            Triple("Hồ sơ game không giới hạn", "Lưu khung quét, tên game và phong cách cho từng game.", true),
            Triple("Dịch video trên web", "Trình duyệt trong app, bảng dịch ở đáy. Nút dịch bắt đầu / tạm dừng, chọn ngôn ngữ, đọc phụ đề CC hoặc quét chữ (OCR) trong khung bạn kéo, ẩn phụ đề gốc, cỡ chữ, đọc to.", true),
            Triple("28 thứ tiếng trong game", "Chữ Latin, Nhật, Hàn, Trung, Hindi đọc trên máy; Nga, Thái, Ả Rập... nhờ Gemini.", true),
            Triple("Từ điển riêng cho từng game", "Ép tên nhân vật, chiêu thức, địa danh luôn đúng một cách gọi, cả khi dịch bằng AI lẫn dịch trên máy.", true),
            Triple("Kho game Việt hoá", "Hơn 200 game PS5, PS4, Xbox, PC, Steam, Switch, có ảnh bìa. Chọn game là dùng ngay gói Việt hoá của game đó (thuật ngữ, nhân vật, câu thông báo).", true),
            Triple("Gói Việt hoá: nhập, xuất, chia sẻ", "Nhập gói từ file hoặc link (.txt, .csv, .json, .po), sửa thẻ nhân vật, lưu gói của mình ra file. Câu thoại khớp là dùng luôn, không qua bộ dịch.", true),
            Triple("Áp file Việt hoá như trên máy tính", "Tải file chữ Việt hoá của game (en.json + vi.json, strings.xml, .properties, .srt...) rồi ghép 2 file hoặc nhập vào game đó: câu trên màn hình khớp câu gốc trong file (kể cả chữ đọc lệch vài ký tự) thì hiện đúng bản Việt hoá. Câu có biến số bị bỏ qua.", true),
            Triple("Dịch theo cảm xúc và nhịp nhân vật", "Giận, sợ, thì thầm, mỉa mai... đổi giọng đọc, tốc độ và hiện nhãn cảm xúc trên phụ đề. Nhân vật nói chậm hay nhanh đặt trong thẻ nhân vật.", true),
            Triple("11 kiểu dịch và Prompt riêng", "Tự nhiên, thuyết minh, anh – em, bụi bặm, cao bồi, podcast, tin tức... hoặc tự viết yêu cầu cho AI.", true),
            Triple("Liên kết tới các trang Việt hoá cộng đồng", "The Red Team, PS Việt Hóa, Tổ Mèo Cam... mở trang gốc theo nền tảng. App không tải hộ hay chép lại bản vá.", true),
            Triple("Mở app chơi từ xa", "Mở PS Remote Play, Chiaki, Moonlight, Steam Link... rồi hiện bản dịch đè lên.", true),
            Triple("Kiểm tra bản mới, báo lỗi, yêu cầu game", "Ở cuối màn Cài đặt.", true),
            Triple("Chơi PS4/PS5 ngay trong app (thử nghiệm)", "Ghép nối trong app, nhiều người chơi để vừa chơi trên TV vừa chơi trên điện thoại, tay cầm ảo, dịch phụ đề ngay trên màn chơi. Chỉ chạy trên điện thoại arm64; chưa kiểm thử trên mọi máy PS.", true),
            Triple("Tự tải và cài bản vá Việt hoá vào game", "Chưa có. Bản vá do từng nhóm giữ quyền, nên app chỉ dẫn tới trang gốc.", false),
            Triple("Đọc bản vá Việt hoá dạng nhị phân", "Không làm được: file .exe, .pak, .locres, .xdelta... sửa thẳng file game. App chỉ đọc được file chữ (json, xml, strings, properties, srt, csv).", false),
            Triple("App cài lên TV LG, Samsung", "Chưa có app cài lên TV. Dùng được bằng cách chơi trong app rồi chiếu điện thoại lên TV (Smart View, Screen Share, Chromecast, cáp USB-C ra HDMI): xem mục Phụ đề trên TV.", false),
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


/** Ghi công và giấy phép của các thành phần app đang dùng. */
class LicensesActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val items = listOf(
            "Dịch game" to "App viết độc lập, tất cả tính năng dùng miễn phí. Không dùng mã nguồn, hình ảnh hay dữ liệu của app khác.",
            "Google ML Kit" to "Đọc chữ trên màn hình (Latin, Nhật, Hàn, Trung, Devanagari) và dịch trên máy. Theo điều khoản Google ML Kit.",
            "Google Gemini API" to "Dịch AI, giọng đọc AI và đọc chữ Nga, Thái, Ả Rập... Chỉ chạy khi bạn nhập khóa của riêng bạn, theo điều khoản Google AI.",
            "Gói Việt hoá" to "Thuật ngữ, thẻ nhân vật và câu thông báo có sẵn do người làm app soạn theo cách gọi phổ biến của cộng đồng, không chép từ trang nào và không phải bản dịch chính thức. Tên game và nhân vật thuộc về chủ sở hữu của chúng, chỉ dùng để gọi tên.",
            "Trang Việt hoá cộng đồng" to "The Red Team, PS Việt Hóa, Tổ Mèo Cam, NSW Việt Hóa Hub, Vịt Việt Hóa, VuiKey, VietPatch và các kho GitHub thuộc về tác giả của họ. App chỉ có liên kết ra trang gốc, không tải hộ và không chép lại bản vá.",
            "Ảnh bìa game" to "Không đóng gói sẵn trong app. Khi có mạng, app hỏi Steam (Valve) rồi Wikipedia bằng tên game để lấy ảnh bìa (ưu tiên ảnh dọc, không có thì ảnh ngang) và lưu trên máy; tắt được trong Kho game. Ảnh bìa và nhãn hiệu thuộc về nhà phát hành, Valve và các bên giữ bản quyền ảnh. Game không tìm được ảnh dùng ảnh vẽ sẵn của app.",
            "CloudAPI (tùy chọn)" to "Dịch AI qua dịch vụ theo chuẩn OpenAI mà bạn tự nhập địa chỉ và khóa.",
            "Giọng đọc của máy" to "Dùng bộ đọc văn bản thành giọng nói có sẵn trong Android.",
            "Giọng đọc offline" to "Chạy bằng sherpa-onnx (Apache-2.0) và ONNX Runtime (MIT). Giọng Piper VAIS-1000 (CC BY 4.0) là gói tải thêm trong Cài đặt, lấy từ kho phát hành của sherpa-onnx trên GitHub. Bảng phiên âm tiếng Việt của app được tạo bằng eSpeak NG (GPL-3.0) ở máy của người làm app; trong app không có mã eSpeak.",
            "Remote Play trong app" to "Dùng engine chiaki-ng (github.com/streetpea/chiaki-ng), giấy phép AGPL-3.0 kèm ngoại lệ OpenSSL, cùng OpenSSL, libcurl, json-c, miniupnpc, nanopb và Oboe (Apache-2.0). Vì có engine này, bản app có Remote Play phát hành theo AGPL-3.0; mã nguồn đầy đủ nằm ở github.com/temu838655-dev/DichGame. App không liên kết với Sony; PlayStation là nhãn hiệu của Sony Interactive Entertainment.",
            "App chơi từ xa" to "PS Remote Play, Chiaki, Moonlight, Steam Link... là app riêng của bên thứ ba. Dịch game chỉ mở chúng và hiện bản dịch đè lên."
        )
        screen("Giấy phép và ghi công") {
            val c = Ui.card(this@LicensesActivity)
            items.forEachIndexed { i, it ->
                val col = Ui.vbox(this@LicensesActivity)
                col.setPadding(0, Ui.dp(this@LicensesActivity, 10), 0, Ui.dp(this@LicensesActivity, 10))
                col.addView(Ui.tv(this@LicensesActivity, it.first, 16f, Ui.TEXT, true))
                col.addView(Ui.tv(this@LicensesActivity, it.second, 13f, Ui.SUB))
                c.addView(col)
                if (i < items.size - 1) c.addView(Ui.divider(this@LicensesActivity), Ui.lp(MATCH_PARENT, 1))
            }
            put(c, 0, 12)
        }
    }
}
