# AUTONOMOUS_PROGRESS.md

Cập nhật: 2026-11-26 06:05 (UTC+8)
Repository: https://github.com/temu838655-dev/DichGame
Nhánh audit: audit/competitor-parity-20261126
Main commit được kiểm tra: a373a90212eab60788b51bb7bc64e74b29b3a627

## Trạng thái đã xác minh

- [x] Đã đọc AUTONOMOUS_PROGRESS.md, BUG_TRACKER.md, QUALITY_CHECKLIST.md và COMPETITIVE_GAP_ANALYSIS.md trên main.
- [x] Đã kiểm tra Git tree recursive, branches, recent commits, workflows và Releases.
- [x] Main build #1913 (45708105647) success: JVM tests, phone release, Android TV release, APK packaging, signature/zipalign/package checks và artifact upload.
- [x] Main run #1913 xác nhận public Release bị skip đúng policy: IS_PUBLIC_RELEASE=true, CAN_PUBLISH_RELEASE=false do thiếu signing secrets.
- [x] Release công khai gần nhất là build-1872, gồm DichGame.apk và DichGame-TV.apk. Không có Release mới từ main run #1913.
- [ ] Emulator smoke #161 (45708105649) vẫn pending; run #159 trước đó cancelled trước khi emulator chạy. Không coi đây là pass.
- [x] PR #85 security/credential-hardening đang open, không draft; head 7a01bacdf64c9996ce8f37b89b63df041fe2e442, 44 commits, 23 files.
- [x] PR #85 CI #1927 (45709164698) success.
- [ ] PR #85 build #1939 (45710182871) in_progress tại thời điểm kiểm tra.
- [ ] GitHub API báo PR #85 mergeable=false; base SHA c72454d76b1257c745a0209a0a5fd9c00e00c9fd cũ hơn main. Không merge mù quáng.
- [x] PR #85 thêm AES-GCM Android Keystore cho credentials, HTTPS validation CloudAPI, private/local TV PCM host, DNS-rebinding mitigation, backup exclusions và OCR bounds tests.
- [ ] LG/TV custom TLS trust manager vẫn cần review độc lập; CI pass không chứng minh trust model an toàn.
- [ ] SEC-001 keystore/dichgame.jks (2,636 bytes) vẫn nằm trong public repository. Không tự xóa/đổi key nếu chưa có kế hoạch tương thích app đã cài.
- [ ] SEC-002 manifest vẫn bật usesCleartextTraffic=true toàn ứng dụng; cần thiết kế policy LAN/TV tương thích.
- [x] Đã truy cập trực tiếp và đối chiếu PCNET, PS5 Tiếng Việt, Hóng Sub.
- [x] PS5 Tiếng Việt hiển thị 196 game: 31 có bộ Việt hóa, 165 hướng dẫn Dịch AI tại thời điểm truy cập.
- [x] PCNET Việt Hóa công bố app Windows 10/11 cho Steam: tự tìm game, cài/gỡ một nút, version/update và báo lỗi dịch.
- [x] Hóng Sub công bố Remote Play/capture card/window capture, tối đa 8 vùng, menu/full-screen, phụ đề nổi/đè chữ, voice theo nhân vật, sync audio và TV theo từng nền tảng.
- [ ] Chưa có test phần cứng Android arm64, Android TV, LG, Samsung hoặc console thật.

## Kiểm tra kiến trúc và phạm vi

- Main là Kotlin/Android, có app phone và Android TV receiver.
- Có source/workflows preview iOS và Windows; không đồng nghĩa có bản production signed.
- README/competitive analysis ghi PC/Steam native chưa phải chức năng dịch trực tiếp được xác minh trên main.
- Có Catalog, packs, community local store, OCR, Gemini/CloudAPI, ASR, TTS, Remote Play và TV link modules.
- Branch feature riêng không được tính là đã tích hợp vào main.
- Không sao chép source, giao diện độc quyền, thương hiệu hoặc dữ liệu có bản quyền từ đối thủ.

## Ưu tiên tiếp theo

### P0 — Security/release
1. Chủ sở hữu quyết định giữ khóa cũ trong GitHub Secrets để duy trì update compatibility (chấp nhận rủi ro) hay migration package/key và dữ liệu.
2. Review LG/TV TLS trust manager; thiết kế TOFU/pinning có xác nhận người dùng.
3. Thu hẹp cleartext traffic mà không phá receiver LAN; audit từng HTTP/WebSocket endpoint.
4. Xác minh run #1939, cập nhật base PR #85 và giải quyết conflicts có kiểm soát.
5. Hoàn tất emulator smoke test.

### P1 — Kho Việt hóa
1. Thống kê game/packs/entries thực tế trên main; không dùng số liệu đối thủ làm số liệu dự án.
2. Audit metadata: game ID, aliases, platform, game build, source, license, scope, coverage evidence, pack version/changelog.
3. Test exact → normalized → fuzzy → context matching có confidence threshold.
4. Kiểm tra game profile/terminology/character context và AI fallback.
5. Kiểm tra import/export, moderation, anti-spam, history và rollback.

### P2 — OCR/AI/ASR/TTS
1. Test corpus OCR đa ngôn ngữ, precision/recall.
2. Test 8 vùng, menu/full-screen, small text, duplicate suppression.
3. Test AI profile, consistency, placeholders, cache, retry, quota và offline fallback.
4. Test ASR short utterance, overlap OCR/ASR và timing.
5. Test TTS, character voices, audio route và sync.

### P3 — TV/multi-platform
1. Android TV pairing/overlay/reconnect/latency/audio.
2. LG/Samsung TV thật; không hứa overlay toàn hệ thống nếu OS không cho phép.
3. PC/Steam native và capture card desktop cần app, build và hardware test riêng.
4. Phân biệt iOS/Windows preview với signed production release.

### Tiếp tục từ đây
1. Kiểm tra run #1939 và PR #85; đọc logs/jobs.
2. Review diff PR #85: WebView JS bridge, ApiSecrets migration/backup, TvLink DNS/IP, OCR bounds, LG TLS trust.
3. Nếu PR base conflict, cập nhật nhánh từ main có kiểm soát, chạy lại CI rồi mới cân nhắc merge.
4. Kiểm tra smoke #161; xác minh runner và log, không tuyên bố test khi pending.
5. Audit kho game/pack schema, importer, matcher và moderation trên main.
6. Cập nhật ba file theo dõi sau mỗi giai đoạn có bằng chứng.
7. Không tạo Release mới trước khi signing strategy, smoke và release checks đạt.

## Bằng chứng
- Main commit: https://github.com/temu838655-dev/DichGame/commit/a373a90212eab60788b51bb7bc64e74b29b3a627
- Main build #1913: https://github.com/temu838655-dev/DichGame/actions/runs/45708105647
- Smoke #161: https://github.com/temu838655-dev/DichGame/actions/runs/45708105649
- PR #85: https://github.com/temu838655-dev/DichGame/pull/85
- PR #85 test #1927: https://github.com/temu838655-dev/DichGame/actions/runs/45709164698
- PR #85 build #1939: https://github.com/temu838655-dev/DichGame/actions/runs/45710182871
- Release build-1872: https://github.com/temu838655-dev/DichGame/releases/tag/build-1872
- PCNET: https://pcnetdl.com/pcnet-viet-hoa/
- PS5 Tiếng Việt: https://ps5tiengviet.com/games.html
- Hóng Sub: https://honggame.store/hong-sub/
