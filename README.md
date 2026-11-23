# Dịch game

Dịch phụ đề game và phim sang tiếng Việt ngay trên màn hình, có thể đọc to, có thể đẩy phụ đề lên TV.

- `app/` – app điện thoại Android (`com.dichgame.app`): chụp màn hình, đọc chữ (OCR), dịch, hiện bản dịch nổi.
- `tv/` – app nhận phụ đề cho Android TV / Google TV (`com.dichgame.tv`), vẽ nổi trên hình HDMI.
- `tv-webos/` – mã nguồn app webOS nhận phụ đề trên LG (cửa sổ trong suốt, thử nghiệm; chưa kiểm chứng phủ HDMI thật).
- `tv-tizen/` – mã nguồn app Samsung Tizen dùng TVWindow/PiP BEHIND để hiện phụ đề phía trên nguồn HDMI (cần ký chứng chỉ theo TV).
- `tv-web-common/` và `docs/TV_WEBOS_TIZEN.md` – bộ nhận phụ đề PIN/Wi-Fi dùng chung và hướng dẫn đóng gói IPK/WGT.
- `keystore/` – khóa ký APK mặc định (xem mục "Khóa ký").
- `.github/workflows/build-apk.yml` – tự build toàn bộ trên GitHub.

## Tính năng (app điện thoại)

