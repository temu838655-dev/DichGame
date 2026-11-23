# Hai chế độ Dịch và Thuyết minh của DichGame (Android)

Giao diện có **hai chế độ chính**: **Dịch** và **Thuyết minh**. Trong chế độ Dịch có công tắc **AI Tự động** để bật ưu tiên gói game/bộ nhớ/Gemini; không tạo chế độ thứ ba. Phần giọng đọc chỉ có **Giọng cơ bản** (TTS của máy) và **Giọng AI** (Gemini, tự lùi về cơ bản khi lỗi). Giọng offline đã tải cũ không còn hiển thị để lựa chọn.

## Thuyết minh (Đọc Vietsub) — phim Vietsub / game đã Việt hóa

- Chọn **Đọc Vietsub** từ trang chính, Cách dịch, hoặc Cài đặt trong màn chơi Remote Play.
- Đưa vùng quét vào phụ đề tiếng Việt. App chỉ đọc tiếng Việt đã có bằng hệ thống TTS đang chọn, **không dịch, không gửi chữ để Gemini dịch**.
- Nguồn CC/OCR không đủ bằng chứng là tiếng Việt hoặc rõ là tiếng nước ngoài sẽ không được đọc. Chữ quá ngắn/không dấu có thể bị bỏ qua nhằm tránh đọc sai ngôn ngữ.
- Có thể gán giọng theo nhân vật nếu đang bật TTS Gemini và bộ nhận dạng người nói lấy được tên.

## ✦ AI Tự động

1. **Tên game**: dùng game đã chọn trong Kho game; nếu trên vùng OCR xuất hiện **nguyên tựa game đủ rõ ở hai lượt quét liên tiếp**, app chuyển sang hồ sơ đúng game. Không đoán game bằng một từ thoại hoặc tự khẳng định truy vấn được tên game đang chạy qua PS5 Remote Play.
2. **Bộ nhớ**: ưu tiên câu Việt hóa khớp chính xác từ gói game/file người dùng, rồi câu AI đã học đúng game/người nói, tiếp theo kết quả gần đúng có độ tin cậy cao của gói Việt hóa.
3. **Gemini**: chỉ gửi câu chưa có trong bộ nhớ sang Gemini; khi không có khóa sẽ dùng dịch trên máy cho những ngôn ngữ được hỗ trợ. Khóa chính + khóa dự phòng được thử lần lượt ở lỗi 401, 403, 429. Khóa nằm cùng một Google Cloud project có thể cùng hết hạn mức nên không bảo đảm luôn thông qua.
4. **Ghi nhớ**: chỉ lưu câu AI qua bộ lọc chất lượng; lưu riêng theo hồ sơ game, tên người nói và ngôn ngữ, không trộn GTA V với GTA VI.
5. **Giọng nhân vật**: giọng Gemini TTS được gán theo nhân vật và lưu ổn định trong hồ sơ game. Người dùng có thể thay giọng cụ thể ở mục Giọng đọc / Gói Việt hóa. Nếu chưa có tên người nói hoặc không dùng TTS Gemini, app dùng giọng mặc định đã chọn.

**Giới hạn thực tế:** OCR chỉ có thể nhận diện tên game khi hình ảnh được quét chứa chính xác tên game, thường là màn hình tiêu đề. Một số game dùng tên viết tắt/hình logo không đọc được, khi đó chọn thủ công trong Kho game để tránh sai. Phụ đề đã có tiếng Việt không cần chuyển sang AI Tự động: dùng Đọc Vietsub sẽ chính xác hơn và không tốn lượt dịch.

## Kiểm thử

- `VietsubGuardTest`: nhận câu tiếng Việt và không đọc câu tiếng Anh/Nhật/Pháp hoặc OCR rác.
- `AutoGameDetectorTest`: yêu cầu xác nhận hai lần, không nhầm series GTA V/VI và không nhận tựa game từ một câu hội thoại dài.
- Build Android cần chạy `app:testDebugUnitTest` và `app:assembleRelease`; chức năng thực tế vẫn phải kiểm thử với PS5/TV và phim/game Vietsub thật.
