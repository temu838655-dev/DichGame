# COMPETITIVE_GAP_ANALYSIS.md

Ngày đối chiếu: 2026-11-26 (UTC+8)
Repository: https://github.com/temu838655-dev/DichGame
Main commit: a373a90212eab60788b51bb7bc64e74b29b3a627

Chỉ ghi nhận tính năng công khai trên website và source/CI repository. Không suy đoán kiến trúc nội bộ, không sao chép mã nguồn, giao diện độc quyền, thương hiệu hoặc dữ liệu có bản quyền.

## 1. PCNET

Nguồn:
- https://pcnetdl.com/
- https://pcnetdl.com/pcnet-viet-hoa/
- https://pcnetdl.com/viet-hoa-mien-phi/
- https://pcnetdl.com/pcnet-viet-hoa/grounded-2/

Tính năng quan sát được:
- PCNET Việt Hóa là ứng dụng Windows 10/11 miễn phí, mã nguồn mở, hướng tới game Steam; trang tải mô tả một file khoảng 2 MB.
- Trang kho công khai hiển thị 14 game Steam tại lần kiểm tra 2026-11-26; mỗi trang game công bố build/version tương thích, phạm vi đã dịch/giữ nguyên, ngày cập nhật và hướng dẫn.
- App tự tìm game Steam; cài Việt hóa và gỡ Việt hóa bằng thao tác một nút; app báo khi có bản dịch mới.
- Có báo lỗi dịch kèm ảnh chụp/Discord; người dùng có thể yêu cầu game mới.
- PCNET Dịch Game đã phát hành Windows installer 0.7.16 (khoảng 103 MB) cho PS5. Nguồn hình là Sony PS Remote Play hoặc capture card/OBS; có thể chơi PS5 trên máy tính qua Remote Play và hiện phụ đề lên hình.
- PCNET Dịch Game tự thông báo/cập nhật trong app. Trang chính thức hướng dẫn kiểm tra SHA-256 bằng PowerShell; họ ghi chú bộ cài chưa có chữ ký số nên Windows SmartScreen có thể cảnh báo.
- PC game trong PCNET Dịch Game vẫn được ghi là “sắp có” tại trang tải ngày 2026-11-26.
- Website gắn nhãn mã nguồn mở và liên kết GitHub pcnetdl/dich-game cùng pcnet-viet-hoa, nhưng cả hai URL repository trả 404 khi kiểm tra; không thể xác minh source/installer internals ở lần audit này.
- Chưa thấy bằng chứng đủ rõ trong tài liệu công khai về backup/restore file gốc, checksum integrity của từng file game, rollback transaction hoặc multi-store ngoài Steam cho PCNET Việt Hóa.

## 2. PS5 TIẾNG VIỆT / CONSOLE TRANSLATOR

Nguồn:
- https://ps5tiengviet.com/
- https://ps5tiengviet.com/games.html
- https://ps5tiengviet.com/android-guide.html
- https://ps5tiengviet.com/desktop.html

Tính năng quan sát được:
- Kho game hiển thị 196 game tại thời điểm truy cập.
- 31 game có bộ câu Việt hóa; 165 game có hướng dẫn Dịch AI.
- Game cards ghi thể loại, phạm vi nội dung, nguồn dịch và hướng dẫn theo game.
- Bộ câu có sẵn được ưu tiên so khớp trước; câu không khớp mới dùng phương thức dịch đã chọn.
- Nhãn Có Việt hóa không đồng nghĩa toàn bộ game đã dịch; phạm vi cụ thể được ghi theo game.
- Có yêu cầu bổ sung game qua bot/nhóm.
- Website công bố Android, iOS/iPadOS, Windows và macOS; tuyên bố nền tảng không thay thế kiểm thử độc lập.
- Website mô tả Premium/PRO trả phí; dự án này không cần sao chép paywall.

## 3. HÓNG SUB

Nguồn:
- https://honggame.store/hong-sub/
- https://honggame.store/huong-dan-dich-phu-de-game-ps4-ps5-tieng-viet/

