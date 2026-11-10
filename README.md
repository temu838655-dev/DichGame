# Dịch game

Dịch phụ đề game và phim sang tiếng Việt ngay trên màn hình, có thể đọc to, có thể đẩy phụ đề lên TV.

- `app/` – app điện thoại Android (`com.dichgame.app`): chụp màn hình, đọc chữ (OCR), dịch, hiện bản dịch nổi.
- `tv/` – app nhận phụ đề cho Android TV / Google TV (`com.dichgame.tv`), vẽ nổi trên hình HDMI.
- `keystore/` – khóa ký APK mặc định (xem mục "Khóa ký").
- `.github/workflows/build-apk.yml` – tự build toàn bộ trên GitHub.

## Tính năng (app điện thoại)

| Màn hình | Có gì |
| --- | --- |
| Chính | Chuyển **Dịch / Thuyết minh**, chọn ngôn ngữ game (Anh, Nhật, Hàn, Trung), nút Bắt đầu, hộp **"Bạn chơi ở đâu?"** (TV / điện thoại) |
| Cách dịch | **Dịch trên máy** (miễn phí, offline), **CloudAPI** (AI theo chuẩn OpenAI, nhập địa chỉ + khóa + model), **Google Gemini** (khóa + model), nút **Lưu và kiểm tra**, số câu trước cho AI hiểu mạch truyện |
| Cài đặt › Giọng đọc | **Giọng của máy** hoặc **giọng AI Gemini** (8 giọng, tự lùi về giọng máy khi lỗi), tốc độ 0,5x–3x, **đọc theo cảm xúc**, đọc qua loa TV, nghe thử |
| Cài đặt › Phụ đề | Hiện câu gốc, cỡ chữ 50–200 %, kiểu chữ, mục **Nâng cao** (tần suất quét, chữ hiện dần, dịch menu tự động) |
| Cài đặt › Hồ sơ game | Nhiều hồ sơ, mỗi hồ sơ nhớ tên game + vùng quét riêng, **phong cách dịch** (Tự nhiên, Thuyết minh, Ngắn gọn, Sát nghĩa) |
| Phụ đề trên TV | Chọn hãng TV, hướng dẫn 4 bước, tìm TV trong mạng, ghép bằng mã 8 số, gửi câu thử |
| Dịch video trên web | Trình duyệt trong app, đọc phụ đề của trình phát (thẻ track, YouTube, video.js, JW Player, Plyr...) rồi hiện bản dịch, có thể đọc to |
| Hướng dẫn / Tính năng | Hướng dẫn 5 bước, danh sách tính năng |

Trong lúc chạy, thông báo có 3 nút: **Chỉnh/Khóa khung**, **Dịch menu**, **Vùng dịch**.

## Build trên GitHub

1. Tạo repo mới trên GitHub, đẩy **toàn bộ** thư mục này lên (nhớ cả thư mục ẩn `.github` và thư mục `keystore`).
   Dùng dòng lệnh: `git init && git add -A && git commit -m "Dịch game" && git branch -M main && git remote add origin <địa chỉ repo> && git push -u origin main`
2. Vào tab **Actions** › workflow **Build APK**. Nó chạy tự động mỗi lần đẩy code, hoặc bấm **Run workflow**.
3. Khi xong có 4 file APK:
   - mục **Artifacts** của lần chạy (`DichGame-APKs`), và
   - mục **Releases** của repo (khi đẩy lên nhánh `main`/`master` hoặc gắn tag `v*`), tải thẳng được bằng điện thoại.

| File | Dùng cho |
| --- | --- |
| `DichGame-phone-release.apk` | Cài lên điện thoại |
| `DichGame-TV-release.apk` | Cài lên Android TV |
| `*-debug.apk` | Bản gỡ lỗi, cùng khóa ký nên cài đè lên nhau được |

Phát hành bản có số phiên bản: `git tag v2.0 && git push origin v2.0`.

### Khóa ký

Mặc định APK ký bằng khóa trong `keystore/dichgame.jks` (mật khẩu `dichgame123`). Khóa này nằm công khai trong repo, chỉ hợp để dùng cá nhân.
Muốn dùng khóa riêng: tạo 4 secrets trên GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), workflow sẽ tự dùng.
Lưu ý: đổi khóa thì phải gỡ app cũ rồi cài lại.

## Ghép TV

1. Cài `DichGame-TV-release.apk` lên TV (app **Downloader** trên TV, nhập đường dẫn tải file trong Releases; hoặc USB).
2. Mở **Dịch game TV**, cấp quyền "Hiển thị trên ứng dụng khác".
   Nếu TV không có menu này: `adb shell appops set com.dichgame.tv SYSTEM_ALERT_WINDOW allow`.
3. TV hiện địa chỉ IP và mã 8 số (đổi mỗi 60 giây).
4. Trên điện thoại: **Phụ đề trên TV** › **Tìm TV trong mạng** (hoặc nhập IP) › nhập mã › **Ghép TV** › **Gửi câu thử lên TV**.
5. Điện thoại và TV phải cùng Wi-Fi. Cổng 8765 (TCP) và 8766 (UDP, để tìm TV).
6. Khi bấm Bắt đầu chọn **Chơi trên TV** thì phụ đề (và giọng đọc, nếu bật "Đọc qua loa TV") được đẩy lên TV.

## Giới hạn hiện tại

- Chưa có app cho **TV LG (webOS)** và **TV Samsung (Tizen)**; chỉ Android TV / Google TV.
- Chưa có **Chơi trên điện thoại bằng Remote Play** (điều khiển máy chơi game ngay trong app). "Chơi trên điện thoại" hiện nghĩa là hình game đã hiện sẵn trên điện thoại (ứng dụng Remote Play, cloud gaming, thẻ capture...).
- Giao diện chỉ có tiếng Việt.
- Giọng của máy là các giọng tiếng Việt cài sẵn (Cài đặt › Ngôn ngữ và nhập › Chuyển văn bản thành giọng nói). Giọng AI dùng chung khóa Google Gemini với bộ dịch, tên model mặc định `gemini-3.8-flash-tts` (đổi được trong app nếu Google đổi tên).
- "Đọc theo cảm xúc" đoán cảm xúc bằng từ khóa và dấu câu, chưa dùng AI.
- "Dịch video trên web" phụ thuộc trang web có để lộ phụ đề cho trình duyệt hay không; trang dùng DRM hoặc vẽ phụ đề lên canvas thì không đọc được, khi đó dùng chế độ chụp màn hình thường.
- Không có phần trả phí, không có mạng xã hội.