| Màn hình | Có gì |
| --- | --- |
| Chính | Chuyển **Dịch / Thuyết minh**, chọn ngôn ngữ game (**28 thứ tiếng**, xem mục "Ngôn ngữ đọc được"), nút Bắt đầu, hộp **"Bạn chơi ở đâu?"** (TV / điện thoại) |
| Cách dịch | **Dịch trên máy** (miễn phí, offline), **CloudAPI** (AI theo chuẩn OpenAI, nhập địa chỉ + khóa + model), **Google Gemini** (khóa + model), nút **Tải gói ngôn ngữ** (tải trước mô hình dịch trên máy), nút **Lưu và kiểm tra**, số câu trước cho AI hiểu mạch truyện |
| Cài đặt › Giọng đọc | **Giọng của máy**, **giọng AI Gemini** (8 giọng) hoặc **giọng offline** tải thêm về máy (chạy không cần mạng; AI và offline lỗi thì tự lùi về giọng máy), tốc độ 0,5x–3x, **đọc theo cảm xúc**, **tự đọc nhanh khi câu dồn lại**, đọc qua loa TV, nghe thử |
| Cài đặt › Phụ đề | Hiện câu gốc, **hiện câu thoại trước** (chữ nhỏ phía trên), cỡ chữ 50–200 %, kiểu chữ, mục **Nâng cao** (tần suất quét, chữ hiện dần, dịch menu tự động) |
| Kho game | Tìm kiếm, lọc theo nền tảng (**PS5, PS4, Xbox, PC, Steam, Switch**), tab **Đã chơi / Tất cả**, tab trạng thái có số đếm **Tất cả / Việt hoá / Dịch AI**, **ảnh bìa** từng game (tải theo tên game: ưu tiên ảnh **dọc** ở Steam rồi Wikipedia; game nào không có ảnh dọc thì dùng ảnh **ngang** đặt vừa khung trên nền mờ; tên phải khớp hẳn; lưu trên máy, tắt được; game không tìm được ảnh nào dùng ảnh vẽ sẵn), nhãn **VIỆT HÓA** trên ô, nút **Sao chép tên game**, nút **Game khác**. Bấm một game để xem **gói Việt hoá**, cách chơi và dịch theo nền tảng, rồi **Chọn game này** hoặc **Chọn và dịch ngay**: app tạo (hoặc chuyển sang) hồ sơ tên game đó và dùng gói Việt hoá của game. Hơn 200 game phổ biến (ảnh bìa tải khi có mạng) |
| Gói Việt hoá | Mỗi game có sẵn **thuật ngữ** (tên riêng, chiêu thức, địa danh), **thẻ nhân vật** (xưng hô, giọng, nhịp nói) và **câu thông báo chuẩn** ("YOU DIED", "Quest complete"...). Áp dụng khi dịch bằng AI lẫn dịch trên máy: câu thoại khớp thì dùng luôn, không qua bộ dịch (chịu được lỗi đọc chữ như `Y0U D1ED`). Nhập gói từ file hoặc link, sửa thẻ nhân vật, lưu gói của mình ra file để chia sẻ. Gói có sẵn do người làm app soạn theo cách gọi phổ biến, **không phải bản dịch chính thức** của nhà phát hành |
| Dịch theo cảm xúc | AI gắn thẻ cảm xúc và nhịp cho mỗi câu (không có thì đoán bằng từ khóa); giọng đọc đổi tốc độ, cao độ, kiểu nói; phụ đề hiện nhãn màu (vd "giận dữ · nhanh"). Nhịp riêng của từng nhân vật đặt trong thẻ nhân vật. Tắt được trong Gói Việt hoá |
| Cài đặt › Hồ sơ game | Nhiều hồ sơ, mỗi hồ sơ nhớ tên game + vùng quét riêng, **11 kiểu dịch** (Tự nhiên, Thuyết minh, Ngắn gọn, Sát nghĩa, Anh – em, Bụi bặm, Cao bồi, Podcast, Tin tức, Học tập) và **Prompt riêng** (tự viết yêu cầu cho AI), **từ điển riêng** |
| Việt hoá cộng đồng | Trong Kho game và Gói Việt hoá có liên kết tới The Red Team, PS Việt Hóa, Tổ Mèo Cam, NSW Việt Hóa Hub, Vịt Việt Hóa, VuiKey, VietPatch... theo nền tảng. **Chỉ mở trang gốc**: app không tải hộ, không đăng lại bản vá (mỗi bản vá có điều khoản của tác giả) |
| Phụ đề trên TV | Chọn hãng TV (Google TV / LG / Samsung), các bước dùng, **Kết nối TV**: Google TV ghép bằng mã 8 số; TV LG ghép qua LG Connect Apps và hiện phụ đề bằng thông báo của TV (thử nghiệm); trang web phụ đề phát từ điện thoại (xem bằng trình duyệt TV/iPhone/máy tính bảng); hướng dẫn Developer Mode LG/Samsung |
| Dịch video trên web | Trình duyệt trong app với bảng dịch ở đáy và thanh công cụ: nút dịch (bắt đầu / tạm dừng, nhấn giữ để chọn ngôn ngữ), nguồn chữ **CC** (phụ đề của trình phát: thẻ track, YouTube, video.js, JW Player, Plyr...) hoặc **OCR** (quét chữ trong khung chọn vùng kéo được), chữ hiện dần, thuyết minh, ẩn phụ đề gốc, cỡ chữ, đọc to, cài đặt |
| Chơi trên điện thoại | Chọn **Chơi trên điện thoại** để mở app chơi từ xa đã cài (PS Remote Play, Chiaki, Moonlight, Steam Link, GeForce NOW, Xbox) và hiện bản dịch đè lên hình game. |
| Remote Play tích hợp (thử nghiệm) | Chơi và dịch ngay trong app, không cần mở PS Remote Play. Trình ghép nối 3 bước (tìm máy trong Wi-Fi/LAN hoặc nhập IP, PSN Account ID dạng Base64, mã 8 số trên máy); bộ dò riêng của app gửi gói hỏi thẳng qua Wi-Fi/LAN theo ba cách (255.255.255.255, broadcast của mạng, quét từng IP trong dải mạng) để vẫn thấy máy khi router chặn broadcast, có nút Quét lại và dòng cho biết đang quét mạng nào; nhiều người chơi cho mỗi máy, nên dùng người chơi phụ để vừa chơi trên TV vừa chơi trên điện thoại; đánh thức máy từ chế độ nghỉ; chất lượng hình chỉnh được (Tự động theo khả năng giải mã của máy và cỡ màn hình, 720p, 1080p, 540p, 360p; 30 hoặc 60 FPS; H.264 hoặc H.265 nếu máy giải mã được bằng phần cứng; tốc độ mạng) và mô tả đúng giới hạn của engine (tối đa 1080p; PS4 thường chỉ H.264; không có 4K hay 120 FPS); xoay dọc ↔ ngang mượt, mặc định ngang, có thể chọn Tự động theo cảm biến hoặc Dọc: màn chơi, SurfaceView và phiên Remote Play được giữ nguyên khi xoay (không dựng lại, không kết nối lại); tay cầm ảo đa chạm (dọc và ngang) hoặc tay cầm thật nối điện thoại; màn chơi có thanh trên (✕, tay cầm ảo, chọn người chơi), khung video viền vàng với thẻ "Đang tìm máy…", thanh 9 công cụ dịch xếp như app mẫu (xoay, **nút PS** gửi thật tới máy chơi game, khung chọn vùng có thanh **Đặt lại / Xong** khi đang chỉnh, bật/tắt dịch phụ đề, đọc to, cỡ chữ, tốc độ quét, ⚙ cài đặt ngay trong màn chơi (Dịch hoặc **Thuyết minh**, ngôn ngữ trong game, giọng đọc cơ bản / AI offline / Gemini, tốc độ đọc, nghe thử, tải giọng offline, phóng hình, lối vào chất lượng hình) mà không rời màn chơi nên phiên Remote Play được giữ nguyên, trợ giúp) kèm hai nút **Dịch hết chữ** (đọc và dịch toàn bộ chữ trên khung hình MỘT lần rồi tự tắt) và **Dịch menu** (dịch chữ giao diện liên tục); **thanh tự thu gọn** để không vướng khi chơi: bấm xong một nút hoặc chỉnh xong khung là thu lại, để yên cũng tự thu lại (xoay ngang sau 5 giây; xoay dọc sau 8 giây, tính lại mỗi lần chạm tay cầm ảo và không thu khi đang giữ nút), kể cả khi chưa kết nối máy chơi game; thu lại rồi thì xoay ngang còn một chấm nhỏ giữa mép trên, xoay dọc còn thanh kéo để mở lại; nút bật/tắt dịch phụ đề ngay trên màn chơi: **hình lấy thẳng từ video Remote Play (PixelCopy), không cần quyền ghi màn hình và không cần quyền hiển thị trên ứng dụng khác**, nút khung (⛶) để kéo khung đọc phụ đề, khung tự ẩn khi đã chọn xong, chữ dịch nằm trong màn chơi nên không lọt vào ảnh đọc chữ; kết nối lại và nhập PIN khi máy yêu cầu. Nếu app đóng đột ngột, lần mở sau hiện lý do hệ thống ghi nhận và các bước cuối để biết đường sửa. Engine Chiaki-ng hiện chỉ build cho arm64-v8a. Chưa xác nhận tương thích mọi firmware/router; cần thử trên PS4/PS5 thật. Không nhập mật khẩu PSN vào app. |
| Cập nhật ngay trong app | Mỗi lần có bản phát hành mới (tag `build-N`, N cũng là số bản dựng ghi trong app), app tự hỏi GitHub khi mở và hiện tấm **Có bản mới** kèm phần "Có gì mới" (lấy từ lời nhắn commit), cộng thẻ nhắc ở màn hình chính cho đến khi cập nhật xong. Bấm **Cập nhật ngay**: app tải file APK về (có thanh tiến độ, mất mạng thì tải tiếp phần còn lại), kiểm tra đúng là bản mới hơn của chính app này rồi nhờ trình cài đặt của Android cài đè, **không mở trình duyệt, không cần vào GitHub**. Android luôn tự hỏi xác nhận một lần (nút Update/Cập nhật) và lần đầu cần bật quyền "Cài ứng dụng không rõ nguồn" cho Dịch game (app dẫn tới đúng trang cài đặt rồi tự cài tiếp khi quay lại). Bản mới phải ký cùng khóa với bản đang cài (xem Khóa ký) nên dữ liệu và cài đặt được giữ nguyên. App TV cũng có nút cập nhật chọn bằng remote. Chỉ cập nhật được từ bản có chức năng này trở đi; bản cũ hơn cần cài tay một lần. |
| Cài đặt › Giới thiệu và hỗ trợ | Xem phiên bản, **Kiểm tra bản mới** (mở đúng tấm cập nhật ở trên), **Báo lỗi / yêu cầu game** (mở GitHub Issues điền sẵn thông tin máy) |
| Hướng dẫn / Tính năng | Hướng dẫn 5 bước, danh sách tính năng |

