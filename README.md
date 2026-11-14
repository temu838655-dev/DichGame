# Dịch game

Dịch phụ đề game và phim sang tiếng Việt ngay trên màn hình, có thể đọc to, có thể đẩy phụ đề lên TV.

- `app/` – app điện thoại Android (`com.dichgame.app`): chụp màn hình, đọc chữ (OCR), dịch, hiện bản dịch nổi.
- `tv/` – app nhận phụ đề cho Android TV / Google TV (`com.dichgame.tv`), vẽ nổi trên hình HDMI.
- `keystore/` – khóa ký APK mặc định (xem mục "Khóa ký").
- `.github/workflows/build-apk.yml` – tự build toàn bộ trên GitHub.

## Tính năng (app điện thoại)

| Màn hình | Có gì |
| --- | --- |
| Chính | Chuyển **Dịch / Thuyết minh**, chọn ngôn ngữ game (**28 thứ tiếng**, xem mục "Ngôn ngữ đọc được"), nút Bắt đầu, hộp **"Bạn chơi ở đâu?"** (TV / điện thoại) |
| Cách dịch | **Dịch trên máy** (miễn phí, offline), **CloudAPI** (AI theo chuẩn OpenAI, nhập địa chỉ + khóa + model), **Google Gemini** (khóa + model), nút **Tải gói ngôn ngữ** (tải trước mô hình dịch trên máy), nút **Lưu và kiểm tra**, số câu trước cho AI hiểu mạch truyện |
| Cài đặt › Giọng đọc | **Giọng của máy**, **giọng AI Gemini** (8 giọng) hoặc **giọng offline** tải thêm về máy (chạy không cần mạng; AI và offline lỗi thì tự lùi về giọng máy), tốc độ 0,5x–3x, **đọc theo cảm xúc**, **tự đọc nhanh khi câu dồn lại**, đọc qua loa TV, nghe thử |
| Cài đặt › Phụ đề | Hiện câu gốc, **hiện câu thoại trước** (chữ nhỏ phía trên), cỡ chữ 50–200 %, kiểu chữ, mục **Nâng cao** (tần suất quét, chữ hiện dần, dịch menu tự động) |
| Kho game | Tìm kiếm, lọc theo nền tảng (**PS5, PS4, Xbox, PC, Steam, Switch**), tab **Đã chơi / Tất cả**, tab trạng thái có số đếm **Tất cả / Việt hoá / Dịch AI**, **ảnh bìa** từng game (tải từ Steam theo tên game, lưu trên máy, tắt được; game không có trên Steam dùng ảnh vẽ sẵn), nhãn **VIỆT HÓA** trên ô, nút **Sao chép tên game**, nút **Game khác**. Bấm một game để xem **gói Việt hoá**, cách chơi và dịch theo nền tảng, rồi **Chọn game này** hoặc **Chọn và dịch ngay**: app tạo (hoặc chuyển sang) hồ sơ tên game đó và dùng gói Việt hoá của game. Hơn 200 game phổ biến (ảnh bìa tải từ Steam khi có mạng) |
| Gói Việt hoá | Mỗi game có sẵn **thuật ngữ** (tên riêng, chiêu thức, địa danh), **thẻ nhân vật** (xưng hô, giọng, nhịp nói) và **câu thông báo chuẩn** ("YOU DIED", "Quest complete"...). Áp dụng khi dịch bằng AI lẫn dịch trên máy: câu thoại khớp thì dùng luôn, không qua bộ dịch (chịu được lỗi đọc chữ như `Y0U D1ED`). Nhập gói từ file hoặc link, sửa thẻ nhân vật, lưu gói của mình ra file để chia sẻ. Gói có sẵn do người làm app soạn theo cách gọi phổ biến, **không phải bản dịch chính thức** của nhà phát hành |
| Dịch theo cảm xúc | AI gắn thẻ cảm xúc và nhịp cho mỗi câu (không có thì đoán bằng từ khóa); giọng đọc đổi tốc độ, cao độ, kiểu nói; phụ đề hiện nhãn màu (vd "giận dữ · nhanh"). Nhịp riêng của từng nhân vật đặt trong thẻ nhân vật. Tắt được trong Gói Việt hoá |
| Cài đặt › Hồ sơ game | Nhiều hồ sơ, mỗi hồ sơ nhớ tên game + vùng quét riêng, **11 kiểu dịch** (Tự nhiên, Thuyết minh, Ngắn gọn, Sát nghĩa, Anh – em, Bụi bặm, Cao bồi, Podcast, Tin tức, Học tập) và **Prompt riêng** (tự viết yêu cầu cho AI), **từ điển riêng** |
| Việt hoá cộng đồng | Trong Kho game và Gói Việt hoá có liên kết tới The Red Team, PS Việt Hóa, Tổ Mèo Cam, NSW Việt Hóa Hub, Vịt Việt Hóa, VuiKey, VietPatch... theo nền tảng. **Chỉ mở trang gốc**: app không tải hộ, không đăng lại bản vá (mỗi bản vá có điều khoản của tác giả) |
| Phụ đề trên TV | Chọn hãng TV, hướng dẫn 4 bước, tìm TV trong mạng, ghép bằng mã 8 số, gửi câu thử |
| Dịch video trên web | Trình duyệt trong app với bảng dịch ở đáy và thanh công cụ: nút dịch (bắt đầu / tạm dừng, nhấn giữ để chọn ngôn ngữ), nguồn chữ **CC** (phụ đề của trình phát: thẻ track, YouTube, video.js, JW Player, Plyr...) hoặc **OCR** (quét chữ trong khung chọn vùng kéo được), chữ hiện dần, thuyết minh, ẩn phụ đề gốc, cỡ chữ, đọc to, cài đặt |
| Chơi trên điện thoại | Chọn **Chơi trên điện thoại** để mở app chơi từ xa đã cài (PS Remote Play, Chiaki, Moonlight, Steam Link, GeForce NOW, Xbox) và hiện bản dịch đè lên hình game. |
| Remote Play tích hợp (thử nghiệm) | Chơi và dịch ngay trong app, không cần mở PS Remote Play. Trình ghép nối 3 bước (tìm máy trong LAN hoặc nhập IP, PSN Account ID dạng Base64, mã 8 số trên máy); nhiều người chơi cho mỗi máy, nên dùng người chơi phụ để vừa chơi trên TV vừa chơi trên điện thoại; đánh thức máy từ chế độ nghỉ; chất lượng 720p/1080p và tốc độ mạng chỉnh được; tay cầm ảo đa chạm (dọc và ngang) hoặc tay cầm thật nối điện thoại; nút bật/tắt dịch phụ đề ngay trên màn chơi; kết nối lại và nhập PIN khi máy yêu cầu. Engine Chiaki-ng hiện chỉ build cho arm64-v8a. Chưa xác nhận tương thích mọi firmware/router; cần thử trên PS4/PS5 thật. Không nhập mật khẩu PSN vào app. |
| Cài đặt › Giới thiệu và hỗ trợ | Xem phiên bản, **Kiểm tra bản mới** (so với trang Releases), **Báo lỗi / yêu cầu game** (mở GitHub Issues điền sẵn thông tin máy) |
| Hướng dẫn / Tính năng | Hướng dẫn 5 bước, danh sách tính năng |

