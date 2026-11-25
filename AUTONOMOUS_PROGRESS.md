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
- [x] Xác minh repository là public và keystore/dichgame.jks (2,636 bytes) đang được track trong Git; build-1872 xác nhận 4 signing secrets trống và dùng keystore trong repo. Đánh giá khóa ký đã lộ.
- [x] Sửa workflow: thiếu signing secrets thì vẫn chạy test/build và lưu artifact kiểm thử, nhưng bỏ qua bước tạo Release công khai; thiếu một phần secrets thì fail ngay.
- [x] Cập nhật README cảnh báo compromise, phân biệt giữ khóa cũ (tương thích nhưng không an toàn) và đổi khóa mới (không cập nhật đè được; cần migration dữ liệu).
- [ ] Audit toàn bộ module AI/OCR/ASR/TTS/UI và kiểm tra các đường đi lỗi/tích hợp còn thiếu.
- [x] Sửa teardown ASR trong CaptureService: trước đây đợi worker tối đa 3 giây rồi đóng PCM queue dù nhận dạng có thể còn chạy; nay chờ worker dừng và giải phóng model xong mới đóng queue (dùng stopAndJoinUntilStopped đã có test).
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
- RỦI RO NGHIÊM TRỌNG: keystore release đã nằm trong repository public và đã ký build-1872. Không thể thu hồi chứng thư đã lộ đối với app sideload hiện có. Workflow mới chặn phát hành thiếu secrets nhưng không thể tự giải quyết việc đổi khóa mà vẫn cập nhật đè. Cần chủ sở hữu quyết định giữ khóa cũ trong Secrets để duy trì tương thích (chấp nhận rủi ro), hay lập kế hoạch chuyển đổi package/cài lại và sao lưu dữ liệu sang khóa mới.
- app/build.gradle.kts có fallback thông tin ký mặc định trong cấu hình Gradle; README ghi rõ khóa mặc định là public/test và cần secrets riêng cho phát hành. Cần xác minh workflow từ chối public release khi thiếu secrets, không ghi secrets vào log và không vô tình dùng khóa test.
- CI dùng một số action phiên bản @v4; log đã cảnh báo setup-java@v4 deprecated. Cần lên kế hoạch nâng cấp có kiểm chứng.
- README ghi phạm vi chơi trực tiếp hiện tại là PS5/PS4/Xbox; PC chỉ là nguồn dữ liệu Việt hóa, Switch chưa được hỗ trợ. Không quảng bá vượt quá phạm vi này.
- OCR cloud cho một số bộ chữ cần Gemini; OCR offline hiện dựa ML Kit cho các bộ chữ đã hỗ trợ, không được mô tả là offline cho mọi ngôn ngữ.
- Audio capture/ASR phụ thuộc quyền và chính sách capture của Android/ứng dụng nguồn; Remote Play/console thật cần kiểm thử thực địa.

## Tiếp tục từ đây

1. Đọc file này và kiểm tra trạng thái workflow 45703102330, 45703102492 cùng Release build-1872.
2. Kiểm tra workflow build #1878 và unit test #163 sau thay đổi ASR teardown; bảo đảm test/build pass và release được skip vì secrets chưa cấu hình.
3. Kiểm tra CI sau commit workflow/README: build kiểm thử có chạy được không; push main không được phát hành nếu secrets thiếu. Xác nhận run cũ #1873 không tạo thêm release sau khi policy mới vào main.
4. Hoàn tất smoke test; nếu fail, lấy log, sửa nguyên nhân, chạy lại.
5. Audit sâu các điểm bảo mật nêu trên, xem README, AndroidManifest, Prefs/ApiSecrets/KeyRing, network clients, CaptureService/RemotePlay.
6. Thêm test cho các lỗi có thể tái hiện; chạy unit tests và build phone/TV.
7. Cập nhật file này sau mỗi giai đoạn có bằng chứng.
8. Chỉ kết luận production-ready khi có kiểm thử thiết bị thật; nếu không có thiết bị thì ghi rõ giới hạn.

## Link xác minh

- PR #84: https://github.com/temu838655-dev/DichGame/pull/84
- Commit main: https://github.com/temu838655-dev/DichGame/commit/3fa577b18c14862c8ad4677736a84edc5b36ddd5
- Build workflow #1872: https://github.com/temu838655-dev/DichGame/actions/runs/45703102330
- Smoke test workflow #159: https://github.com/temu838655-dev/DichGame/actions/runs/45703102492
- Release build-1872: https://github.com/temu838655-dev/DichGame/releases/tag/build-1872


## Audit tiếp tục — 2026-11-26 05:34 UTC+8

### Xác minh bổ sung
- [x] Repository main truy cập được; tài khoản hiện tại có quyền admin/push.
- [x] Đọc README, AndroidManifest, app/build.gradle.kts và toàn bộ phần chính của build-apk.yml.
- [x] Run 45703102330: job build completed/success; unit tests, TV build, phone build, APK packaging, signature/zipalign/package checks và Release step đều success. Lưu ý đây là run trước khi hoàn tất audit policy khóa ký; không dùng nó làm bằng chứng policy mới.
- [x] Run 45703102492: emulator smoke job completed/cancelled; bước “Chạy trên máy giả lập” bị cancelled. Đây KHÔNG phải pass.
- [x] Workflow hiện tại có điều kiện `CAN_PUBLISH_RELEASE == 'true'` cho ghi chú và tạo Release; thiếu signing secrets trên public push sẽ đặt false trong bước nạp khóa.
- [x] Tạo BUG_TRACKER.md và QUALITY_CHECKLIST.md trong hai commit audit riêng.

