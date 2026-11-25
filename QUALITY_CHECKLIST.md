# QUALITY_CHECKLIST.md

Cập nhật: 2026-11-26 (UTC+8)

| Hạng mục | Trạng thái | Bằng chứng / giới hạn |
|---|---|---|
| Repository / nhánh / quyền truy cập | VERIFIED | Repo public, nhánh main, quyền push/admin. |
| Build Android phone + TV | VERIFIED (baseline) | Run 45703102330 thành công trước đợt audit. Run main #1897 hiện đang chạy, chưa có kết quả cuối. |
| APK signature / zipalign / package IDs | VERIFIED (baseline) | Các bước CI run 45703102330 thành công; package com.dichgame.app và com.dichgame.tv, chứng thư ký chung. |
| Public signing key safety | NOT PASS | Keystore đã lộ trong repo public; workflow chặn public Release thiếu secrets nhưng không thu hồi khóa cũ. |
| Signing gate sau thay đổi | PARTIAL | Main run 45707264385 bước nạp khóa success; phải đợi bước cuối để xác minh Release thực sự skipped. |
| Emulator smoke test | NOT PASS | Smoke attempt 2 bị cancelled tại bước build debug; chưa chạy emulator. Rerun mới đã yêu cầu, chờ kết quả. |
| AI translation / Gemini runtime | NOT VERIFIED | Có JVM unit tests và logic retry; chưa xác minh API thật với key hợp lệ trong app. |
| CloudAPI transport | FIXED (PR #85 pending) | CloudAPI chỉ chấp nhận HTTPS hợp lệ, từ chối userinfo/HTTP; JVM tests trong run 45706828580 success. |
| OCR runtime / đa ngôn ngữ | NOT VERIFIED | Chưa có bằng chứng thiết bị hoặc test tích hợp đầy đủ. |
| Audio recognition / PCM lifecycle | PARTIAL | Teardown có unit tests trước đó; giới hạn PCM tới TV private/local host có test trong PR #85. Chưa test thiết bị. |
| TTS / voice synchronization | NOT VERIFIED | Chưa chạy test runtime trên thiết bị. |
| TV credentials / browser receiver tokens | FIXED (PR #85 pending) | Mã hóa AES-GCM Android Keystore và migrate legacy; cần verify trên thiết bị và sau restore. |
| LAN / TV connection / cleartext scope | NOT PASS | Manifest vẫn bật cleartext toàn app để tương thích LAN; chưa hoàn tất transport isolation. |
| Remote Play PS4/PS5 / Xbox | NOT VERIFIED | Cần thiết bị/firmware/tài khoản/mạng thực tế. |
| UI navigation / screen sizes / accessibility | NOT VERIFIED | Smoke chưa chạy; chưa kiểm tra thủ công mọi màn hình. |
| Data persistence / backup / restore | PARTIAL | Backup loại trừ file credentials; migration/restore thực tế chưa test. |
| Release updater compatibility | PARTIAL | Workflow có kiểm tra certificate/versionCode; chưa verify cài đè từ bản cũ trên thiết bị. |
| Performance / battery / memory soak | NOT VERIFIED | Chưa có profile hoặc soak test. |
| Security review (secrets, network, backup, permissions) | IN PROGRESS | SEC-001 còn blocked; SEC-002 còn open; PR #85 thêm một số mitigation. |
| Documentation / autonomous tracking | VERIFIED | AUTONOMOUS_PROGRESS.md, BUG_TRACKER.md, QUALITY_CHECKLIST.md, COMPETITIVE_GAP_ANALYSIS.md. |

## Điều kiện kết luận
Chỉ chuyển sang VERIFIED khi có test/log/bằng chứng cụ thể. NOT VERIFIED và BLOCKED không được diễn giải thành đạt.
