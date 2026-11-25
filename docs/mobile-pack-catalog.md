# Danh mục gói Cloud theo nền tảng (Android)

Ứng dụng Android tự kiểm tra danh mục HTTPS khi bật **Tự kiểm tra kho Cloud**. Danh mục chỉ trỏ tới manifest của gói; manifest lại trỏ tới CSV có SHA-256. Không nhúng hoặc sao chép bản vá của cộng đồng.

## Schema 2 (khuyến nghị)

Mỗi entry phải khai báo nền tảng cụ thể. DịchGame chỉ chọn entry khớp **tên game + nền tảng**; không dùng gói PC làm dự phòng cho PS5, PS4, Xbox hoặc Switch.

```json
{
  "schema": 2,
  "games": [
    {
      "game": "Example Game",
      "platform": "PC",
      "manifest": "https://packs.example.org/example-pc.dichgame.json"
    },
    {
      "game": "Example Game",
      "platform": "PS5",
      "manifest": "https://packs.example.org/example-ps5.dichgame.json"
    }
  ]
}
```

Giá trị platform hợp lệ: `PC`, `STEAM`, `PS5`, `PS4`, `XBOX`, `SWITCH`, `ANDROID`, `IOS`. Không khai báo hai entry trùng tên game và nền tảng.

## Manifest gói

```json
{
  "schema": 1,
  "game": "Example Game",
  "version": 3,
  "url": "https://packs.example.org/example-ps5.csv",
  "bytes": 12345,
  "sha256": "64-lowercase-hex-characters"
}
```

CSV phải có tiêu đề `source,vi,game`. Ứng dụng kiểm tra game, kích thước và SHA-256 trước khi nhập. Phiên bản thấp hơn hoặc cùng phiên bản nhưng hash khác bị từ chối; dữ liệu offline hiện có được giữ nguyên. Bản người chơi tự sửa luôn được ưu tiên hơn bản Cloud.

## Schema 1 cũ

Schema 1 không có trường `platform` chỉ được đọc khi gọi tra cứu không kèm nền tảng (chế độ tương thích cũ). Tự động cập nhật trong phiên chơi luôn truyền nền tảng đã chọn, vì vậy entry schema 1 không có platform sẽ không được tự động chọn. Hãy nâng danh mục lên schema 2.

## Quyền và an toàn

- Chỉ đưa vào danh mục các gói mà chủ nguồn cho phép phân phối/đồng bộ theo điều khoản của họ.
- HTTPS bắt buộc; dùng link trực tiếp tới manifest `.dichgame.json`.
- Ghi rõ nguồn, tác giả, giấy phép và phiên bản trong hồ sơ phát hành của bạn.
- Link trang web cộng đồng như The Red Team vẫn là link-only cho tới khi có API hoặc quyền tích hợp bằng văn bản; ứng dụng không scrape, không mirror và không tự tải patch từ các trang đó.
- Chọn đúng phiên bản game, vùng, edition và platform trước khi phát hành. Hash bảo vệ tính toàn vẹn file, không tự chứng minh quyền phân phối.