### Vấn đề còn mở
- [ ] SEC-001: keystore công khai đã lộ. Workflow chặn phát hành thiếu secrets, nhưng chưa có quyết định chuyển khóa an toàn. Không tự ý đổi chứng thư vì sẽ chặn cập nhật đè của người dùng hiện tại.
- [ ] SEC-002: app manifest bật cleartext traffic toàn ứng dụng. Phải audit endpoint HTTP LAN/TV trước khi thu hẹp policy.
- [ ] QA-001: chạy lại và hoàn tất emulator smoke test; run cũ bị cancelled.
- [ ] REL-001: xác minh workflow sau commit release-gate thực sự bỏ qua public Release khi secrets thiếu.
- [ ] QA-002: kiểm thử trên Android arm64, Android TV và console thật vẫn cần thiết bị thực.
- [ ] Audit sâu Gemini/key storage, OCR, ASR, TTS, Remote Play, backup/restore, permissions, lifecycle, UI, hiệu năng và network endpoints.

### Bằng chứng và giới hạn
- Build baseline: https://github.com/temu838655-dev/DichGame/actions/runs/45703102330
- Emulator smoke (cancelled): https://github.com/temu838655-dev/DichGame/actions/runs/45703102492
- Commits mới nhất trước audit: 07da9260cfa4e403c9a0fb788d90958e91e568d8.
- Chưa tuyên bố app production-ready; chưa có kiểm thử thiết bị thật.


### Đối chiếu sản phẩm tham chiếu
- [x] Tạo COMPETITIVE_GAP_ANALYSIS.md dựa trên trang chủ, hướng dẫn Android, trang desktop và kho game công khai.
- [x] Xác định các GAP có bằng chứng: đối thủ công bố iOS/Windows/macOS và PC/Steam native; repository hiện tại là Android phone/TV và README chưa công bố PC/Steam native. LG/Samsung trong repo chưa được xác minh tương đương TV support của đối thủ.
- [ ] Không triển khai mở rộng nền tảng trong đợt này trước khi ổn định Android core.

### Smoke test
- [x] Đã gửi yêu cầu rerun job smoke bị cancelled (GitHub trả success cho thao tác rerun).
- [ ] Công cụ hiện vẫn trả kết quả step cũ với “Chạy trên máy giả lập: cancelled”; chưa có attempt mới được xác nhận. Giữ QA-001 ở OPEN, không đánh dấu pass.


## Bổ sung hardening sau review PR #85 — 2026-11-26

### Phát hiện và sửa mới trên branch security/credential-hardening
- [x] SEC-004: backup rules trước đó bỏ sót legacy `dg.xml`, nơi credential plaintext có thể còn tồn tại trước lần migration đầu. Đã loại trừ file này khỏi full backup, cloud backup và device transfer. Trade-off: các cài đặt trong dg.xml không tự khôi phục từ Android backup; dữ liệu trên máy hiện tại không bị xóa.
- [x] Thêm `ci/test_backup_rules_security.py` kiểm tra các file SharedPreferences nhạy cảm bị loại khỏi cả cloud/device backup.
- [x] Thêm CI step chạy regression test backup rules.
- [x] Cập nhật README giải thích rõ tác động sao lưu.
- [x] SEC-005: khi ciphertext không đọc được do Keystore reset nhưng legacy plaintext còn, ApiSecrets.read giờ thử mã hóa lại bằng khóa Keystore hiện tại; chỉ cleanup plaintext sau khi commit encrypted prefs thành công.
- [ ] CI cho các thay đổi trên chưa có kết quả cuối. Các push liên tiếp đã làm một số workflow attempts cũ bị cancelled; đợi run mới nhất theo commit cuối.

### Rủi ro vẫn mở
- [ ] SEC-006: MiniWs chấp nhận self-signed TLS certificate bằng trust-all. Có giới hạn kết nối private/local, nhưng chưa xác thực danh tính TV; cần thiết kế trust-on-first-use/pinning có UX xác nhận fingerprint và tương thích TV LG.
- [ ] SEC-001: khóa ký cũ đã public; quyết định chuyển khóa vẫn thuộc chủ sở hữu.
- [ ] SEC-002: usesCleartextTraffic vẫn bật toàn app vì HTTP receiver LAN; CloudAPI và gửi PCM đã có kiểm soát trong PR nhưng cần cô lập transport sâu hơn.
- [ ] QA-001: smoke run attempt 3 đang build debug, emulator chưa chạy.


### Review sâu migration browser receiver token
- [x] Phát hiện TvOut trước đây xóa legacy token trước khi parse JSON ciphertext thành công; JSON hỏng có thể làm mất pairing token còn có thể phục hồi.
- [x] Sửa thứ tự: parse encrypted JSON trước; chỉ xóa legacy sau khi parse thành công. Khi payload hỏng, giữ legacy và thử mã hóa lại trước khi cleanup.
- [x] Thêm WebReceiverTokenStorageTest: valid token filtering, malformed JSON fallback, valid empty payload remains authoritative after revokeAll.
- [ ] CI cho commit cuối PR chưa hoàn tất; run #1915 đang queued lúc cập nhật.
- [ ] MiniWs trust-all TLS vẫn là rủi ro mở cần thiết kế pinning.


### Review bảo vệ địa chỉ TV — chống DNS rebinding
- [x] Phát hiện TvLink kiểm tra host resolve tới mạng riêng nhưng sau đó mở URL bằng hostname gốc, khiến HTTP client có thể resolve lần hai.
- [x] Sửa TvLink resolve một lần, chọn IP private/local và tạo URL bằng địa chỉ số đã xác thực (IPv6 scope được encode).
- [x] Bổ sung regression test xác nhận resolver trả đúng numeric IP dùng để kết nối.
- [ ] CI cho commit mới chưa có kết quả; run mới nhất sẽ chạy sau các push của PR branch.
