# DichGame — hồ sơ khai báo Data safety

**Nhà phát triển:** Huyy Đâyy  
**Email hỗ trợ:** temu838655@gmail.com  
**Mã ứng dụng:** `com.dichgame.app`  
**Chính sách quyền riêng tư:** [DichGame](https://github.com/temu838655-dev/DichGame/blob/7b216999e976c7bd760704c5f94ad9824b869975/docs/privacy-policy.md)

## Dữ liệu và mục đích

| Luồng sử dụng | Dữ liệu gửi khỏi thiết bị | Mục đích | Phạm vi |
|---|---|---|---|
| OCR đám mây | Ảnh vùng OCR hoặc khung hình có chữ, gửi tới Google Gemini | Nhận dạng chữ trong game | Tùy chế độ và ngôn ngữ đã chọn |
| Dịch AI | Chữ nhận dạng và ngữ cảnh cần dịch, gửi tới Google Gemini hoặc máy chủ CloudAPI đã cấu hình. Lời thoại do Whisper nhận dạng cũng có thể được dịch qua luồng này | Dịch và giữ ngữ cảnh | Tùy chọn |
| Giọng AI | Văn bản cần đọc, gửi tới Google Gemini | Tổng hợp giọng đọc | Tùy chọn |
| Phụ đề qua TV hoặc trình duyệt | Văn bản phụ đề và có thể có âm thanh tổng hợp gửi tới thiết bị nhận đã ghép | Hiển thị phụ đề hoặc phát giọng đọc ở thiết bị nhận | Tùy chọn |
| Remote Play | Thông tin ghép nối và lưu lượng điều khiển, hình ảnh, âm thanh giữa thiết bị và máy chơi game | Kết nối và chơi từ xa | Tùy chọn |
| Tải mô hình, gói giọng, ảnh và dữ liệu gói | Yêu cầu mạng và siêu dữ liệu kết nối tới máy chủ cung cấp nội dung | Tải tài nguyên của tính năng | Theo tính năng |

## Cách điền và những mục cần xác minh trong Console

- Đánh dấu **Có** ở câu hỏi liệu app có thu thập hoặc chia sẻ bất kỳ loại dữ liệu người dùng nào phải khai báo. Google định nghĩa thu thập theo việc gửi ra khỏi thiết bị, kể cả khi máy chủ do bên thứ ba vận hành.
- Với OCR đám mây, đối chiếu mục **Photos** cho ảnh được gửi. Với dịch và giọng AI, đối chiếu **Other user-generated content** cho văn bản có thể chứa nội dung người dùng. Mục đích dự kiến là **App functionality**. Các luồng AI nêu trên là tùy chọn.
- Dữ liệu ghi âm được Whisper xử lý trên thiết bị. Phân biệt âm thanh gốc, lời thoại đã chuyển thành chữ và giọng tổng hợp gửi tới TV. Kiểm tra mục **Audio files** theo đúng luồng TV và quy tắc ngoại lệ do người dùng chủ động chuyển dữ liệu.
- Xác minh với điều khoản của dịch vụ AI, SDK và dịch vụ tải tài nguyên trước khi khai **Shared** hoặc **Processed ephemerally**. Việc một yêu cầu chạy nhanh không chứng minh nhà cung cấp chỉ giữ dữ liệu trong bộ nhớ trong thời gian xử lý. Kiểm tra thông tin chẩn đoán của ML Kit và dịch vụ đi kèm trước khi chốt các mục chẩn đoán hoặc mã định danh.
- Không đánh dấu **mọi dữ liệu đều được mã hóa khi truyền** trước khi xử lý các kết nối HTTP nội bộ của TV và xác minh giao thức của mọi đường gửi dữ liệu phải khai báo. Chế độ Gemini dùng HTTPS.
- DichGame trên Android không có luồng tự tạo tài khoản DichGame. Người dùng có thể xóa khóa API, dữ liệu ghép nối và dữ liệu ứng dụng trên điện thoại. Nếu nội dung đã được gửi tới dịch vụ ngoài, thời hạn lưu giữ và yêu cầu xóa phụ thuộc dịch vụ đó.
- Sau khi xác minh trên thiết bị thật, đối chiếu dữ liệu thực tế với [hướng dẫn Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) trước khi xác nhận trong Console.
