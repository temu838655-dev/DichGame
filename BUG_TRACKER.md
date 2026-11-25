# BUG_TRACKER.md

Cập nhật: 2026-11-26 (UTC+8)
Phạm vi: audit repository Dịch Game by Huyy Đâyy

| ID | Mức độ | Vấn đề / bằng chứng | Nguyên nhân hoặc ảnh hưởng | Trạng thái |
|---|---|---|---|---|
| SEC-001 | CRITICAL | `keystore/dichgame.jks` đã được commit trong repository public; Gradle còn fallback mật khẩu/alias mặc định. README xác nhận khóa đã lộ. | Bất kỳ ai có bản sao khóa có thể ký APK giả cùng package/chứng thư. Không thể thu hồi chứng thư đối với cài đặt sideload hiện có. | BLOCKED — cần chủ sở hữu quyết định chiến lược chuyển khóa; workflow hiện đã chặn public Release khi thiếu đủ 4 signing secrets. |
| SEC-002 | HIGH | `app/src/main/AndroidManifest.xml` đặt `android:usesCleartextTraffic="true"` cho toàn ứng dụng. | Cho phép HTTP không mã hóa ở mọi host; tăng nguy cơ lộ/chỉnh sửa dữ liệu. Cần lập danh sách endpoint LAN/TV và giới hạn bằng Network Security Config, không tắt mù quáng. | OPEN |
| QA-001 | HIGH | Emulator smoke workflow run 45703102492 kết thúc `cancelled` tại bước “Chạy trên máy giả lập”. | Chưa có bằng chứng smoke test hoàn tất; không được coi là pass. | OPEN |
| QA-002 | HIGH | Chưa có bằng chứng kiểm thử trên Android arm64, Android TV và console thật. | CI không thay thế được kiểm thử phần cứng/Remote Play thực tế. | BLOCKED — cần thiết bị và phiên chơi thực tế. |
| REL-001 | MEDIUM | Run 45703102330 build/release #1872 hoàn tất success trước khi policy khóa ký mới được ghi nhận; đây không chứng minh policy mới đã chạy đúng. | Cần xác minh run sau commit policy; hiện chưa có run ID/kết quả xác nhận trong lần audit này. | OPEN |
| DOC-001 | LOW | Chưa có `QUALITY_CHECKLIST.md` và tracker tập trung trước audit này. | Khó theo dõi các tiêu chí và trạng thái đã xác minh. | FIXED — tạo tracker/checklist trong commit audit này. |

## Quy tắc trạng thái
- OPEN: cần điều tra hoặc sửa.
- FIXED: đã thay đổi nhưng chưa có kiểm thử đủ.
- VERIFIED: có bằng chứng kiểm thử phù hợp.
- BLOCKED: cần quyết định, quyền truy cập hoặc thiết bị bên ngoài.

Không đánh dấu VERIFIED chỉ dựa trên việc source code biên dịch.
