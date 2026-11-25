# BUG_TRACKER.md

Cập nhật: 2026-11-26 (UTC+8)
Phạm vi: audit repository Dịch Game by Huyy Đâyy

| ID | Mức độ | Vấn đề / bằng chứng | Nguyên nhân hoặc ảnh hưởng | Trạng thái |
|---|---|---|---|---|
| SEC-001 | CRITICAL | `keystore/dichgame.jks` đã được commit trong repository public; Gradle còn fallback mật khẩu/alias mặc định. | Khóa cũ đã lộ; không thể thu hồi chứng thư đối với cài đặt sideload hiện có. | BLOCKED — cần chủ sở hữu quyết định giữ khóa trong GitHub Secrets để tương thích (chấp nhận rủi ro) hay chuyển khóa kèm migration package/dữ liệu. Workflow release mới gate bằng 4 secrets. |
| SEC-002 | HIGH | Phone manifest vẫn đặt `android:usesCleartextTraffic="true"` toàn app. PR #85 thêm chặn CloudAPI bearer credential qua HTTP và chỉ gửi PCM tới host TV private/local. | HTTP cleartext còn cần cho receiver trong LAN; chưa thể tắt global nếu chưa tách transport/đánh giá đầy đủ endpoint và backward compatibility. | OPEN — mitigation một phần đang ở PR #85; cần review/merge sau khi CI hoàn tất và thiết kế giới hạn cleartext sâu hơn. |
| QA-001 | HIGH | Smoke run 45703102492 attempt 2 kết thúc cancelled ngay ở bước “Build bản debug”; emulator chưa chạy. | CI smoke chưa có kết quả chức năng. Đã gửi rerun job mới nhất 139527252026; chờ attempt mới. | OPEN |
| QA-002 | HIGH | Chưa có bằng chứng kiểm thử Android arm64, Android TV và PS4/PS5/Xbox thật. | CI không thay thế kiểm thử phần cứng/Remote Play thực tế. | BLOCKED — cần thiết bị, console, firmware, mạng và tài khoản thực tế. |
| REL-001 | MEDIUM | Main build run 45707264385 (#1897) đang chạy; signing-gate step đã success nhưng các bước unit test/build/verify/release chưa hoàn tất. | Chưa xác minh kết luận Release bị skip khi secrets thiếu. | OPEN — kiểm tra lại run sau khi kết thúc. |
| SEC-003 | HIGH | PR #85 thay TV token/LG key/browser receiver tokens sang AES-GCM Android Keystore; thêm HTTPS validation cho CloudAPI và private-host validation cho TV PCM. | Giảm rò rỉ token và bearer credential; cần kiểm tra migration, clear/remove và tích hợp trên thiết bị. | FIXED (branch only) — PR #85 còn draft/open; JVM unit tests success, build app đang chạy. |
| DOC-001 | LOW | Tài liệu theo dõi audit đã được tạo và cập nhật. | Trạng thái được lưu trong repository. | VERIFIED |

## Quy tắc trạng thái
- OPEN: cần điều tra hoặc sửa.
- FIXED: đã thay đổi nhưng chưa có kiểm thử đủ.
- VERIFIED: có bằng chứng kiểm thử phù hợp.
- BLOCKED: cần quyết định, quyền truy cập hoặc thiết bị bên ngoài.

Không đánh dấu VERIFIED chỉ dựa trên việc source code biên dịch.
