# Đóng góp bản dịch cộng đồng trong Android

## Trạng thái hiện tại

Màn hình **Gói Việt hoá → Đóng góp cộng đồng** cho phép người chơi:

- Soạn bản dịch với câu gốc, tiếng Việt và tên người đóng góp tùy chọn.
- Lưu bản nháp riêng theo hồ sơ và game.
- Ghi nhận báo lỗi với câu gốc và lý do.
- Xem lại hoặc xóa từng bản nháp/báo lỗi.

Dữ liệu hiện được lưu cục bộ trong SharedPreferences của ứng dụng. Mỗi hồ sơ/game được lọc riêng; có giới hạn số lượng và độ dài đầu vào; các bản ghi lỗi định dạng được bỏ qua khi đọc.

## Giới hạn quan trọng

- Nút **Lưu bản nháp** không gửi dữ liệu lên Internet.
- Báo lỗi chỉ được lưu trên thiết bị, chưa vào hàng đợi kiểm duyệt máy chủ.
- Bản nháp không được trộn vào PackStore, Cloud pack hoặc bộ nhớ dịch.
- Không bản ghi nào được gắn trạng thái VERIFIED hay được coi là đã kiểm duyệt.
- Gỡ ứng dụng hoặc xóa dữ liệu ứng dụng có thể làm mất các bản nháp/báo lỗi này.

Đây là lớp giao diện và lưu nháp an toàn trong khi backend cộng đồng chưa được triển khai và triển khai thật. Không hiển thị thông báo "đã gửi" khi chưa có xác nhận từ máy chủ.

## Điều kiện để mở gửi cộng đồng thật

1. API HTTPS có xác thực người đóng góp, quyền reviewer riêng và giới hạn tốc độ.
2. Endpoint idempotent để gửi submission/report, có chống spam và giới hạn kích thước.
3. Trạng thái máy chủ rõ ràng: COMMUNITY, AI_REVIEWED, AI_UNVERIFIED, VERIFIED, REJECTED.
4. Hàng đợi reviewer có lịch sử kiểm duyệt bất biến; người gửi không thể tự xác minh đóng góp của mình.
5. Đồng bộ có retry/backoff, mã bản ghi ổn định và xử lý xung đột; chỉ dữ liệu VERIFIED mới được phát hành thành gói dùng trong game.
6. Chính sách quyền tác giả/giấy phép và nguồn dữ liệu được kiểm tra trước khi chia sẻ công khai.

Xem thêm: [Quy trình kiểm duyệt knowledge base](../knowledge_base/MODERATION.md) và [đồng bộ gói offline](../knowledge_base/BACKUP_SYNC.md).
