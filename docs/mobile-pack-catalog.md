# Danh mục dữ liệu Việt hóa dùng chung cho PS5, PS4 và Xbox

DịchGame chỉ hỗ trợ **dịch phụ đề khi chơi trên PS5, PS4 và Xbox**. Nền tảng ghi trong metadata của một bản Việt hóa là **nơi xuất phát của dữ liệu tham khảo**, không phải nền tảng đích của DịchGame.

Ví dụ: game có bản Việt hóa PC được cấp phép trích xuất cặp câu gốc–tiếng Việt. DịchGame có thể dùng bộ câu đó để tra phụ đề OCR khi người chơi đang chơi cùng game trên PS5, PS4 hoặc Xbox. Không cần có patch riêng cho console.

## Danh mục game (schema 2)

Mỗi game có một bộ dữ liệu hợp nhất, có thể được tổng hợp từ nhiều nguồn hợp lệ. `source_platform` chỉ ghi nguồn gốc dữ liệu (ví dụ PC); ứng dụng không lọc gói theo nền tảng console đang chơi.

```json
{
  "schema": 2,
  "games": [
    {
      "game": "Example Game",
      "source_platform": "PC",
      "manifest": "https://packs.example.org/example.dichgame.json"
    }
  ]
}
```

Mỗi game chỉ có một manifest trong danh mục. Nếu có nhiều bản Việt hóa/nguồn, hãy hợp nhất các câu dịch vào một bộ dữ liệu đã kiểm tra trùng lặp, nguồn gốc và quyền sử dụng. Không tạo nhiều entry trùng game gây mơ hồ.

## Manifest gói

```json
{
  "schema": 1,
  "game": "Example Game",
  "version": 3,
  "url": "https://packs.example.org/example.csv",
  "bytes": 12345,
  "sha256": "64-lowercase-hex-characters"
}
```

CSV phải có tiêu đề `source,vi,game`. Ứng dụng kiểm tra game, kích thước và SHA-256 trước khi nhập. Phiên bản thấp hơn hoặc cùng phiên bản nhưng hash khác bị từ chối; dữ liệu offline hiện có được giữ nguyên. Bản người chơi tự sửa luôn được ưu tiên hơn bản Cloud.

## Luồng dịch trên console

1. Người chơi chọn game và console đích: PS5, PS4 hoặc Xbox.
2. DịchGame tìm một bộ dữ liệu theo game, không theo console.
3. Dữ liệu có thể đến từ bản Việt hóa PC, phụ đề cộng đồng hoặc đóng góp được phép sử dụng; giữ lại nguồn, tác giả, giấy phép và phiên bản.
4. OCR đọc phụ đề từ luồng hình ảnh console, chuẩn hóa và chờ câu ổn định.
5. Tra câu gốc trong bộ dữ liệu chung và bộ nhớ theo game/ngữ cảnh.
6. Chỉ khi không tìm thấy mới gọi AI; kết quả AI được lưu ở trạng thái chưa xác minh để tái sử dụng.

## Quyền và an toàn

- Chỉ nhập/trích xuất hoặc đồng bộ nội dung khi chủ nguồn cho phép; metadata công khai không đồng nghĩa với quyền sao chép bản dịch.
- HTTPS bắt buộc; dùng link trực tiếp tới manifest `.dichgame.json`.
- Ghi rõ nguồn, tác giả, giấy phép và phiên bản trong hồ sơ phát hành.
- Link trang cộng đồng như The Red Team vẫn là link-only cho tới khi có API hoặc quyền tích hợp được xác nhận; ứng dụng không scrape, không mirror và không tự tải patch từ các trang đó.
- Nếu dữ liệu đến từ bản PC, ghi rõ build/phiên bản PC gốc và trạng thái kiểm tra câu dịch. Hash xác minh tính toàn vẹn file, không tự chứng minh quyền phân phối.
- DịchGame không cài patch PC vào PS5/PS4/Xbox và không sửa file game console; app chỉ tra cứu/dịch phụ đề qua luồng capture/Remote Play/overlay phù hợp.
