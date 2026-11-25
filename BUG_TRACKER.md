# BUG_TRACKER.md

Cập nhật: 2026-11-26 06:05 (UTC+8)

| ID | Mức độ | Vấn đề / bằng chứng | Nguyên nhân hoặc ảnh hưởng | Trạng thái |
|---|---|---|---|---|
| SEC-001 | CRITICAL | keystore/dichgame.jks (2,636 bytes) đang được track trong repository public; build-1872 dùng key này khi signing secrets trống. | Private signing key đã lộ; xóa file ở commit mới không thu hồi bản sao trong Git history. | BLOCKED — chủ sở hữu chọn giữ key trong GitHub Secrets để duy trì update compatibility (chấp nhận rủi ro) hoặc migration package/key và dữ liệu. |
| SEC-002 | HIGH | AndroidManifest bật usesCleartextTraffic=true toàn application. | LAN TV receiver cần HTTP; policy global mở rộng bề mặt tấn công. | OPEN — inventory endpoint, tách transport LAN và cấu hình policy có phạm vi; không tắt mù quáng. |
| SEC-003 | HIGH | PR #85 thêm AES-GCM Android Keystore, CloudAPI HTTPS validation, backup exclusions, private/local TV PCM host. | Cải thiện credential/transport security. | PARTIAL — PR open; run #1927 success, #1939 đang chạy; cần review, resolve base và test migration thiết bị. |
| SEC-004 | HIGH | LG/TV custom TLS trust manager cần xác minh có chấp nhận mọi certificate hay không. | Trust-all TLS có thể cho phép MITM. | OPEN — review implementation, thiết kế TOFU/pinning hoặc fingerprint confirmation. |
| QA-001 | HIGH | Smoke #161 (45708105649) pending; run #159 cancelled trước khi emulator chạy. | Chưa có bằng chứng UI/runtime smoke. | OPEN — runner phải thực thi và test phải pass. |
| QA-002 | HIGH | Chưa có bằng chứng test Android arm64, Android TV, PS4/PS5/Xbox thật. | CI không xác nhận capture, audio, Remote Play, pairing, overlay hoặc hiệu năng phần cứng. | BLOCKED — cần thiết bị/console/firmware/mạng/tài khoản. |
| QA-003 | HIGH | OCR/AI/ASR/TTS runtime chưa được xác minh end-to-end trên thiết bị. | Unit tests không chứng minh chất lượng OCR, timing, voice hoặc subtitle loss. | OPEN — cần test corpus và thiết bị thật. |
| REL-001 | MEDIUM | Main build #1913 success; public Release step skip đúng khi thiếu signing secrets. Release gần nhất vẫn build-1872. | Không thể phát hành production key an toàn khi secrets chưa đủ. | VERIFIED (gate behavior only) — CAN_PUBLISH_RELEASE=false; không đồng nghĩa có Release mới. |
| REL-002 | MEDIUM | PR #85 API báo mergeable=false, base SHA cũ hơn main; run #1939 in_progress. | Có thể cần cập nhật base/resolve conflicts. | OPEN — cập nhật branch và chạy lại CI sau review. |
| LIB-001 | MEDIUM | Có Catalog/pack/community modules; chưa xác minh coverage, license provenance, matcher confidence và moderation end-to-end. | Có module code không đồng nghĩa có kho dữ liệu phong phú. | OPEN — audit schema/data/importer/matching/review queue. |
| DOC-001 | LOW | Các file tiến độ, lỗi, checklist và competitive analysis được duy trì. | Tài liệu phải phản ánh bằng chứng. | VERIFIED (tài liệu tồn tại; cần cập nhật liên tục). |

## Quy tắc trạng thái
- OPEN: cần điều tra hoặc sửa.
- PARTIAL: có thay đổi nhưng thiếu review/kiểm thử.
- FIXED: source đã thay đổi, chưa đủ bằng chứng xác minh.
- VERIFIED: có test/log/bằng chứng phù hợp cho phạm vi nêu.
- BLOCKED: cần quyết định, quyền truy cập, bí mật hoặc thiết bị bên ngoài.

Không đánh dấu VERIFIED chỉ dựa trên việc biên dịch thành công.

## Audit bổ sung — dữ liệu kho game

- [x] Main Catalog.kt có 201 tên game và 201 platform-code entries; đây chỉ là metadata chọn game, không phải số game đã Việt hóa.
- [x] Built-in glossary: 33 nhóm trong VietHoa.kt và 81 nhóm trong VietHoaPlus.kt; built-in phrase memory có 58 dòng chung và 18 dòng riêng ở 6 franchise groups.
- [ ] Chưa có bằng chứng 201 game đều có phrase packs. Không dùng nhãn “Có gói” cho trạng thái chỉ có glossary; PR #86 đổi nhãn UI thành “Thuật ngữ riêng”.
- [ ] Python knowledge_base hiện độc lập với Android/desktop; chưa có hosted sync, account auth hoặc reviewer admin tích hợp.
- [ ] Các pack do người dùng import/manifest HTTPS cần được kiểm kê riêng trên thiết bị/server; chưa có số liệu aggregate đáng tin cậy.
- [x] Matcher bug: previous_sentence/next_sentence đã được nối vào context score và có regression test.
- [x] Knowledge Base Tests #218 và #219 success.
- [ ] Android build #1948 và emulator smoke #161 chưa có kết quả cuối ở thời điểm cập nhật.
