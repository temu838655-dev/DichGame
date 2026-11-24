# Tự động ghép kho Việt hóa theo ID (thử nghiệm trên nền build-1251)

Công cụ Python 3.8+ `tools/build_viet_pack.py` không cần thư viện ngoài, không gọi API AI và không đổi mã Android. Chỉ dùng bản dịch mà bạn có quyền sử dụng và phân phối. Không tự tải bản vá từ bên thứ ba, không vượt DRM.

## Đầu vào
Hai file CSV UTF-8 có ID ổn định của cùng game, cùng phiên bản và cùng bảng ngôn ngữ; nếu ID chỉ duy nhất trong từng bảng, hãy ghép namespace bảng vào ID trước.

```csv
id,source
menu.start,Start game
menu.quit,Quit game
```

```csv
id,vi
menu.quit,Thoát trò chơi
menu.start,Bắt đầu trò chơi
```

## Tạo kho
```sh
python3 tools/build_viet_pack.py --source en.csv --vi vi.csv --game "Example Game" --version 1 --url "https://YOUR-HOST/example/v1/pack.csv" --output example-v1 --authorized
python3 tools/build_viet_pack.py --self-test
```

Công cụ ghép theo ID, lọc trùng, loại các câu có nhiều bản dịch khác nhau sau chuẩn hóa (app hiện tra kho bằng câu gốc, chưa phân biệt ID trong gói Cloud). Không ghép theo vị trí dòng. Mặc định từ chối file thiếu ID, dư ID hoặc xung đột; `--allow-partial` chỉ xuất các cặp rõ ràng và báo số lượng bị loại. Giới hạn 100.000 câu / 24 MB. Xuất `pack.csv`, `pack.dichgame.json` (manifest schema 1, kích thước, SHA-256) và `report.json` (thống kê). Thư mục đích phải chưa tồn tại.

## Đưa lên Cloud
Kiểm tra lại nguồn, chất lượng dịch, biến định dạng, tên game và số lượng trước khi phân phối. Đưa CSV lên URL HTTPS đã khai báo, xác minh SHA-256 rồi mới đưa manifest lên. Trên app chọn đúng game, nhập URL manifest. Khi cập nhật, tăng version, giữ file CSV theo đường dẫn riêng từng version và chỉ thay manifest sau khi upload CSV thành công. Giữ kho cũ để phục hồi. Không cần đổi APK hoặc khóa ký nếu bản 1251 đã hỗ trợ manifest.

Công cụ này không trích xuất file nhị phân game, không dịch câu còn thiếu, không xác minh chất lượng ngôn ngữ, không upload lên Cloudflare và không gửi dữ liệu người chơi. Không phải mọi câu xuất ra đều được app tra cứu (ví dụ câu quá ngắn). Không chứa dữ liệu Việt hóa của bên thứ ba.