Trong lúc chạy, thông báo có 3 nút: **Chỉnh/Khóa khung**, **Dịch menu**, **Vùng dịch**.

## Build trên GitHub

1. Tạo repo mới trên GitHub, đẩy **toàn bộ** thư mục này lên (nhớ cả thư mục ẩn `.github` và thư mục `keystore`).
   Dùng dòng lệnh: `git init && git add -A && git commit -m "Dịch game" && git branch -M main && git remote add origin <địa chỉ repo> && git push -u origin main`
2. Vào tab **Actions** › workflow **Build APK**. Nó chạy tự động mỗi lần đẩy code, hoặc bấm **Run workflow**.
3. Khi xong có 2 file APK:
   - mục **Artifacts** của lần chạy (`DichGame-APKs`), và
   - mục **Releases** của repo (khi đẩy lên nhánh `main`/`master` hoặc gắn tag `v*`), tải thẳng được bằng điện thoại.

| File | Dùng cho |
| --- | --- |
| `DichGame.apk` | Cài lên điện thoại Android (chip arm64, tức hầu hết điện thoại từ Android 8 trở lên). Đây là bản chính, có cả Remote Play trong app |
| `DichGame-TV.apk` | Cài lên Android TV |

Mỗi lần phát hành chỉ có hai file trên. Workflow tự xóa các bản `build-N` cũ, chỉ giữ 2 bản mới nhất. Tên hai file (`DichGame.apk`, `DichGame-TV.apk`) và tag `build-N` là thứ app dùng để tự tìm bản mới, đừng đổi. Cần bản debug hay bản cho chip 32-bit thì build tay bằng `gradle :app:assembleDebug` hoặc `:app:assembleRelease` (file nằm trong `app/build/outputs/apk/`).