Tính năng quan sát được:
- Nguồn hình: PS Remote Play (Sony app hoặc luồng tích hợp), capture card HDMI, cửa sổ game/PC.
- Vùng phụ đề kéo chọn; tối đa 8 vùng dịch bổ sung theo hướng dẫn.
- Dịch câu, dịch menu, gợi ý nhiệm vụ và dịch cả màn hình; tự cập nhật khi màn hình đổi.
- Phụ đề nổi; có tùy chọn đè tiếng Việt lên phụ đề gốc.
- Đọc tiếng Việt; giọng riêng theo nhân vật, chọn đọc trên máy tính/TV và đồng bộ độ trễ âm thanh.
- Kho dịch chung dùng câu đã có dữ liệu trước khi chờ AI.
- Hướng dẫn Android app, Android TV/Google TV, LG webOS và Samsung TV; cách cài, cấp quyền và ghép mã/IP riêng từng nền tảng.
- Hóng Sub mô tả phần mềm Windows có 25 giọng AI; không coi số lượng giọng là benchmark chất lượng.

## 4. BẢNG ĐỐI CHIẾU

| Hạng mục | PCNET | PS5 Tiếng Việt | Hóng Sub | Dịch Game trên main |
|---|---|---|---|---|
| Kho game | Trang Việt hóa/game detail | 196 game công khai | Kho dịch chung/chọn game | Có Catalog.kt; số lượng và độ đầy đủ chưa audit |
| Bộ câu Việt hóa | Gói Steam | 31 game có pack | Kho dịch chung | Có pack/catalog/community modules; coverage chưa xác minh |
| Dịch AI theo game | Không phải trọng tâm installer | 165 hướng dẫn Dịch AI | Chọn game để tăng ngữ cảnh | Có Gemini/AI/game context; accuracy cần benchmark |
| OCR phụ đề | PCNET Dịch Game PS5 riêng; PC desktop chưa phát hành tại trang đã xem | Có app dịch màn hình theo website | Có | Có ML Kit/Gemini OCR source; runtime chưa xác minh |
| Nhiều vùng OCR | Chưa thấy công bố rõ ở installer | Chưa có số vùng xác minh | Tối đa 8 vùng | Có region/capture source; cần test 8 vùng |
| Menu/toàn màn hình | PCNET Dịch Game chưa có bản tải công khai | Có dịch nội dung màn hình | Có | Có menu/full-frame source; chưa xác minh end-to-end |
| Kho cộng đồng | Báo lỗi/đề nghị thêm game | Bot/nhóm yêu cầu game | Kho dịch chung | CommunityLocalStore; moderation/sync chưa xác minh |
| Giọng đọc | Không phải điểm chính installer | Có TTS/voice theo website | Nhiều giọng, voice nhân vật | Có TTS/AI speech source; chất lượng/sync chưa xác minh |
| Đồng bộ TV | Chưa thấy bằng chứng | Có phụ đề TV theo website | Có hướng dẫn TV/audio sync | Có Android TV/TV link source; chưa test TV thật |
| PC/Steam | PCNET Việt Hóa có installer Steam; PCNET Dịch Game PC vẫn sắp có | Website công bố desktop app | Có window/capture | GAP: chưa xác minh native PC/Steam trên main |
| Cài/gỡ file game | Một nút cài/gỡ | Bộ câu trong app | Không phải installer game files | Chưa xác minh installer/backup/rollback PC |
| Version compatibility | Có game build/version | Pack/scope theo game | Chọn game/profile | Cần audit metadata và pack validator |
| Hướng dẫn game | Có | Có | Có | Catalog/profile có; độ bao phủ chưa xác minh |
| Update | Báo pack mới | Catalog/pack cập nhật | App tự cập nhật theo nền tảng | Có CloudRefresh/updater source; chưa test thiết bị |
| Báo lỗi/đóng góp | Báo lỗi kèm ảnh | Bot/nhóm yêu cầu game | Hỗ trợ gửi log/lỗi | Local community store; moderation/publish chưa xác minh |

## 5. TRẠNG THÁI REPOSITORY ĐÃ KIỂM CHỨNG

- Main run #1913: Android phone/TV build, JVM tests, APK packaging, signature/zipalign/package checks success.
- Main run #1913 bỏ qua public Release vì signing secrets chưa đủ.
- Release công khai mới nhất tại thời điểm audit là build-1872, có phone APK và TV APK.
- Có modules Catalog, packs, community local store, OCR, Gemini, ASR, TTS, Remote Play và TV link.
- Có workflows/branches preview iOS/Windows; không đồng nghĩa production signed releases.
- Smoke #161 pending; smoke #159 bị cancelled trước khi emulator chạy.
- OCR/AI/ASR/TTS chưa được test end-to-end trên thiết bị.
- Android TV, LG webOS, Samsung Tizen chưa được test TV vật lý.
- PS5/PS4/Xbox Remote Play chưa có bằng chứng console thật.
- Pack count, license provenance, moderation và matching quality chưa được thống kê/benchmark từ dữ liệu thật.
- PC/Steam native capture/installer chưa được xác minh trên main.

