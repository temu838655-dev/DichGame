# Ứng dụng nhận phụ đề TV LG webOS / Samsung Tizen (thử nghiệm)

Mã nguồn gốc: `tv-webos/`, `tv-tizen/`, `tv-web-common/`.
Hai app TV này **chỉ nhận và hiển thị chữ dịch**. OCR, nhận diện ngôn ngữ, AI và giọng đọc hiện thực hiện trên điện thoại Android. Chưa có chức năng nhận âm thanh tiếng Việt vào loa TV LG/Samsung trong bộ nhận web này.

## 1. Bật dịch và ghép TV

1. TV và điện thoại Android dùng cùng Wi-Fi/LAN tin cậy, không mở port 8780 ra Internet.
2. Trên điện thoại vào **Phụ đề trên TV → TV LG / TV Samsung → Bật trang web phụ đề**.
3. Ghi IP điện thoại (vd. `192.168.1.12`) và **mã ghép 8 số** hiển thị.
4. Cài ứng dụng TV trên hệ điều hành tương ứng. Mở app, nhập IP điện thoại và mã, bấm **Kết nối**.
5. Bắt đầu dịch trên điện thoại. App TV hỏi `/tv-pair` rồi nhận chữ bằng long-poll `/tv-poll`, tự nối lại khi mất mạng.
6. Khi ngắt máy chủ phụ đề trên điện thoại và bật lại, mã/token phiên sẽ đổi và TV cần ghép lại. Nếu app bị phủ bởi ứng dụng khác, thử mở lại và nhấn phím **LÊN** để hiện bảng kết nối.

Bộ nhận không lưu nội dung phụ đề; nó chỉ nhớ IP và token trong bộ nhớ cục bộ của ứng dụng TV. Mã ghép có giới hạn 8 lần thử/phút theo IP. Kết nối LAN HTTP **chưa mã hóa TLS**. Trang phụ đề cũ `/poll` khi bạn chủ động bật vẫn có thể đọc từ trình duyệt trong mạng nhà; không dùng Wi-Fi công cộng.

## 2. Đóng gói từ mã nguồn

Chạy từ thư mục gốc của repository:

```sh
bash tools/prepare-tv-web.sh
```

Lệnh tạo thư mục `dist-tv/webos` và `dist-tv/tizen` với `index.html`, `receiver.js`, `styles.css`, icon, manifest. Có thêm `*-SOURCE.zip`: **không phải ứng dụng có thể cài trực tiếp**.

### LG webOS (`.ipk`)

- Cài công cụ chính thức của LG: `npm install -g @webos-tools/cli`.
- Đóng gói: `ares-package dist-tv/webos -o dist-tv`.
- Bật **Developer Mode** và **Key Server** trên TV LG, cấu hình TV qua `ares-setup-device`.
- Cài gói IPK nhận được bằng `ares-install -d <ten-device> dist-tv/<file>.ipk`, rồi `ares-launch -d <ten-device> com.dichgame.tv.webos`.
- Trên TV đang ở cổng HDMI có hình game, mở app bằng lệnh từ máy tính trước khi chơi. Giữ app chạy khi chuyển về game.

**Hạn chế:** `transparent: true` và `defaultWindowType: overlay` trong appinfo chỉ là thử nghiệm. Có máy/firmware chặn lớp phủ HDMI hoặc không cho app thường sử dụng cửa sổ overlay. Bản `.ipk` đóng gói thành công **không chứng minh đã phủ chữ lên HDMI**. Không khẳng định đủ điều kiện phát hành LG Content Store trước khi LG xác nhận chính sách.

### Samsung Tizen (`.wgt`)

- Cài **Tizen Studio + Samsung TV Extension + Certificate Manager**.
- Bật Developer Mode trên TV Samsung. Thêm IP máy tính dùng để cài, khởi động lại và kết nối bằng Device Manager.
- Nhập `dist-tv/tizen` làm TV Web App, xác nhận package ID và ứng dụng trong `config.xml` phù hợp chứng chỉ.
- Tạo **Samsung Certificate Profile** có DUID TV của bạn. Build và ký `.wgt` bằng Tizen Studio; không đưa file `.p12`, mật khẩu hay DUID vào repo hoặc GitHub Actions.
- Cài gói lên TV từ Tizen Studio, mở app khi PS5 đã bật. TVWindow lấy hình HDMI bằng PiP `BEHIND` và lớp HTML vẽ phụ đề phía trước.
- Nếu có nhiều cổng HDMI, chọn nguồn trong bảng app. Nếu TV tự nhảy khỏi app, kiểm tra HDMI-CEC/Anynet+ trên TV hoặc HDMI Device Link trên PS5.

**Hạn chế:** Samsung không hỗ trợ app overlay trên ứng dụng khác; đường chính thức là TVWindow Picture-in-Picture. TVWindow/HDMI đầu vào, mức phân giải, âm thanh và API có thể khác theo model/firmware. Chưa có `.wgt` ký sẵn vì chứng chỉ phải tạo bằng tài khoản và TV mục tiêu.

## 3. Kiểm thử cần làm trên thiết bị thật

- Đúng IP, mã sai và khóa sau 8 lần sai; mã hết hạn khi restart server; không cho token sai đọc chữ.
- Đặt TV ở HDMI game, bật app: hình HDMI còn đủ khung và chữ nằm đúng phía dưới.
- Thử các game phụ đề dài, chữ Việt có dấu, màu nền khác nhau, xoay/tắt màn điện thoại, bật/tắt dịch.
- Cắm/rút HDMI, chuyển cổng, tắt/bật TV, thử TV mất mạng và tự nối lại.
- Nếu hình đen khi app mở, lấy **model TV, phiên bản firmware/webOS/Tizen, log thiết bị**, không kết luận phần dịch bị hỏng.

## Tài liệu API chính thức

- Samsung PiP overlay: https://developer.samsung.com/smarttv/develop/guides/pip-picture-in-picture/showing-pip-overlays.html
- Samsung TVWindow: https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/tvwindow-api.html
- Samsung cấp chứng chỉ: https://developer.samsung.com/smarttv/develop/tools/additional-tools/vscode/creating-certificates.html
- LG appinfo: https://webostv.developer.lge.com/develop/references/appinfo-json
- LG CLI: https://webostv.developer.lge.com/develop/tools/cli-installation