Phát hành bản có số phiên bản: `git tag v2.0 && git push origin v2.0`.
Mỗi bản dựng trên nhánh `main` có số thứ tự (`build-N`), số này được ghi vào app (versionCode = 20 + N) để app biết có bản mới hơn khi bấm **Kiểm tra bản mới**. Cài đè lên bản cũ được vì cùng khóa ký.

### Khóa ký

Mặc định APK ký bằng khóa trong `keystore/dichgame.jks` (mật khẩu `dichgame123`). Khóa này nằm công khai trong repo, chỉ hợp để dùng cá nhân.
Muốn dùng khóa riêng: tạo 4 secrets trên GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), workflow sẽ tự dùng.
Lưu ý: đổi khóa thì phải gỡ app cũ rồi cài lại.

## LG webOS và Samsung Tizen (mã nguồn app TV mới, thử nghiệm)

`bash tools/prepare-tv-web.sh` chuẩn bị mã nguồn cài cho hai hệ điều hành; GitHub Actions `Build experimental LG and Samsung TV receivers` đóng gói được `.ipk` **thử nghiệm** cho LG và nguồn Tizen, chưa có `.wgt` ký theo TV. Hai app dùng IP điện thoại và mã ghép 8 số ở **Phụ đề trên TV → bật trang web phụ đề**; server hỗ trợ `/tv-pair` và `/tv-poll`. Samsung dùng API TVWindow/PiP thay vì app overlay; LG thử nghiệm nền trong suốt. Cần thử trên firmware TV thật, không cam kết hiện trên mọi HDMI.

**Xem [hướng dẫn triển khai LG / Samsung](docs/TV_WEBOS_TIZEN.md)**.

## Ghép TV

1. Cài `DichGame-TV.apk` lên TV (app **Downloader** trên TV, nhập đường dẫn tải file trong Releases; hoặc USB).
2. Mở **Dịch game TV**, cấp quyền "Hiển thị trên ứng dụng khác".
   Nếu TV không có menu này: `adb shell appops set com.dichgame.tv SYSTEM_ALERT_WINDOW allow`.
3. TV hiện địa chỉ IP và mã 8 số (đổi mỗi 60 giây).
4. Trên điện thoại: **Phụ đề trên TV** › **Tìm TV trong mạng** (hoặc nhập IP) › nhập mã › **Ghép TV** › **Gửi câu thử lên TV**.
5. Điện thoại và TV phải cùng Wi-Fi. Cổng 8765 (TCP) và 8766 (UDP, để tìm TV).
6. Khi bấm Bắt đầu chọn **Chơi trên TV** thì phụ đề (và giọng đọc, nếu bật "Đọc qua loa TV") được đẩy lên TV.

## Luồng dịch thông minh

Mỗi lượt quét chữ đi qua các bước (mã trong `app/src/main/java/com/dichgame/app/`, phần logic không phụ thuộc Android nên có unit test):

1. **Bỏ qua khung đứng yên** (`Ocr.thumbFine`/`sameStrict`): hình trong khung y hệt lần đọc trước thì không đọc lại, nhưng không bỏ khi đang chờ xác nhận một câu và cứ 12 lần lại đọc một lần.
2. **Lọc chữ rác** (`OcrGuard`): loại chữ trống, toàn ký hiệu/số, một chữ lặp, dãy phụ âm vô nghĩa, độ tin cậy ML Kit thấp.
3. **Tách người nói** (`DialogueSplitter`): nhận "Tên: lời", thẻ 【Tên】, tên đứng trên/bên trái khung thoại. Tên lạ chỉ được tin khi có trong thẻ nhân vật, có dấu hai chấm, hoặc đứng trên ít nhất hai câu khác nhau; chưa đủ căn cứ thì vẫn giữ làm câu riêng, không bỏ chữ, không ghép hai người.
4. **Lời nghe được** (`GameAudioAsrWorker` › `AsrGuard`): loại câu Whisper hay bịa khi gặp nhạc/tiếng súng/im lặng ("thanks for watching", nhãn [Music], câu lặp vô tận, sai chữ viết).
5. **Hợp nhất** (`SubtitleFusion`): chữ trên màn hình là nguồn chính; lời nghe được trùng thì bỏ, chưa có trên màn hình thì chờ ~1,3 giây để chữ xác nhận rồi mới hiện, màn hình không đọc được thì hiện luôn, chữ đọc dở thì ghép với phần nghe được (chỉ khi phần chồng đủ dài), chữ máy đánh chữ dài dần thì thay bản dở.
6. **Dịch** (`TextTranslator.translateFull`): gói Việt hoá (file đã nhập, khớp đúng rồi khớp gần đúng) › bộ nhớ đệm › AI có ngữ cảnh (tên game, thẻ nhân vật, người nói, câu trước) › dịch trên máy. Bản dịch AI qua `TranslationGuard`; hỏng thì dịch lại có lưu ý, vẫn hỏng thì lùi về dịch trên máy; còn nghi ngờ thì hiện dấu (?).
7. **Giữ nhịp** (`SubtitlePacer`): giữ mỗi dòng đủ thời gian đọc (~17 ký tự/giây, 1,4–8 giây), không thay dòng quá sớm, hàng chờ đầy thì gộp chứ không bỏ.
8. **Sửa bản dịch sai** (`RecentLines`, `CorrectActivity`): 40 câu vừa dịch nằm trong bộ nhớ (không ghi ra đĩa); bạn sửa rồi Lưu thì bản sửa vào gói Việt hoá của game và được dùng trước cả dịch AI ở lần gặp sau. Mở từ thông báo hoặc Cài đặt › Phụ đề.

