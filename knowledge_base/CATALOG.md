# Kho game và nguồn Việt hóa

## Mục tiêu

DịchGame chỉ hỗ trợ dịch phụ đề khi chơi trên **PS5, PS4 và Xbox**. Kho dữ liệu lưu cặp câu gốc–tiếng Việt theo game và ngữ cảnh. Nền tảng ghi trong metadata (ví dụ PC) là **nguồn xuất phát của dữ liệu**, không phải nền tảng đích. Bản Việt hóa PC được phép trích xuất có thể cung cấp câu dịch tham khảo cho cùng game đang chơi trên PS5, PS4 hoặc Xbox.

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
    "source_platform": "PC",
    "target_platforms": ["PS5", "PS4", "XBOX"],
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

Trường `source_platform` ghi **nền tảng của bản Việt hóa nguồn** (ví dụ PC/Steam); `target_platforms` ghi các console được phép sử dụng dữ liệu đó. Nếu bỏ qua `target_platforms`, mặc định dữ liệu được khai báo phục vụ PS5, PS4 và Xbox. Bộ lọc console chỉ kiểm tra danh sách đích, không loại dữ liệu chỉ vì nguồn là PC/Steam. Trường `platform` cũ vẫn được chấp nhận như bí danh tương thích ngược. `download_permitted` chỉ được bật nếu registry của nguồn cũng cho phép tải nội dung.

## Chạy

```bash
python -m unittest discover -s knowledge_base/tests -v
python -m knowledge_base.catalog_cli --db data/catalog.sqlite --registry knowledge_base/catalog_sources.json --feed approved-feed.json --confirm-authorized
```

Feed không được nhập nếu thiếu xác nhận quyền, nguồn chưa đăng ký, nguồn là link-only, nền tảng không hợp lệ hoặc giấy phép không rõ. Import lặp lại cùng release không tạo bản ghi trùng.

## Danh mục Cloud cho ứng dụng Android

Danh mục HTTPS theo game, độc lập với console đích, manifest CSV có hash và quy tắc không hạ phiên bản được mô tả tại [mobile-pack-catalog.md](../docs/mobile-pack-catalog.md).

## Quy trình áp dụng trong game

1. Người chơi chọn game và console đích: PS5, PS4 hoặc Xbox.
2. Kho tìm dữ liệu theo game/ngữ cảnh; dữ liệu nguồn có thể xuất phát từ bản Việt hóa PC.
3. Nếu có gói dữ liệu được phép đồng bộ: kiểm tra quyền, hash, game build nguồn và chất lượng câu dịch trước khi đưa vào bộ nhớ tra cứu; không cài patch PC vào console.
4. Nếu chỉ có trang nguồn: mở trang gốc để người dùng tự xem hướng dẫn/tải theo điều khoản tác giả.
5. Khi không có câu dịch phù hợp, OCR ổn định văn bản rồi tra translation memory theo game/ngữ cảnh; chỉ sau đó mới gọi AI. AI output lưu ở trạng thái chưa xác minh.

## Giới hạn hiện tại

Đây là lớp registry + nhập metadata feed được cấp quyền, chưa phải dịch vụ crawler, API đồng bộ nền, kho patch, installer PC hay pipeline OCR đầu-cuối. Console vẫn cần luồng capture/Remote Play/overlay; app điện thoại không thể tự sửa file game PS5/PS4/Xbox.