Trong lúc chạy, thông báo có 3 nút: **Chỉnh/Khóa khung**, **Dịch menu**, **Vùng dịch**.

## Build trên GitHub

1. Tạo repo mới trên GitHub, đẩy **toàn bộ** thư mục này lên (nhớ cả thư mục ẩn `.github` và thư mục `keystore`).
   Dùng dòng lệnh: `git init && git add -A && git commit -m "Dịch game" && git branch -M main && git remote add origin <địa chỉ repo> && git push -u origin main`
2. Vào tab **Actions** › workflow **Build APK**. Nó chạy tự động mỗi lần đẩy code, hoặc bấm **Run workflow**.
3. Khi xong có 2 file APK:
   - mục **Artifacts** của lần chạy (`DichGame-APKs`), và
   - mục **Releases** của repo (khi đẩy lên nhánh `main`/`master` hoặc gắn tag `v*`), tải thẳng được bằng điện thoại.

| File | Dùng cho |
| --- | --- |
| `DichGame.apk` | Cài lên điện thoại Android (chip arm64, tức hầu hết điện thoại từ Android 8 trở lên). Đây là bản chính, có cả Remote Play trong app |
| `DichGame-TV.apk` | Cài lên Android TV |

Mỗi lần phát hành chỉ có hai file trên. Workflow tự xóa các bản `build-N` cũ, chỉ giữ 2 bản mới nhất. Cần bản debug hay bản cho chip 32-bit thì build tay bằng `gradle :app:assembleDebug` hoặc `:app:assembleRelease` (file nằm trong `app/build/outputs/apk/`).