Cài đặt › Phụ đề có chế độ hiệu năng, bỏ qua khi hình đứng yên, tự nhận game đổi ngôn ngữ, và xem **số đo lần dịch gần nhất** (số khung bỏ qua, chữ rác bị loại, câu trùng, độ trễ, pin).

**Kiểm thử:** `gradle :app:testDebugUnitTest` chạy các test logic, gồm `PipelineSimulationTest` mô phỏng buổi chơi theo kịch bản (phụ đề đều, phụ đề hiện trễ, không phụ đề, nói nhanh, nói chậm, nhạc xen kẽ, OCR sai...). Đó là kiểm tra **logic** với độ trễ giả định, không phải đo trên máy thật: tỉ lệ nhận đúng chữ, độ trễ thật, CPU, RAM, pin phụ thuộc máy và game, cần chơi thật rồi xem "Số đo lần dịch gần nhất". App không cam kết chính xác tuyệt đối.

## Áp file Việt hoá như chơi trên máy tính

Khi bạn tải về file chữ Việt hoá của một game, nhập vào hồ sơ của game đó (Kho game › chọn game › **Nhập file Việt hoá của game này**, hoặc Gói Việt hoá › Nhập và chia sẻ) thì lúc chơi, câu nào trên màn hình khớp một câu gốc trong file sẽ hiện đúng bản Việt hoá trong file, không qua bộ dịch (mã: `LocFormat.kt`, `Pack.kt`, `PackStore.importJoined`).

- **Một file** (nút "Từ file", app tự nhận dạng; mã: `Bilingual.kt`): file gói của app (.txt) hoặc file đã có cả hai ngôn ngữ ngay trong đó: bảng .csv/.tsv (cột gốc + cột Việt, có hay không có dòng tiêu đề, cột nào trước cũng được), .json (`{"câu gốc": "câu Việt"}`, mảng `[{"en":…,"vi":…}]`, hoặc hai bảng `{"en":{…},"vi":{…}}`), gettext .po và .mo, XLIFF, TMX, Ren'Py (.rpy), phụ đề .srt hai dòng mỗi mục, từng dòng "gốc | Việt" hoặc "gốc = Việt", dòng xen kẽ (một dòng gốc, một dòng Việt). Bên nào là tiếng Việt được nhận từ chính chữ (chữ đặc trưng như đ, ơ, ư, ạ, ề…), không cần đúng thứ tự cột; cột toàn mã khoá (`menu.start`) không bao giờ bị coi là câu gốc; hai ngôn ngữ không phải tiếng Việt (Anh + Pháp...) thì không nhập.
- **File .zip**: app mở ra, ghép file gốc với file Việt hoá cùng tên (khác nhau ở tên ngôn ngữ trong tên file hoặc thư mục, như `en/ui.json` + `vi/ui.json`), đọc luôn file có sẵn cả hai ngôn ngữ, và báo file nào chỉ có tiếng Việt hay không ghép được. Bỏ qua ảnh, âm thanh, chương trình. File .rar, .7z phải giải nén trước.
- **Ghép 2 file** (nút "Ghép 2 file"): chọn file chữ gốc rồi file đã Việt hoá. Đọc được .json (lồng nhau, mảng), .xml (strings.xml, .resx, `<tên name="khoá">chữ</tên>`), .strings (iOS), .properties/.ini/.lang, .srt, .csv/.tsv, và file mỗi dòng một câu. Ghép theo **khoá** (cùng khoá ở hai file); .srt ghép theo mốc thời gian, file từng dòng ghép theo **thứ tự** và chỉ khi hai file gần như cùng số dòng. Không ghép được thì app báo rõ lý do, không đoán.
- **Làm sạch**: bỏ thẻ định dạng (`<b>`, `<color=…>`), `\n` và `<br>` thành khoảng trắng. Bỏ qua và báo số lượng: câu chưa dịch (giống hệt gốc), dòng không có chữ, câu có biến số (`{tên}`, `%s`, `$x$`...) vì app không biết điền.
- **Khớp**: trước hết khớp đúng (không phân biệt hoa thường, dấu câu, chữ dễ nhầm như 0/O, 1/l/I). Không được thì khớp gần đúng cho câu từ 18 ký tự (câu CJK từ 8): chịu được chữ OCR đọc lệch vài ký tự, giống từ 92% (câu dưới 40 ký tự) hoặc 88% (câu dài hơn), **cùng các con số** ("Take 5" không bao giờ khớp "Take 6"), và bỏ qua nếu có hai câu khác nghĩa giống ngang nhau. Câu ngắn như "Yes." chỉ khớp đúng. Đo trong test: 60.000 câu dựng chỉ mục khoảng 1 giây, mỗi lần tra gần đúng dưới 1 ms (máy dựng test, không phải điện thoại).
- Bản bạn sửa trong "Sửa bản dịch sai" và file đã nhập đều nằm trong cùng bộ nhớ hồ sơ; câu nhập sau đè câu nhập trước. Mỗi hồ sơ lưu tối đa 100.000 câu.

