# Đối chiếu yêu cầu 1–33 của PR #92

Ngày 27/11/2026. Bảng này ghi bằng chứng theo nền tảng và các phép thử còn thiếu. Các số mục khớp với đề bài. “Cần kiểm” nghĩa là chưa đủ bằng chứng nghiệm thu toàn bộ mục đó.

| Mục | Bằng chứng hiện có | Cần kiểm hoặc hoàn thiện |
|---|---|---|
| 1. Mục tiêu | Có luồng dịch Android, kho tri thức Python và đầu nhận TV. | Chạy kịch bản tích hợp theo game, phim và phụ đề có sẵn. |
| 2. Kiểm tra mã | Thay đổi tập trung ở nhánh riêng và PR #92. | Đo bộ nhớ và tải dài hạn trên thiết bị. |
| 3. Tên AI | Android có TranslationModelCatalogTest và SessionStatsTest. | So tên hiển thị với model trả về của API thật khi chuyển dự phòng. |
| 4. HTTP 429 | KeyRingTest phân biệt chờ hạn mức, chặn thanh toán và khóa sai trong thời gian nghỉ. AiRetryTest, test_ai_fallback và test_batch_translate kiểm tra thử lại và dừng khi hết hạn mức. | Đo tốc độ gửi tổng khi nhiều câu khác nhau tới cùng nhà cung cấp. |
| 5. HTTP 400 | Backend kiểm tra một lần gửi khi gặp 400. Android kiểm tra thông báo 400 dùng được cho CloudAPI và Gemini; chế độ Việt hóa kết hợp Gemini báo giữ câu gốc cho cả sáu nhóm lỗi. | Kiểm tra thông báo tiếng Việt trên giao diện với từng dịch vụ. |
| 6. Tốc độ | Kiểm thử SQLite xác nhận yêu cầu trùng chỉ gọi hàm dịch một lần. | Đo p50/p95 từ thay đổi khung hình tới phụ đề, cùng thiết bị và cảnh. |
| 7. TTS | SpeechQueueTest kiểm tra thứ tự, tồn đọng và nhận diện sự kiện của từng người nói. | Gây lỗi âm thanh giữa câu và đo phục hồi đầu ra thực. |
| 8. Màu nhân vật | Android có cấu hình màu theo game và truyền tên riêng sang TV. | Kiểm tra sau đổi cảnh, khởi động lại và trên TV thật. |
| 9. Phân vai | Có CastContextTest và cấu hình giọng theo nhân vật. | Kiểm đủ nhóm tuổi, nguồn chứng cứ, giọng chưa xác định và khóa giọng. |
| 10. Kho game | Kho tri thức có bảng game, nhân vật, nguồn dữ liệu và kiểm thử nhập, sao lưu. | Kiểm kê các trường hồ sơ và thao tác sửa từ giao diện. |
| 11. Thuật ngữ | HybridTranslationIntegrationTest kiểm tra tra thuật ngữ khi dịch trên Android. | Kiểm hết vòng đề xuất, duyệt, khóa, xuất và nhập lại. |
| 12. Bộ nhớ dịch | Backend có kiểm thử khởi động lại, khác game, ngữ cảnh, số lượng, biến và người nói. | Kiểm hết trạng thái chất lượng, lịch sử chỉnh sửa và ưu tiên bản khóa trên từng client. |
| 13. Chất lượng | TranslationGuardTest và test_matcher kiểm tra bản dịch. AiRetryTest gửi năm kiểu phần tử JSON lỗi sau một đoạn hợp lệ và kiểm tra cả năm phản hồi Gemini đều bị loại. | Đánh giá xưng hô, giọng văn và ngữ cảnh bằng hội thoại thật. |
| 14. Giao diện | Có quản lý cài đặt và thống kê phiên. Màn hình chính Android chỉ đưa ra Dịch và Thuyết minh trong bộ chọn. | Đi hết các màn quản trị và đối chiếu số thống kê với nhật ký. |
| 15. Kiến trúc | Android đi qua OCR, TextTranslator, hiển thị và hàng đợi đọc. Smoke test chạy luồng phụ đề trong màn chơi. | Kiểm tra hủy sự kiện cũ khi đổi cảnh lúc AI hoặc TTS đang bận. |
| 16. Bảo toàn dữ liệu | Có kiểm thử sao lưu Python. Dữ liệu dịch cũ được kiểm tra khi đọc. | Diễn tập cập nhật, phục hồi dữ liệu và bảo toàn thiết lập khóa trên bản phát hành. |
| 17. Kiểm thử | Có CI backend, Android JVM, ghép nối TV và smoke trên Android 14. | Hoàn thiện ma trận thiết bị, API thật và phép đo đầu cuối. |
| 18. Trình tự | Các nhóm sửa được đẩy riêng trên PR. | Đối chiếu các mục còn mở trước bước phát hành. |
| 19. Bàn giao | Số đo và giới hạn backend đã ghi trong tài liệu bộ nhớ dịch. | Báo cáo cuối cùng, danh sách tệp, hướng dẫn nâng cấp và liên kết Release. |
| 20. Nguyên tắc | Có kiểm thử hồi quy cho lỗi cụ thể và nhật ký CI. | Chạy lại tập kiểm thử liên quan trên commit ứng viên phát hành. |
| 21. Chỉ thuyết minh | Smoke Android dùng phụ đề Việt có sẵn và kiểm tra nhật ký xem có phát sinh dịch. | Thử phim và game Việt hóa với vùng chọn, watermark và âm thanh thật. |
| 22. Model | Có danh mục model trên Android và kiểm thử adapter GPT-5 Mini ở backend. | Kiểm tra quyền truy cập Gemini và OpenAI bằng khóa thực, gồm trường hợp model ưu tiên vắng mặt. |
| 23. Phương thức | Có chế độ Việt hóa và Việt hóa kết hợp Gemini; bài JVM đi qua hàm dịch của app. | Chạy đủ các phương thức với cấu hình mới, cấu hình cũ và lỗi dịch vụ. |
| 24. Tự động | VietsubGuardTest và các kiểm thử bộ nhớ bao phủ quyết định cục bộ. | Thử nội dung xen kẽ ngôn ngữ, thay đổi game và giới hạn chi phí. |
| 25. Xem trước | Có AiContextBudgetTest và đường nhận các câu tiếp theo do nguồn cung cấp. | Kiểm tra nguồn phụ đề thực có câu tiếp theo và độ trễ khi chỉ có OCR. |
| 26. Giọng | Có hàng đợi và lựa chọn giọng trong Android. | Kiểm tra danh sách giọng khả dụng, nghe thử và chuyển giọng dự phòng. |
| 27. OCR | Có OcrQualityTest và smoke nhận chữ trên SurfaceView. | Đo nhịp quét thực, CPU và số yêu cầu khi màn hình đứng yên. |
| 28. Giữ phụ đề | Có SubtitlePacerTest và logic chuyển tiếp phụ đề dùng chung Android/TV. | Đo thời điểm câu mới xuất hiện và câu cũ biến mất lúc TTS chậm. |
| 29. Câu cuối | Có logic thời gian giữ phụ đề. | Đo biên 2 và 8 giây, tự động và cố định trên màn hình. |
| 30. Điện thoại/TV | Smoke Android có xoay màn hình, kiểm thử web TV có ghép nối và truyền giao diện. | Đo vùng an toàn, xuống dòng, tỷ lệ 100% và kích thước lớn trên TV thật. |
| 31. Phong cách | Android có danh sách phong cách, PromptTemplatesTest và khóa bộ nhớ theo phong cách. | Dịch thử từng phong cách qua giao diện và kiểm thuật ngữ khóa. |
| 32. Mặc định | ProcessingModeTest kiểm tra ánh xạ thiết lập trước đây. | So sánh đủ các giá trị trên cài mới, nâng cấp và khôi phục mặc định. |
| 33. Kiểm thử bổ sung | Có kiểm thử tích hợp Việt hóa, Vietsub, OCR và bộ nhớ; xem liên kết bên dưới. | Đối chiếu từng kết quả với commit ứng viên, bổ sung phần còn thiếu ở mục 21–32. |