Phát hành bản có số phiên bản: `git tag v2.0 && git push origin v2.0`.
Mỗi bản dựng trên nhánh `main` có số thứ tự (`build-N`), số này được ghi vào app (versionCode = 20 + N) để app biết có bản mới hơn khi bấm **Kiểm tra bản mới**. Cài đè lên bản cũ được vì cùng khóa ký.

### Khóa ký

Mặc định APK ký bằng khóa trong `keystore/dichgame.jks` (mật khẩu `dichgame123`). Khóa này nằm công khai trong repo, chỉ hợp để dùng cá nhân.
Muốn dùng khóa riêng: tạo 4 secrets trên GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), workflow sẽ tự dùng.
Lưu ý: đổi khóa thì phải gỡ app cũ rồi cài lại.

## Ghép TV

1. Cài `DichGame-TV.apk` lên TV (app **Downloader** trên TV, nhập đường dẫn tải file trong Releases; hoặc USB).
2. Mở **Dịch game TV**, cấp quyền "Hiển thị trên ứng dụng khác".
   Nếu TV không có menu này: `adb shell appops set com.dichgame.tv SYSTEM_ALERT_WINDOW allow`.
3. TV hiện địa chỉ IP và mã 8 số (đổi mỗi 60 giây).
4. Trên điện thoại: **Phụ đề trên TV** › **Tìm TV trong mạng** (hoặc nhập IP) › nhập mã › **Ghép TV** › **Gửi câu thử lên TV**.
5. Điện thoại và TV phải cùng Wi-Fi. Cổng 8765 (TCP) và 8766 (UDP, để tìm TV).
6. Khi bấm Bắt đầu chọn **Chơi trên TV** thì phụ đề (và giọng đọc, nếu bật "Đọc qua loa TV") được đẩy lên TV.

## Ngôn ngữ đọc được

| Cách đọc | Ngôn ngữ |
| --- | --- |
| Trên máy (nhanh, offline, miễn phí) | Anh, Nhật, Hàn, Trung, Pháp, Đức, Tây Ban Nha, Bồ Đào Nha, Ý, Indonesia, Hà Lan, Ba Lan, Thổ Nhĩ Kỳ, Hindi, Thụy Điển, Séc, Romania, Hungary, Đan Mạch, Phần Lan, Na Uy, Mã Lai |
| Nhờ Google Gemini đọc ảnh (cần khóa Gemini miễn phí từ aistudio.google.com) | Nga, Ukraina, Thái, Ả Rập, Hy Lạp, Do Thái |

Với nhóm Gemini, chỉ ảnh phần màn hình cần đọc (khung phụ đề, hoặc vùng bạn chọn) được gửi cho Google, và chỉ khi bạn chọn các thứ tiếng này. Bản dịch sang tiếng Việt vẫn có thể chạy trên máy.
Lần đầu dùng mỗi thứ tiếng, app tải mô hình dịch nên cần mạng.

## Giới hạn hiện tại

