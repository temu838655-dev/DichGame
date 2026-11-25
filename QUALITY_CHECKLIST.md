# QUALITY_CHECKLIST.md

Cập nhật: 2026-11-26 06:05 (UTC+8)

| Hạng mục | Trạng thái | Bằng chứng / giới hạn |
|---|---|---|
| Repository / nhánh / quyền | VERIFIED | Repo public; main a373a902; nhánh audit riêng tạo để cập nhật tài liệu. |
| Branches / commits / workflows | VERIFIED (inventory) | Đã đọc tree, branches, commits, workflow runs và Releases; chưa review từng nhánh feature. |
| Android phone release build | VERIFIED (CI only) | Main #1913 success; chưa thay thế test điện thoại thật. |
| Android TV release build | VERIFIED (CI only) | Main #1913 success; chưa xác minh receiver/overlay trên TV thật. |
| JVM unit tests | VERIFIED (CI only) | Main #1913 và PR #85 #1927 success. |
| APK signature / zipalign / package IDs | VERIFIED (CI only) | Main #1913 checks pass; chưa xác nhận production signing key. |
| Public Release gate | VERIFIED (gate behavior only) | Main #1913: IS_PUBLIC_RELEASE=true, CAN_PUBLISH_RELEASE=false; release step skipped. |
| Latest official release | VERIFIED (metadata) | build-1872 có phone APK và TV APK; không phải build mới từ main #1913. |
| Production signing key safety | NOT PASS / BLOCKED | keystore đã commit trong public Git; cần quyết định compatibility hay migration. |
| Emulator smoke | NOT PASS | #161 pending; #159 cancelled trước khi emulator chạy. |
| Android arm64 device | NOT VERIFIED | Chưa có log thiết bị thật. |
| PS4/PS5/Xbox Remote Play | NOT VERIFIED | Cần console/firmware/account/mạng và test session thật. |
| OCR languages | NOT VERIFIED | Có ML Kit và Gemini cloud OCR; cần corpus, precision/recall và runtime tests. |
| OCR multi-region/full-screen | PARTIAL / NOT VERIFIED | Có source/UI; chưa test 8 vùng, menu, full-screen và tọa độ end-to-end. |
| Gemini/AI translation | NOT VERIFIED | Có retry/key rotation/cache/error handling; chưa test API thật trong app với key hợp lệ. |
| Translation memory/pack matching | PARTIAL / NOT VERIFIED | Có catalog/pack modules; chưa benchmark exact/normalized/fuzzy/context matching và data coverage. |
| Community moderation | NOT VERIFIED | Local community store tồn tại; chưa xác minh submit/approve/reject/audit/anti-spam/sync. |
| ASR / PCM lifecycle | PARTIAL | Có teardown/queue code và unit tests; chưa test utterance ngắn, OCR overlap, route và latency. |
| TTS / character voices / sync | NOT VERIFIED | Có TTS/AI speech source; chưa test voice, route và drift thực tế. |
| TV credential storage | PARTIAL (PR #85) | AES-GCM + migration + backup exclusions; PR chưa merge và chưa test restore thiết bị. |
| CloudAPI HTTPS | PARTIAL (PR #85) | Source/tests trong PR; run #1927 success, #1939 in progress lúc audit. |
| LAN cleartext scope | NOT PASS | Manifest bật cleartext toàn app; cần policy hẹp hơn nhưng tương thích receiver. |
| LG/Samsung TV | NOT VERIFIED | Source/workflows có; chưa test TV vật lý, permission, overlay và update. |
| LG TLS trust | OPEN / NOT PASS | Cần review trust manager và TOFU/pinning. |
| PC/Steam native | GAP | Không tuyên bố dịch trực tiếp PC/Steam trên main nếu chưa có app chạy/test end-to-end. |
| iOS/Windows previews | PARTIAL | Có workflow/assets preview; IPA chưa ký và Windows chưa phải production signed. |
| UI navigation/accessibility | NOT VERIFIED | Smoke chưa chạy; cần kiểm tra mọi màn hình, font scale, orientation và TV focus. |
| Persistence/backup/restore | PARTIAL | Có backup exclusions trên PR; cần test migration/restore dữ liệu cũ. |
| Update compatibility | PARTIAL | Workflow có certificate/version checks; chưa test update từ build-1872 trên thiết bị. |
| Performance/battery/memory soak | NOT VERIFIED | Chưa có profile/soak test dài hạn. |
| Competitor audit/docs | VERIFIED (audit branch) | Đã kiểm tra 3 website và ghi nguồn; không đồng nghĩa dự án đạt parity. |

## Release gate
- [ ] Chủ sở hữu quyết định signing key và production GitHub Secrets được cấu hình an toàn.
- [ ] Không còn CRITICAL/HIGH security blocker chưa xử lý hoặc chưa được chấp thuận bằng văn bản.
- [ ] Emulator smoke test thực thi và pass.
- [ ] Unit tests + phone/TV release builds + APK verification pass trên commit phát hành.
- [ ] Test cài mới, update từ build-1872, backup/restore và updater trên thiết bị.
- [ ] Test capture → OCR → translation memory → AI fallback → subtitle/TTS → TV end-to-end.
- [ ] README/Release notes chỉ công bố nền tảng đã kiểm chứng.
- [ ] APK ký production key được xác minh fingerprint, cài đặt và tải lại sau phát hành.

Không diễn giải CI build thành công thành ứng dụng production-ready.

| Hạng mục | Trạng thái bổ sung | Bằng chứng / giới hạn |
|---|---|---|
| Danh sách game trong app | VERIFIED (source count) | Catalog.kt có 201 tên và 201 platform-code entries; không đồng nghĩa có 201 bộ dịch. |
| Built-in glossary | VERIFIED (source inventory) | 33 nhóm VietHoa.kt và 81 nhóm VietHoaPlus.kt; không phải thoại đầy đủ. |
| Built-in phrase memory | VERIFIED (source inventory) | 58 dòng dùng chung + 18 dòng riêng trên 6 franchise groups. |
| Phrase packs 201 game | NOT VERIFIED | Không có bằng chứng toàn bộ 201 game có pack thoại riêng; user-imported/remote packs cần inventory riêng. |
| Kho game label accuracy | FIXED IN PR #86 | “Có gói” đổi thành “Thuật ngữ riêng”; UI giải thích đây không phải bộ thoại đầy đủ. Android build đang chờ. |
| Adjacent dialogue matching | VERIFIED (Python CI) | previous_sentence/next_sentence đã được dùng; Knowledge Base Tests #218/#219 success. |
| Knowledge Base app integration | NOT COMPLETE | Python SQLite component và verified-only CSV bridge có; hosted sync/auth/reviewer admin và end-to-end app integration chưa có. |
