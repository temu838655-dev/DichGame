# Gói Việt hóa qua Cloudflare hoặc máy chủ HTTPS

Chọn đúng game trong Kho game, chọn Từ link và nhập URL file có hậu tố `.dichgame.json`. Chỉ sử dụng bản dịch có quyền phân phối.

Manifest schema 1:

```json
{"schema":1,"game":"GTA VI","version":1,"url":"https://YOUR_HOST/gta6.csv","bytes":1234,"sha256":"REPLACE_WITH_64_HEX_DIGITS"}
```

File CSV phải có tiêu đề `source,vi,game` và mỗi dòng có đúng một game. `bytes` là kích thước file thực, `sha256` là SHA-256 của chính file đó. Tạo từ file CSV đã xuất bằng `wc -c gta6.csv` và `sha256sum gta6.csv`. Đặt file và manifest ở URL HTTPS trực tiếp (ví dụ R2 public/custom domain hoặc Pages), không qua trang HTML hay redirect. App kiểm tra game, kích thước, mã SHA-256 rồi mới lưu vào kho offline. Các bản dịch đã lưu trước đó được ưu tiên giữ; không tự ghi đè. Giới hạn 24 MB mỗi file CSV và 100.000 câu trong kho. Nếu tải lỗi, kho cũ không đổi.

SHA-256 kiểm tra tính toàn vẹn so với manifest, không thay thế chữ ký số hoặc xác thực nhà phát hành. Chưa có đồng bộ hai chiều, tự động cập nhật nền hoặc máy chủ được triển khai bởi thay đổi này.
