# Kho game và nguồn Việt hóa

## Mục tiêu

Kho game lưu thông tin game và từng bản Việt hóa theo **nền tảng, khu vực, phiên bản game, phiên bản bản dịch, tác giả, nguồn và giấy phép**. Một game có thể có nhiều bản phát hành khác nhau; không dùng một gói PS5 thay cho PC/PS4/Switch hoặc ngược lại.

## Quy tắc kết nối nguồn

Danh sách nguồn được khai báo trong `catalog_sources.json`. Mỗi nguồn có một chế độ:

- `official_api`: API chính thức và được phép tích hợp.
- `authorized_feed`: feed metadata do chủ nguồn cho phép.
- `manual`: quản trị viên nhập và duyệt metadata.
- `link_only`: chỉ mở trang nguồn gốc, không tự thu thập hoặc tải nội dung.

**The Red Team hiện được cấu hình `link_only`**. DịchGame lưu liên kết tới trang gốc; chưa có API công khai hoặc quyền tích hợp tự động được xác nhận trong cấu hình này. Vì vậy không scrape trang, không tự tải patch, không mirror/rehost file. Khi có thỏa thuận hoặc API được cấp phép, quản trị viên mới đổi cấu hình nguồn và cung cấp adapter phù hợp.

## Định dạng feed metadata được cấp quyền

Feed UTF-8 JSON có cấu trúc:

```json
{
  "source_id": "authorized-source",
  "entries": [{
    "game_id": "publisher-or-catalog-id",
    "title": "Game title",
    "platform": "PC",
    "region": "Global",
    "edition": "Standard",
    "game_version": "1.2.3",
    "translation_version": "1.0",
    "author_name": "Translator",
    "source_url": "https://source.example/game",
    "license_id": "PERMISSION-GRANTED",
    "license_url": "https://source.example/terms",
    "attribution": "Required credit",
    "download_permitted": false,
    "updated_at": "2026-11-26T00:00:00Z"
  }]
}
```

`download_permitted` chỉ được bật nếu registry của nguồn cũng cho phép tải nội dung. Hiện module chỉ nhập metadata và đường dẫn; chưa tải hoặc cài patch.

## Chạy

```bash
python -m unittest discover -s knowledge_base/tests -v
python -m knowledge_base.catalog_cli --db data/catalog.sqlite --registry knowledge_base/catalog_sources.json --feed approved-feed.json --confirm-authorized
```

Feed không được nhập nếu thiếu xác nhận quyền, nguồn chưa đăng ký, nguồn là link-only, nền tảng không hợp lệ hoặc giấy phép không rõ. Import lặp lại cùng release không tạo bản ghi trùng.

## Danh mục Cloud cho ứng dụng Android

Định dạng danh mục HTTPS theo tên game + nền tảng, manifest CSV có hash và quy tắc không hạ phiên bản được mô tả tại [mobile-pack-catalog.md](../docs/mobile-pack-catalog.md). Đây là định dạng phân phối riêng của Android, không phải feed metadata của kho Python.

## Quy trình áp dụng trong game

1. Người chơi chọn game và nền tảng.
2. Kho tìm bản dịch đúng platform/region/edition/game version.
3. Nếu có gói được phép tải: kiểm tra quyền, hash và tính tương thích trước khi cài (cài đặt patch là bước riêng, cần người dùng đồng ý, sao lưu và có rollback).
4. Nếu chỉ có trang nguồn: mở trang gốc để người dùng tự xem hướng dẫn/tải theo điều khoản tác giả.
5. Khi không có câu dịch phù hợp, OCR ổn định văn bản rồi tra translation memory theo game/ngữ cảnh; chỉ sau đó mới gọi AI. AI output lưu ở trạng thái chưa xác minh.

## Giới hạn hiện tại

Đây là lớp registry + nhập metadata feed được cấp quyền, chưa phải dịch vụ crawler, API đồng bộ nền, kho patch, installer PC hay pipeline OCR đầu-cuối. Console locked platforms vẫn cần luồng capture/Remote Play/overlay; app điện thoại không thể tự sửa file game PS5/Xbox/Switch.