## Nguồn bằng chứng

- [Smoke Android 14, commit 206e150, có ảnh và nhật ký](https://github.com/temu838655-dev/DichGame/actions/runs/45955949865)
- [Kho tri thức trước lần sửa tra dữ liệu nhập, commit e1aa917](https://github.com/temu838655-dev/DichGame/actions/runs/45980865906)
- [Phép đo SQLite và cách kiểm thử dữ liệu nhập](../knowledge_base/TRANSLATION_MEMORY.md)
- [Các commit trên PR #92](https://github.com/temu838655-dev/DichGame/pull/92/commits)

- [Kiểm thử JVM Android về thông báo lỗi, thời gian nghỉ theo model và cấu trúc phản hồi Gemini tại ab574dc](https://github.com/temu838655-dev/DichGame/actions/runs/45984756549)

## Thử API thật ngày 27/11/2026

Workflow dùng hai secret độc lập, tra danh mục và gửi đúng một yêu cầu dịch
cho mỗi nhà cung cấp. Google liệt kê và trả về model
`gemini-3.5-flash-lite`. Câu trả lời chưa đạt phép kiểm tổng hợp tiếng Việt,
biến `{player}` và số lượng 2. Log không lưu câu trả lời nên chưa xác định
được điều kiện nào trượt. OpenAI liệt kê `gpt-5-mini`, rồi trả HTTP 429 với
mã `credit_balance_exhausted` khi dịch. Chưa có câu dịch hoặc model phản hồi
từ OpenAI. Phép thử gọi REST trực tiếp, chưa xác minh giao diện thiết bị.

[Nhật ký hai nhà cung cấp](https://github.com/temu838655-dev/DichGame/actions/runs/46023042121)

Lần thử Gemini tiếp theo dùng hướng dẫn tiếng Việt, phân cách câu nguồn và
kiểm riêng ba tiêu chí. Model trả về là `gemini-3.5-flash-lite`. Phản hồi
giữ đúng một biến `{player}`, giữ số 2 và chứa từ tiếng Việt chỉ tiền xu.
Thời gian từ lúc gửi yêu cầu sinh bản dịch đến khi kiểm xong phản hồi là
483 ms cho một câu. Phép đo REST này chưa bao gồm OCR, giao diện và TTS.
Lần thử chỉ chạy Gemini. [Nhật ký từng tiêu chí](https://github.com/temu838655-dev/DichGame/actions/runs/46023393694).
