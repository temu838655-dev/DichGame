# Gói Việt hóa qua Cloudflare hoặc máy chủ HTTPS

Chọn đúng game trong Kho game, chọn Từ link và nhập URL file có hậu tố `.dichgame.json`. Chỉ sử dụng bản dịch có quyền phân phối.

Manifest schema 1:

```json
{"schema":1,"game":"GTA VI","version":1,"url":"https://YOUR_HOST/gta6.csv","bytes":1234,"sha256":"REPLACE_WITH_64_HEX_DIGITS"}
```

File CSV phải có tiêu đề `source,vi,game` và mỗi dòng có đúng một game. `bytes` là kích thước file thực, `sha256` là SHA-256 của chính file đó. Tạo từ file CSV đã xuất bằng `wc -c gta6.csv` và `sha256sum gta6.csv`. Đặt file và manifest ở URL HTTPS trực tiếp (ví dụ R2 public/custom domain hoặc Pages), không qua trang HTML hay redirect. App kiểm tra game, kích thước, mã SHA-256 rồi mới lưu vào kho offline. Các bản dịch đã lưu trước đó được ưu tiên giữ; không tự ghi đè. Giới hạn 24 MB mỗi file CSV và 100.000 câu trong kho. Nếu tải lỗi, kho cũ không đổi.

SHA-256 kiểm tra tính toàn vẹn so với manifest, không thay thế chữ ký số hoặc xác thực nhà phát hành. Chưa có đồng bộ hai chiều, tự động cập nhật nền hoặc máy chủ được triển khai bởi thay đổi này.

## Kho Cloud theo phiên bản
Từ bản cập nhật này, gói Cloud lưu riêng theo game/hồ sơ và thay thế nguyên tử khi tải phiên bản mới hơn. Cùng phiên bản và SHA-256 thì không tải lại CSV; phiên bản cũ hơn hoặc cùng phiên bản nhưng khác nội dung bị từ chối. File nhập tay luôn ưu tiên hơn Cloud; xuất CSV gồm cả hai lớp. Gói Cloud đã nhập bằng phiên bản cũ nằm trong kho cá nhân và không tự di chuyển để tránh làm mất bản sửa tay. Nếu nhà cung cấp đổi nguồn, cần xóa kho hiện có hoặc dùng hồ sơ riêng, sau khi sao lưu. Chưa có tải tự động hoặc upload dữ liệu cá nhân lên Cloudflare.

## Kiểm tra cập nhật ngay trong app
Thẻ Kho Cloud · offline hiển thị phiên bản đã lưu. Sau lần nhập manifest thành công, app nhớ URL manifest trong kho nội bộ, cho phép kiểm tra bản mới bằng một nút. Không tự gửi bản dịch cá nhân lên server. Có thể xóa riêng lớp Cloud mà không xóa bản nhập tay. Đổi nguồn Cloud cần sao lưu và xóa riêng kho Cloud trước. Manifest cũ chưa lưu URL cần nhập lại một lần. Không hỗ trợ cập nhật định kỳ trong nền ở thay đổi này.

## Tạo danh mục và cập nhật tự động (bản mới)

Tạo file spec.json dạng mảng: [{"game":"Game A","id":"game-a","version":1,"csv":"game-a.csv"}]. Chạy từ thư mục repo:

```sh
python3 tools/build_cloud_catalog.py spec.json https://YOUR_HOST packs-out
python3 -m unittest discover -s tools -p test_build_cloud_catalog.py
```

Thư mục đầu ra phải chưa tồn tại. Chỉ xuất bản khi lệnh thành công. Đưa CSV có tên chứa hash lên R2 trước, rồi manifest và cuối cùng catalog.json. Giữ CSV phiên bản cũ để lượt tải đang chạy không bị lỗi. Dùng HTTPS trực tiếp, không redirect; cấu hình cache ngắn cho manifest và catalog. Chỉ xuất bản bản dịch có quyền phân phối. Không đưa dữ liệu cá nhân hoặc khóa API vào kho công khai.

Trong app bật tự cập nhật và nhập URL HTTPS catalog.json. App kiểm tra khoảng 30 phút một lần trong phiên dịch đang hoạt động; không upload dữ liệu cá nhân. Xóa kho Cloud sẽ chặn tự tải lại cho đến khi nhập thủ công thành công. Nguồn đã lưu được ưu tiên; đổi nhà cung cấp cần xóa kho cũ rồi nhập nguồn mới. Các mô tả cũ phía trên về chưa có tự cập nhật áp dụng cho phiên bản trước. Chưa triển khai tài nguyên Cloudflare thật.
