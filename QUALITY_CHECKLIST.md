# QUALITY_CHECKLIST.md

Cập nhật: 2026-11-26 (UTC+8)

| Hạng mục | Trạng thái | Bằng chứng / giới hạn |
|---|---|---|
| Repository / nhánh / quyền truy cập | VERIFIED | Repo public, nhánh main, quyền push/admin. |
| Build Android phone + TV | VERIFIED (baseline) | Workflow run 45703102330 hoàn tất build phone/TV, đóng gói và xác minh APK. Đây là baseline cũ, không phải build sau audit. |
| APK signature / zipalign / package IDs | VERIFIED (baseline) | Các bước CI run 45703102330 thành công; package com.dichgame.app và com.dichgame.tv, chứng thư ký chung. |
| Public signing key safety | NOT PASS | Keystore đã lộ trong repo public; public release mới được workflow gate bằng secrets, nhưng khóa cũ không thể thu hồi. |
| Signing gate sau thay đổi | NOT VERIFIED | Cần run sau commit policy để xác minh thiếu secrets thì bỏ qua Release. |
| Emulator smoke test | NOT PASS | Run 45703102492 bị cancelled tại bước chạy emulator. |
| AI translation / Gemini runtime | NOT VERIFIED | Chưa chạy kiểm thử runtime trong phiên audit này. |
| OCR runtime / đa ngôn ngữ | NOT VERIFIED | Chưa có bằng chứng thiết bị hoặc test tích hợp đầy đủ. |
| Audio recognition / PCM lifecycle | PARTIAL | Đã có unit-test coverage cho teardown theo progress; smoke trên thiết bị chưa xác minh. |
| TTS / voice synchronization | NOT VERIFIED | Chưa chạy test runtime trên thiết bị. |
| LAN / TV connection / cleartext scope | NOT PASS | Manifest phone bật cleartext toàn ứng dụng; cần audit endpoint và giới hạn host. |
| Remote Play PS4/PS5 / Xbox | NOT VERIFIED | Cần thiết bị/firmware/tài khoản/mạng thực tế. |
| UI navigation / screen sizes / accessibility | NOT VERIFIED | Chưa chạy UI test hoặc kiểm tra thủ công toàn màn hình. |
| Data persistence / backup / restore | NOT VERIFIED | Chưa kiểm thử nâng cấp/cài lại/khôi phục. |
| Release updater compatibility | PARTIAL | Workflow có kiểm tra chứng thư/versionCode; cần xác minh trên thiết bị từ bản cài cũ. |
| Performance / battery / memory soak | NOT VERIFIED | Chưa có profile hoặc soak test. |
| Security review (secrets, network, permissions, backup) | IN PROGRESS | Phát hiện SEC-001 và SEC-002; còn rà soát sâu. |
| Documentation / autonomous tracking | VERIFIED | AUTONOMOUS_PROGRESS.md, BUG_TRACKER.md, QUALITY_CHECKLIST.md. |

## Điều kiện kết luận
Chỉ chuyển sang VERIFIED khi có test/log/bằng chứng cụ thể. NOT VERIFIED và BLOCKED không được diễn giải thành đạt.
