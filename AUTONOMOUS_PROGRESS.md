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


## Tiếp tục audit — 2026-11-26 05:40 UTC+8

### Đọc lại trạng thái repository mới nhất
- Main hiện ở commit 084659b171de80fba857f8d05284dc3d0508095b (không còn ở commit c72454d như lần audit trước).
- Có PR #85 `security/credential-hardening` (draft/open, 10 commits, 9 files): mã hóa TV token/LG key/browser receiver token bằng AES-GCM Android Keystore; yêu cầu HTTPS cho CloudAPI; giới hạn gửi PCM tới TV private/local; phân loại billing errors; bổ sung AiSecurityTest và TvLinkSecurityTest.
- PR #85 JVM unit-test check 139530840625 đã success. Build PR run 45706828589 vẫn in_progress ở build phone sau khi unit test và TV receiver build đã success.
- Main build run 45707264385 (#1897) đang in_progress; bước kiểm tra/nạp signing key đã success; unit tests đang chạy. Chưa xác nhận release skip.
- Smoke run 45703102492 attempt 2 bị cancelled tại bước Build bản debug; emulator chưa chạy. Đã gửi rerun job ID 139527252026, chờ attempt tiếp theo.

### Kết quả audit bảo mật bổ sung
- Các HTTP endpoint nhìn thấy trong module app: CloudAPI (đã được PR #85 buộc HTTPS), PackStore (chỉ nhận HTTPS), TV PCM endpoint `http://<TV>:8765/audio` (PR #85 chỉ cho phép IP private/local), WebSubServer/receiver và LG WebSocket có giao thức riêng.
- Vì receiver TV trong LAN cần HTTP cleartext và địa chỉ có thể là IP động, chưa thể tắt `usesCleartextTraffic` toàn app mà không thay đổi kiến trúc hoặc làm hỏng pairing. SEC-002 giữ OPEN; không đánh dấu xong chỉ vì CloudAPI đã được harden.
- MiniWs có custom TLS trust manager; cần review cách pin/verify thiết bị TV thay vì tin cậy tất cả certificate. Chưa sửa vì có thể phá pairing TV tự ký nếu không thiết kế pinning tương thích.
- PR #85 còn draft/open, chưa merge. Không merge trước khi build phone và security migration được kiểm tra đủ.

### Hạng mục còn lại
- [ ] Chờ và xác minh build PR #85; review kỹ TLS trust-all, migration credentials, local-host validation, receiver token storage.
- [ ] Xác minh main build #1897: unit tests, phone/TV builds, APK checks và Release skip khi thiếu secrets.
- [ ] Hoàn tất smoke emulator; attempt 2 bị cancel trước khi chạy app.
- [ ] SEC-001: chủ sở hữu phải chọn chiến lược khóa ký đã lộ. Không thể tự thay khóa mà không làm mất khả năng cập nhật đè app đã cài.
- [ ] SEC-002: thiết kế network security policy an toàn mà vẫn hỗ trợ LAN HTTP; xem xét custom socket/local host routing.
- [ ] Review MiniWs TLS trust manager; thiết kế trust-on-first-use/pinning hoặc xác minh cert theo thiết bị nếu tương thích.
- [ ] Test AI/Gemini runtime, OCR, ASR, TTS, UI, persistence/backup, updater, performance.
- [ ] Kiểm thử thiết bị Android arm64, Android TV, LG/Samsung và console thực.

### Link run
- Main build #1897: https://github.com/temu838655-dev/DichGame/actions/runs/45707264385
- PR #85: https://github.com/temu838655-dev/DichGame/pull/85
- PR #85 build: https://github.com/temu838655-dev/DichGame/actions/runs/45706828589
- PR #85 JVM unit tests: https://github.com/temu838655-dev/DichGame/actions/runs/45706828580
- Smoke test attempt 2 (cancelled before emulator): https://github.com/temu838655-dev/DichGame/actions/runs/45703102492


## Audit tiếp tục — 2026-11-26 06:00 UTC+8

### Bằng chứng CI mới trên main
- [x] Main run #1913 (ID 45708105647) hoàn tất success.
- [x] Build phone release và Android TV release thành công; unit tests, build TV receiver, build phone, đóng gói APK, kiểm tra chữ ký/zipalign/package IDs đều success.
- [x] Artifact `DichGame-APKs` đã upload (ID 15066127993, SHA-256 `a15db18ad5e150f9d11dd7de4c51a18f14ab46378dc9d0bd5cfbadb4801cf722`).
- [x] Public Release đúng cách bị skip: log xác nhận `IS_PUBLIC_RELEASE=true`, `CAN_PUBLISH_RELEASE=false`; signing secrets chưa được cấu hình đầy đủ. Không tạo release chính thức từ khóa test.
- [ ] Smoke test run #161 (ID 45708105649) vẫn pending, chưa có job/runner; attempt cũ #159 bị cancelled ở bước build debug. Đã đổi concurrency `cancel-in-progress: false` để không tự hủy smoke run khi có commit mới.

### PR #85 — security/credential-hardening
- [x] Bổ sung HTTPS validation CloudAPI, encrypted API/TV/receiver secrets, local TV host check, backup exclusions, migration fallback, DNS-rebinding mitigation, OCR bounding box validation.
- [x] CI run #1926 phát hiện test import thiếu `assertEquals`/`assertNotNull`; đã sửa bằng commit `233d3cd98a663197b3c0a0f7156dc4b7232d8ecd`.
- [ ] CI run #1927 (ID 45709164698) đang chạy; bước Android unit tests chưa kết thúc. Chưa xác minh compile/test pass cho toàn bộ PR.
- [ ] Cần thêm SEC-009: luôn disconnect HTTP connection khi gửi PCM lỗi; đã sửa bằng try/finally.
- [ ] Cần thêm SEC-010: validate/clamp bounding box OCR Gemini để tránh tọa độ sai/NaN; đã thêm helper và regression tests.
- [ ] SEC-006 trust-all TLS của LG TV vẫn là rủi ro mở, cần TOFU/pinning có UX xác minh thiết bị.
- [ ] SEC-001 keystore cũ public vẫn cần quyết định của chủ sở hữu; không tự thay certificate gây mất khả năng cập nhật app đã cài.

### Link kiểm thử
- Main build #1913: https://github.com/temu838655-dev/DichGame/actions/runs/45708105647
- Smoke #161 (pending): https://github.com/temu838655-dev/DichGame/actions/runs/45708105649
- PR #85 build #1927: https://github.com/temu838655-dev/DichGame/actions/runs/45709164698
- PR #85: https://github.com/temu838655-dev/DichGame/pull/85
