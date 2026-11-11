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
| Kho game | Tìm kiếm, tab **Đã chơi / Tất cả**, nút **Game khác**. Chọn một game là tạo (hoặc chuyển sang) hồ sơ riêng tên game đó và ghi thể loại để AI dịch đúng giọng. Danh sách có hơn 100 game phổ biến (chỉ tên, không kèm ảnh bìa) |
| Cài đặt › Hồ sơ game | Nhiều hồ sơ, mỗi hồ sơ nhớ tên game + vùng quét riêng, **phong cách dịch** (Tự nhiên, Thuyết minh, Ngắn gọn, Sát nghĩa), **từ điển riêng** (ép AI dịch đúng tên nhân vật, chiêu thức) |
| Phụ đề trên TV | Chọn hãng TV, hướng dẫn 4 bước, tìm TV trong mạng, ghép bằng mã 8 số, gửi câu thử |
| Dịch video trên web | Trình duyệt trong app, đọc phụ đề của trình phát (thẻ track, YouTube, video.js, JW Player, Plyr...) rồi hiện bản dịch, có thể đọc to |
| Chơi trên điện thoại | Chọn **Chơi trên điện thoại** thì app liệt kê các app chơi từ xa đã cài (PS Remote Play, Chiaki, Moonlight, Steam Link, GeForce NOW, Xbox), mở sẵn app bạn chọn rồi hiện bản dịch đè lên hình game. Chưa cài thì có nút mở trang cài PS Remote Play |
| Cài đặt › Giới thiệu và hỗ trợ | Xem phiên bản, **Kiểm tra bản mới** (so với trang Releases), **Báo lỗi / yêu cầu game** (mở GitHub Issues điền sẵn thông tin máy) |
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
Mỗi bản dựng trên nhánh `main` có số thứ tự (`build-N`), số này được ghi vào app (versionCode = 20 + N) để app biết có bản mới hơn khi bấm **Kiểm tra bản mới**. Cài đè lên bản cũ được vì cùng khóa ký.

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

## Ngôn ngữ đọc được

| Cách đọc | Ngôn ngữ |
| --- | --- |
| Trên máy (nhanh, offline, miễn phí) | Anh, Nhật, Hàn, Trung, Pháp, Đức, Tây Ban Nha, Bồ Đào Nha, Ý, Indonesia, Hà Lan, Ba Lan, Thổ Nhĩ Kỳ, Hindi, Thụy Điển, Séc, Romania, Hungary, Đan Mạch, Phần Lan, Na Uy, Mã Lai |
| Nhờ Google Gemini đọc ảnh (cần khóa Gemini miễn phí từ aistudio.google.com) | Nga, Ukraina, Thái, Ả Rập, Hy Lạp, Do Thái |

Với nhóm Gemini, chỉ ảnh phần màn hình cần đọc (khung phụ đề, hoặc vùng bạn chọn) được gửi cho Google, và chỉ khi bạn chọn các thứ tiếng này. Bản dịch sang tiếng Việt vẫn có thể chạy trên máy.
Lần đầu dùng mỗi thứ tiếng, app tải mô hình dịch nên cần mạng.

## Giới hạn hiện tại

- Chưa có app cho **TV LG (webOS)** và **TV Samsung (Tizen)**; chỉ Android TV / Google TV.
- Chưa có **điều khiển PS4/PS5 thẳng trong app** (giao thức Remote Play). Dịch game chỉ mở app chơi từ xa bạn đã cài (nút ở mục "Chơi trên điện thoại") rồi hiện bản dịch đè lên; việc kết nối và điều khiển máy chơi game do app đó làm.
- Chưa có app cho **iOS, Windows, Mac**, và chưa có **kho game Việt hóa kèm bot gửi yêu cầu** (cần máy chủ). Thay vào đó có mục báo lỗi / yêu cầu game dẫn tới GitHub Issues.
- Giao diện chỉ có tiếng Việt.
- Chữ Nga, Ukraina, Thái, Ả Rập, Hy Lạp, Do Thái đọc chậm hơn (khoảng 1–3 giây mỗi câu) vì phải nhờ Gemini đọc ảnh; ở chế độ này tắt "Dịch menu tự động" (vẫn dùng được nút "Dịch menu" và "Vùng dịch").
- Giọng của máy là các giọng tiếng Việt cài sẵn (Cài đặt › Ngôn ngữ và nhập › Chuyển văn bản thành giọng nói). Giọng AI dùng chung khóa Google Gemini với bộ dịch, tên model mặc định `gemini-3.8-flash-tts` (đổi được trong app nếu Google đổi tên).
- **Giọng offline**: dùng thư viện sherpa-onnx (tự tải về lúc build, nằm ở `app/libs/`, không để trong git) và giọng Piper VAIS-1000 tải thêm trong app (khoảng 65 MB). Chưa có giọng nam, và mới có một giọng. Vì sherpa-onnx từ bản 2.0 không còn eSpeak, app tự đổi chữ sang phiên âm bằng bảng `app/src/main/assets/vi_phon.txt`, tạo bởi `tools/gen_vi_phon.py` (cần eSpeak NG ở máy dựng bảng, xem đầu file). Số và ký hiệu được đọc thành chữ; tên riêng, chữ nước ngoài được đọc theo kiểu tiếng Việt nên có thể nghe lạ. Smoke test trên máy giả lập có tự tải giọng và đọc thử một câu (SelfTest.kt) để chắc rằng thư viện chạy được; chất lượng nghe thật vẫn cần bạn nghe trên máy. Nghe không ổn thì dùng mục "Báo lỗi" trong app.
- "Đọc theo cảm xúc" đoán cảm xúc bằng từ khóa và dấu câu, chưa dùng AI.
- "Dịch video trên web" phụ thuộc trang web có để lộ phụ đề cho trình duyệt hay không; trang dùng DRM hoặc vẽ phụ đề lên canvas thì không đọc được, khi đó dùng chế độ chụp màn hình thường.
- Không có phần trả phí, không có mạng xã hội.
