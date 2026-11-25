# QUALITY_CHECKLIST.md

> Bảng lịch sử phía trên được giữ lại. Bảng “Current audit status” bên dưới là trạng thái mới nhất và supersede các snapshot trước.

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


---

## Current audit status — 2026-11-26 06:16 UTC+8

| Hạng mục | Trạng thái mới nhất | Bằng chứng / giới hạn |
|---|---|---|
| Repository / main inventory | VERIFIED | Main commit a373a902; tree, branches, commits, workflows, releases đã kiểm tra. |
| Android phone/TV build baseline | VERIFIED (CI baseline) | Main #1913 success; latest PR build #1960 pending. |
| Android JVM bridge tests | VERIFIED (CI) | Run #172 success trên source có HTTPS redirect validation. |
| Knowledge Base Python tests | VERIFIED (CI) | #223–#226 success trên matcher updates. |
| Android APK build latest PR head | PENDING | #1960 queued; chưa được đánh dấu pass. |
| Emulator smoke | NOT VERIFIED | #161 vẫn ở bước emulator execution; chưa có conclusion. |
| Production signing key safety | NOT PASS / BLOCKED | Keystore tracked public; owner decision required. |
| Signing release gate | VERIFIED (gate behavior only) | Main #1913 skip public Release khi thiếu secrets. |
| Latest official release | VERIFIED (metadata) | build-1872 có phone APK + TV APK; không phải release mới. |
| Catalog game count | VERIFIED (source inventory) | 201 game-title rows; không phải 201 game có pack. |
| Built-in glossary | VERIFIED (source inventory) | 33 nhóm VietHoa.kt + 81 nhóm VietHoaPlus.kt. |
| Built-in phrase memory | VERIFIED (source inventory) | 58 generic rows + 18 specific rows trên 6 franchise groups. |
| Phrase packs cho toàn catalog | NOT VERIFIED | Chưa có bằng chứng 201 game đều có pack thoại; cần inventory từ nguồn cloud/user. |
| Matcher dialogue context | VERIFIED (Python CI) | previous/next sentence context; ambiguous exact fallback; tests #223–#226 success. |
| HTTPS redirect hardening | VERIFIED (Android JVM CI) | Manual redirect, max 3 hops, HTTPS-only, reject credentials/fragments; bridge tests #172 success. |
| Game library status labels | PARTIAL / PR #86 | Glossary-only state đổi thành “Thuật ngữ riêng”; chờ APK build #1960. |
| Knowledge Base hosted integration | NOT COMPLETE | Python/SQLite + CSV bridge có; hosted sync/auth/reviewer admin và end-to-end integration chưa có. |
| CloudAPI/credential hardening | PARTIAL / PR #85 | Build #1939 pass; PR dirty/conflicted; LG trust-all TLS remains open. |
| LAN cleartext policy | NOT PASS | usesCleartextTraffic=true toàn app; cần tách policy. |
| OCR/AI/ASR/TTS runtime | NOT VERIFIED | Chưa có test corpus end-to-end trên thiết bị. |
| TV/console hardware | NOT VERIFIED | Chưa có log Android TV, LG, Samsung, PS5/PS4/Xbox vật lý. |
| PC/Steam native | GAP | Dự án chưa có app native PC/Steam được xác minh trên main. |
| iOS/Windows/macOS production | PARTIAL | Có source/workflow preview; chưa xác minh signed production release. |
| Performance/battery/memory soak | NOT VERIFIED | Chưa có profile/soak test dài hạn. |

## Release gate
- [ ] Owner quyết định signing key compatibility hoặc package/key migration.
- [ ] CRITICAL/HIGH security blockers được xử lý hoặc chấp thuận bằng văn bản.
- [ ] Latest Android build trên commit phát hành pass.
- [ ] Emulator smoke pass.
- [ ] Test cài mới, update từ build-1872, backup/restore trên thiết bị.
- [ ] End-to-end capture → OCR → memory → AI fallback → subtitle/TTS → TV pass.
- [ ] LG TLS trust và cleartext LAN policy được xử lý.
- [ ] Release notes chỉ công bố nền tảng đã kiểm chứng.
- [ ] APK production-signed, fingerprint xác minh và download link kiểm tra được.

Không diễn giải build success thành production-ready.
