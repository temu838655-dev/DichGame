# AUTONOMOUS_PROGRESS.md

Cập nhật: 2026-11-26 (UTC+8)
Repository: https://github.com/temu838655-dev/DichGame
Nhánh: main
Commit nền đã xác minh: 3fa577b18c14862c8ad4677736a84edc5b36ddd5

## Trạng thái đã xác minh

- [x] PR #84 đã merge vào main (2026-11-25T21:18:15Z).
- [x] Các thay đổi reliability AI trên commit nền: bảo vệ/luân phiên API key, phân loại lỗi quota/billing, retry có giới hạn, placeholder protection, request deduplication và ASR teardown.
- [x] Android JVM unit-test workflow #162: success.
- [x] Android release build workflow #1872: bước build điện thoại và TV hoàn tất.
- [x] APK packaging hoàn tất.
- [x] Xác minh APK: chữ ký APK, zipalign, package IDs (com.dichgame.app / com.dichgame.tv), versionCode và chứng thư ký chung đã hoàn tất thành công theo các bước CI.
- [x] Artifact DichGame-APKs được tạo (workflow run 45703102330; artifact ID 15064248181).
- [x] GitHub Release build-1872 đã được tạo với DichGame.apk và DichGame-TV.apk.
- [ ] Workflow smoke test giả lập Android (run 45703102492) chưa kết thúc tại thời điểm cập nhật; bước chạy trên emulator đang in_progress.
- [ ] Hoàn tất audit bảo mật toàn bộ luồng mạng, lưu trữ bí mật, backup, permissions và service lifecycle.
- [ ] Audit toàn bộ module AI/OCR/ASR/TTS/UI và kiểm tra các đường đi lỗi/tích hợp còn thiếu.
- [ ] Kiểm thử thực tế trên điện thoại Android arm64, Android TV và PS4/PS5/Xbox thật. Chưa có bằng chứng phần cứng trong CI.
- [ ] Xác minh đầy đủ hành vi phát hành/cập nhật từ bản cài đặt cũ trên thiết bị người dùng.
- [ ] Hoàn thiện tài liệu nền tảng và hướng dẫn khắc phục theo kết quả audit.

## File đã thay đổi ở giai đoạn trước

- app/src/main/java/com/dichgame/app/Ocr.kt: phân loại lỗi OCR AI theo provider-neutral, tách hàm thông báo lỗi để kiểm thử.
- app/src/test/java/com/dichgame/app/OcrAiErrorTextTest.kt: regression tests cho billing, quota/rate limit, auth/model và network/server errors.
- app/src/main/java/com/dichgame/app/AiRetry.kt: sửa thông báo hết lượt tương thích test và giải thích đúng tình trạng.
- Các thay đổi reliability khác được hợp nhất trong PR #84.

## Phát hiện cần xử lý trong vòng audit kế tiếp

- AndroidManifest.xml hiện bật `android:usesCleartextTraffic="true"`; cần rà soát mọi endpoint HTTP cục bộ/TV và thay bằng Network Security Config có phạm vi host rõ ràng nếu khả thi, tránh tắt mù quáng làm hỏng kết nối LAN.
- app/build.gradle.kts có fallback thông tin ký mặc định trong cấu hình Gradle; README ghi rõ khóa mặc định là public/test và cần secrets riêng cho phát hành. Cần xác minh workflow từ chối public release khi thiếu secrets, không ghi secrets vào log và không vô tình dùng khóa test.
- CI dùng một số action phiên bản @v4; log đã cảnh báo setup-java@v4 deprecated. Cần lên kế hoạch nâng cấp có kiểm chứng.
- README ghi phạm vi chơi trực tiếp hiện tại là PS5/PS4/Xbox; PC chỉ là nguồn dữ liệu Việt hóa, Switch chưa được hỗ trợ. Không quảng bá vượt quá phạm vi này.
- OCR cloud cho một số bộ chữ cần Gemini; OCR offline hiện dựa ML Kit cho các bộ chữ đã hỗ trợ, không được mô tả là offline cho mọi ngôn ngữ.
- Audio capture/ASR phụ thuộc quyền và chính sách capture của Android/ứng dụng nguồn; Remote Play/console thật cần kiểm thử thực địa.

## Tiếp tục từ đây

1. Đọc file này và kiểm tra trạng thái workflow 45703102330, 45703102492 cùng Release build-1872.
2. Hoàn tất smoke test; nếu fail, lấy log, sửa nguyên nhân, chạy lại.
3. Audit sâu các điểm bảo mật nêu trên, xem README, AndroidManifest, Prefs/ApiSecrets/KeyRing, network clients, CaptureService/RemotePlay.
4. Thêm test cho các lỗi có thể tái hiện; chạy unit tests và build phone/TV.
5. Cập nhật file này sau mỗi giai đoạn có bằng chứng.
6. Chỉ kết luận production-ready khi có kiểm thử thiết bị thật; nếu không có thiết bị thì ghi rõ giới hạn.

## Link xác minh

- PR #84: https://github.com/temu838655-dev/DichGame/pull/84
- Commit main: https://github.com/temu838655-dev/DichGame/commit/3fa577b18c14862c8ad4677736a84edc5b36ddd5
- Build workflow #1872: https://github.com/temu838655-dev/DichGame/actions/runs/45703102330
- Smoke test workflow #159: https://github.com/temu838655-dev/DichGame/actions/runs/45703102492
- Release build-1872: https://github.com/temu838655-dev/DichGame/releases/tag/build-1872