**Không làm được:** bản vá nhị phân (.exe, .pak, .locres, .xdelta, .assets, .pkg, bản Việt hoá cài thẳng vào game trên PC/console) sửa trong file game, app không đọc được và không cài hộ; chọn những file này thì app nói rõ là loại gì và vì sao không nhập được, không im lặng bỏ qua. Cũng không đọc trực tiếp file Excel/Word/PDF (hãy xuất ra .csv) và không giải nén được trình cài đặt .exe tự giải nén. Câu có biến số bị bỏ qua. Chữ trong game bị vẽ thành hình (không phải chữ) thì vẫn phụ thuộc OCR. Việc nhận dạng các định dạng trên chỉ kiểm thử bằng dữ liệu mẫu tự dựng (bài thử tự động `BilingualTest`), chưa thử với file Việt hoá thật của cộng đồng, nên file có cấu trúc lạ vẫn có thể bị từ chối; khi đó app báo lý do.

## Ngôn ngữ đọc được

| Cách đọc | Ngôn ngữ |
| --- | --- |
| Trên máy (nhanh, offline, miễn phí) | Anh, Nhật, Hàn, Trung, Pháp, Đức, Tây Ban Nha, Bồ Đào Nha, Ý, Indonesia, Hà Lan, Ba Lan, Thổ Nhĩ Kỳ, Hindi, Thụy Điển, Séc, Romania, Hungary, Đan Mạch, Phần Lan, Na Uy, Mã Lai |
| Nhờ Google Gemini đọc ảnh (cần khóa Gemini miễn phí từ aistudio.google.com) | Nga, Ukraina, Thái, Ả Rập, Hy Lạp, Do Thái |

Với nhóm Gemini, chỉ ảnh phần màn hình cần đọc (khung phụ đề, hoặc vùng bạn chọn) được gửi cho Google, và chỉ khi bạn chọn các thứ tiếng này. Bản dịch sang tiếng Việt vẫn có thể chạy trên máy.
Lần đầu dùng mỗi thứ tiếng, app tải mô hình dịch nên cần mạng.

## Giới hạn hiện tại