## 6. KHOẢNG CÁCH ƯU TIÊN

### P0 — Security/release
1. Keystore release đã commit trong public repository: cần quyết định key migration/compatibility.
2. usesCleartextTraffic=true toàn app: thu hẹp an toàn nhưng giữ receiver LAN.
3. Review LG/TV TLS trust manager; không chấp nhận trust-all.
4. Hoàn tất emulator smoke và xác minh PR #85.

### P1 — Translation library
1. Thống kê game/packs/entries thực tế trên main.
2. Chuẩn hóa metadata: game ID, aliases, platform, game version/build, language, source, license, scope, coverage evidence, pack version, changelog.
3. Exact → normalized → fuzzy → context matching có confidence threshold.
4. Game profile/terminology/character context và AI fallback.
5. Import/export, license manifest, provenance, review queue, anti-spam, version history, rollback.
6. Catalog search/filter/sort và yêu cầu thêm game.

### P2 — OCR/AI/voice
1. Corpus test ảnh game đa ngôn ngữ và đo OCR precision/recall.
2. Test 8 vùng, menu/full-screen, small text, duplicate suppression, frame cadence.
3. Test AI profile, speaker consistency, placeholders, cache, retry, quota, offline fallback.
4. Test ASR short utterance, OCR/ASR deduplication và timing.
5. Test TTS voice, route, sync và interruptions.

### P3 — TV/multi-platform
1. Android TV pairing, overlay, reconnect, latency, audio route.
2. LG/Samsung test thật; tuân thủ giới hạn OS, không hứa overlay toàn hệ thống khi không được hỗ trợ.
3. PC/Steam cần desktop capture + installer; backup/verify/rollback trước khi thay file game; chỉ phân phối patch/delta hợp pháp.
4. Phân biệt iOS/Windows/macOS preview, signed build và production release.
5. Capture card cần desktop app và hardware validation.

## 7. NGUYÊN TẮC DỮ LIỆU VÀ QUYỀN SỬ DỤNG

- Chỉ nhập bộ dịch do dự án tự tạo, cộng đồng cấp phép rõ ràng, mã nguồn mở tương thích license hoặc tác giả cho phép phân phối.
- Mỗi pack lưu source, tác giả/nhóm, license, game version, scope, coverage và lịch sử.
- Không sao chép nguyên bộ dịch độc quyền từ website đối thủ.
- Không phát hành game files hoặc tài sản game có bản quyền.
- PC installer phải backup file gốc, kiểm tra checksum/version, rollback transaction và không ghi đè khi không chắc chắn.

## 8. KẾT LUẬN

Dự án có nền tảng Android và nhiều module cốt lõi, nhưng chưa đủ bằng chứng để tuyên bố parity hoàn chỉnh. Khoảng cách lớn nhất là dữ liệu kho Việt hóa có nguồn rõ ràng, PC/Steam native, kiểm thử OCR/AI/voice end-to-end, bảo mật signing/network và xác minh thiết bị thật.

Không lấy số lượng game của đối thủ làm số liệu của dự án. Không đánh dấu tính năng hoàn thành chỉ vì có source, branch hoặc workflow preview.

## Nguồn công khai
- PCNET: https://pcnetdl.com/
- PCNET Việt Hóa: https://pcnetdl.com/pcnet-viet-hoa/
- PCNET Dịch Game PS5: https://pcnetdl.com/pcnet-dich-game/
- PCNET Grounded 2: https://pcnetdl.com/pcnet-viet-hoa/grounded-2/
- PS5 Tiếng Việt: https://ps5tiengviet.com/
- PS5 Tiếng Việt games: https://ps5tiengviet.com/games.html
- Hóng Sub: https://honggame.store/hong-sub/
- Hóng Sub guide: https://honggame.store/huong-dan-dich-phu-de-game-ps4-ps5-tieng-viet/

## Repository evidence
- Main commit: https://github.com/temu838655-dev/DichGame/commit/a373a90212eab60788b51bb7bc64e74b29b3a627
- Main CI #1913: https://github.com/temu838655-dev/DichGame/actions/runs/45708105647
- Security PR #85: https://github.com/temu838655-dev/DichGame/pull/85