- Chưa có app cho **TV LG (webOS)** và **TV Samsung (Tizen)**; chỉ Android TV / Google TV.
- **Giấy phép Remote Play**: engine Chiaki-ng (`third_party/chiaki-ng`) theo **AGPL-3.0-only kèm ngoại lệ OpenSSL**. Bản APK có Remote Play vì vậy phải được phát hành theo AGPL-3.0 và giữ mã nguồn công khai; bản dựng với `-PnoNative` (dùng cho smoke test) không chứa engine này. Chưa có tệp LICENSE chung cho repo, chủ dự án cần chọn giấy phép AGPL-3.0 cho phần mã còn lại nếu muốn phát hành công khai.
- Remote Play tích hợp PS4/PS5 hiện ở trạng thái **thử nghiệm**. Chưa kiểm thử kết nối, hình ảnh, âm thanh và điều khiển trên đầy đủ model/firmware console; cần người dùng thử với máy thật và gửi log khi gặp lỗi. Bản arm64 có engine Chiaki-ng; các ABI khác không có engine Remote Play.
- Chưa có app cho **iOS, Windows, Mac**, và chưa có **kho Việt hoá tự cập nhật kèm bot gửi yêu cầu** (cần máy chủ). Thay vào đó có mục báo lỗi / yêu cầu game dẫn tới GitHub Issues.
- Giao diện chỉ có tiếng Việt.
- Chữ Nga, Ukraina, Thái, Ả Rập, Hy Lạp, Do Thái đọc chậm hơn (khoảng 1–3 giây mỗi câu) vì phải nhờ Gemini đọc ảnh; ở chế độ này tắt "Dịch menu tự động" (vẫn dùng được nút "Dịch menu" và "Vùng dịch").
- Giọng của máy là các giọng tiếng Việt cài sẵn (Cài đặt › Ngôn ngữ và nhập › Chuyển văn bản thành giọng nói). Giọng AI dùng chung khóa Google Gemini với bộ dịch, tên model mặc định `gemini-3.8-flash-tts` (đổi được trong app nếu Google đổi tên).
- **Giọng offline**: dùng thư viện sherpa-onnx (tự tải về lúc build, nằm ở `app/libs/`, không để trong git) và giọng Piper VAIS-1000 tải thêm trong app (khoảng 65 MB). Khi bật "Đọc qua loa TV", điện thoại tự tạo âm thanh offline rồi gửi sang app TV phát bằng loa TV (cần cài app TV bản mới; TV cũ thì đọc bằng giọng của TV). Chưa có giọng nam, và mới có một giọng. Vì sherpa-onnx từ bản 2.0 không còn eSpeak, app tự đổi chữ sang phiên âm bằng bảng `app/src/main/assets/vi_phon.txt`, tạo bởi `tools/gen_vi_phon.py` (cần eSpeak NG ở máy dựng bảng, xem đầu file). Số và ký hiệu được đọc thành chữ; tên riêng, chữ nước ngoài được đọc theo kiểu tiếng Việt nên có thể nghe lạ. Smoke test trên máy giả lập có tự tải giọng và đọc thử một câu (SelfTest.kt) để chắc rằng thư viện chạy được; chất lượng nghe thật vẫn cần bạn nghe trên máy. Nghe không ổn thì dùng mục "Báo lỗi" trong app.
- "Đọc theo cảm xúc" dùng thẻ cảm xúc do AI gắn khi dịch bằng AI; dịch trên máy thì đoán bằng từ khóa và dấu câu nên kém chính xác hơn.
- "Dịch video trên web" phụ thuộc trang web có để lộ phụ đề cho trình duyệt hay không; trang vẽ phụ đề lên canvas thì không đọc được bằng CC, khi đó đổi sang OCR và kéo khung vào chỗ phụ đề. Video có DRM có thể chụp ra màn đen, OCR sẽ không đọc được.
- **Ảnh bìa** chỉ có cho game có trên Steam (khớp theo tên tiếng Anh); game chỉ có trên PlayStation, Xbox, Switch dùng ảnh vẽ sẵn. Ảnh không đóng gói trong app, thuộc về nhà phát hành.
- Không có phần trả phí, không có mạng xã hội.
- **Bản vá Việt hoá (mod) của cộng đồng** sửa chữ trong file game, mỗi nhóm giữ quyền riêng: app chỉ dẫn tới trang gốc, không tải hộ, không cài vào game. Gói Việt hoá trong app là dữ liệu cho phụ đề trên màn hình, soạn bởi người làm app, không chép từ các trang đó.