- Gói Việt hoá nhập được từ file **.txt** (dạng của app), **.csv / .tsv** (cột câu gốc, cột câu Việt), **.json** và **.po**, hoặc ghép hai file chữ (gốc + Việt hoá: .json, .xml, .strings, .properties, .srt...), tự nhận bảng mã UTF-8, UTF-16 và Windows-1258, tối đa 24 MB mỗi file. Chỉ lấy cặp câu chữ: bản vá (mod) nhị phân sửa trong file game thì app không đọc được (xem "Áp file Việt hoá như chơi trên máy tính").
- **TV LG (thử nghiệm)**: ghép bằng giao thức điều khiển của LG (LG Connect Apps, WebSocket cổng 3001 hoặc 3000) và gửi phụ đề thành thông báo chữ của TV, nằm đè lên cả cổng HDMI. Đã kiểm thử với TV LG giả trên máy (bắt tay WebSocket, ghép nối, gửi thông báo, báo lỗi), **chưa thử trên TV LG thật**; nếu TV báo lỗi, app hiện nguyên văn lỗi đó.
- Đã có **mã nguồn thử nghiệm** ứng dụng cho TV LG (webOS) và Samsung (Tizen), nhưng chưa xác nhận tương thích HDMI/firmware; app Android TV là bản TV cài chính thức trong repo. Cài app Tizen cần chứng chỉ Samsung gắn với từng TV nên không làm sẵn gói chung được; điện thoại cũng không tự cài được. Trong app có hướng dẫn bật Developer Mode của LG và Samsung. Samsung: chiếu màn hình điện thoại hoặc dùng trang web phụ đề.
- Dịch game chỉ có bản Android, không cài hay ghép từ iPhone được; iPhone xem phụ đề được bằng trang web phụ đề ở trình duyệt.
- **Giấy phép Remote Play**: engine Chiaki-ng (`third_party/chiaki-ng`) theo **AGPL-3.0-only kèm ngoại lệ OpenSSL**. Bản APK có Remote Play vì vậy phải được phát hành theo AGPL-3.0 và giữ mã nguồn công khai; bản dựng với `-PnoNative` (dùng cho smoke test) không chứa engine này. Chưa có tệp LICENSE chung cho repo, chủ dự án cần chọn giấy phép AGPL-3.0 cho phần mã còn lại nếu muốn phát hành công khai.
- **Lỗi văng khi vào chơi (đã sửa)**: bản vá chuyển âm thanh Remote Play sang nhận dạng giọng nói trước đây truyền sai con trỏ cho bộ mở âm thanh của Chiaki, làm hỏng bộ nhớ ngay khi luồng âm thanh bắt đầu. Bản vá (`tools/patch-chiaki-audio-pcm.py`) nay có hai hàm riêng cho cấu hình và khung âm thanh, và tự dừng build nếu đăng ký sai. Chưa thử được với PS5 thật trong môi trường dựng ở đây (smoke test trên máy giả lập dùng `-PnoNative`, không có engine); cần bạn thử lại và, nếu vẫn văng, gửi nội dung hộp thoại báo lỗi hiện ở lần mở app kế tiếp.
- Dịch phụ đề trong màn chơi chạy trên luồng chính của app và bắt mọi lỗi; smoke test dùng `LiveDemoActivity` (chỉ bản debug) vẽ một câu phụ đề lên SurfaceView rồi kiểm tra chuỗi PixelCopy → OCR → dịch → hiện. Đây không phải video Remote Play thật: video giải mã bằng phần cứng của từng máy có thể khác.
- Remote Play tích hợp PS4/PS5 hiện ở trạng thái **thử nghiệm**. Chưa kiểm thử kết nối, hình ảnh, âm thanh và điều khiển trên đầy đủ model/firmware console; cần người dùng thử với máy thật và gửi log khi gặp lỗi. Bản arm64 có engine Chiaki-ng; các ABI khác không có engine Remote Play.
- Chưa có app cho **iOS, Windows, Mac**, và chưa có **kho Việt hoá tự cập nhật kèm bot gửi yêu cầu** (cần máy chủ). Thay vào đó có mục báo lỗi / yêu cầu game dẫn tới GitHub Issues.
- Giao diện chỉ có tiếng Việt.
- **Dịch chữ giao diện game** (menu, vật phẩm, kỹ năng, nhiệm vụ, mô tả, thông báo…): chạy trong màn chơi Remote Play bằng lớp phủ, có hai nút: **Dịch menu** (liên tục, tự cập nhật khi đổi màn) và **Dịch hết chữ** (dịch một lần mọi chữ trên khung hình, kể cả vùng phụ đề, giữ 12 giây rồi tự tắt hoặc tắt ngay khi cảnh đổi; các khối chữ nhiều thì xếp hàng dịch lần lượt chứ không bỏ sót đến lượt quét sau), đọc chữ cả khung hình (ML Kit, hoặc Gemini với chữ Nga/Thái/Ả Rập…), bỏ qua có chủ ý mã ID, số liệu, chỉ số viết tắt (HP, ATK…), biến và chữ đã là tiếng Việt, giữ nguyên mã giữ chỗ (`{0}`, `%s`, `<color=…>`), dịch theo gói Việt hoá và thuật ngữ của game, vẽ bản dịch đè đúng chỗ chữ gốc (viền vàng = còn nghi ngờ), giữ nút Dịch menu để xem chuỗi chưa dịch được. Đây là dịch lớp phủ trên hình, **không sửa dữ liệu của game**: game console không thể can thiệp dữ liệu ngôn ngữ; bản Việt hoá chuẩn nằm ở gói Việt hoá mà app nhập (Kho game). Chữ vẽ trên hình đồ hoạ, chữ cong, chữ quá nhỏ hoặc chữ động có thể đọc sai hoặc sót. Smoke test trên máy giả lập kiểm tra đọc chữ và lọc trên một menu vẽ sẵn bằng tiếng Anh; chưa kiểm thử với PS5 thật, game thật hay độ trễ trên máy thật. Dịch giao diện qua app ghi màn hình (CaptureService) chưa hỗ trợ.
- **Nghe âm thanh game** dùng Whisper Tiny offline chạy theo từng đoạn nói (không phải nhận dạng liên tục, nên câu hiện sau khi nhân vật nói xong). Chơi PS4/PS5 trong app: lấy âm thanh từ Remote Play. Game chạy trên điện thoại: tuỳ chọn thử nghiệm (mặc định tắt, Cài đặt › Nhận dạng lời thoại bằng âm thanh) thu âm thanh trong của máy (Android 10+, cần quyền ghi âm và model đã tải); game có quyền chặn kiểu thu này, khi đó app tự báo và (nếu bạn cho phép) chuyển sang micro, chất lượng kém hơn và có thể thu cả giọng bạn; không có quyền thì chỉ dịch bằng chữ trên màn hình. Phần này **chưa được kiểm thử trên máy thật** (chỉ biên dịch và kiểm thử logic), cần người dùng thử và báo lại.
- **Tự nhận game đổi ngôn ngữ** chỉ chuyển giữa các ngôn ngữ chữ Latin (Anh, Pháp, Đức, Tây Ban Nha...) theo chữ trên màn hình. Đổi giữa Anh, Nhật, Hàn, Trung, Nga... vẫn phải chọn tay, vì mỗi thứ dùng bộ đọc chữ khác nhau.
- Tên người nói chỉ được nhận khi chắc chắn (xem Luồng dịch thông minh); game đặt tên người nói theo cách lạ có thể hiện như một câu riêng chứ không phải nhãn.
- Chữ Nga, Ukraina, Thái, Ả Rập, Hy Lạp, Do Thái đọc chậm hơn (khoảng 1–3 giây mỗi câu) vì phải nhờ Gemini đọc ảnh; ở chế độ này tắt "Dịch menu tự động" (vẫn dùng được nút "Dịch menu" và "Vùng dịch").
- Giọng của máy là các giọng tiếng Việt cài sẵn (Cài đặt › Ngôn ngữ và nhập › Chuyển văn bản thành giọng nói). Giọng AI dùng chung khóa Google Gemini với bộ dịch, tên model mặc định `gemini-3.8-flash-tts` (đổi được trong app nếu Google đổi tên).
- **Giọng offline**: dùng thư viện sherpa-onnx (tự tải về lúc build, nằm ở `app/libs/`, không để trong git) và giọng Piper VAIS-1000 tải thêm trong app (khoảng 65 MB). Khi bật "Đọc qua loa TV", điện thoại tự tạo âm thanh offline rồi gửi sang app TV phát bằng loa TV (cần cài app TV bản mới; TV cũ thì đọc bằng giọng của TV). Chưa có giọng nam, và mới có một giọng. Vì sherpa-onnx từ bản 2.0 không còn eSpeak, app tự đổi chữ sang phiên âm bằng bảng `app/src/main/assets/vi_phon.txt`, tạo bởi `tools/gen_vi_phon.py` (cần eSpeak NG ở máy dựng bảng, xem đầu file). Số và ký hiệu được đọc thành chữ; tên riêng, chữ nước ngoài được đọc theo kiểu tiếng Việt nên có thể nghe lạ. Smoke test trên máy giả lập có tự tải giọng và đọc thử một câu (SelfTest.kt) để chắc rằng thư viện chạy được; chất lượng nghe thật vẫn cần bạn nghe trên máy. Nghe không ổn thì dùng mục "Báo lỗi" trong app.
- "Đọc theo cảm xúc" dùng thẻ cảm xúc do AI gắn khi dịch bằng AI; dịch trên máy thì đoán bằng từ khóa và dấu câu nên kém chính xác hơn.
- "Dịch video trên web" phụ thuộc trang web có để lộ phụ đề cho trình duyệt hay không; trang vẽ phụ đề lên canvas thì không đọc được bằng CC, khi đó đổi sang OCR và kéo khung vào chỗ phụ đề. Video có DRM có thể chụp ra màn đen, OCR sẽ không đọc được.
- **Ảnh bìa** chỉ có cho game có trên Steam (khớp theo tên tiếng Anh); game chỉ có trên PlayStation, Xbox, Switch dùng ảnh vẽ sẵn. Ảnh không đóng gói trong app, thuộc về nhà phát hành.
- Không có phần trả phí, không có mạng xã hội.
- **Bản vá Việt hoá (mod) của cộng đồng** sửa chữ trong file game, mỗi nhóm giữ quyền riêng: app chỉ dẫn tới trang gốc, không tải hộ, không cài vào game. Gói Việt hoá trong app là dữ liệu cho phụ đề trên màn hình, soạn bởi người làm app, không chép từ các trang đó.
