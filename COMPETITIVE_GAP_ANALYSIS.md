# COMPETITIVE_GAP_ANALYSIS.md

Ngày đối chiếu: 2026-11-26
Sản phẩm tham chiếu: https://ps5tiengviet.com/ (Console Translator: Dịch game)

Chỉ ghi nhận thông tin quan sát được trên website công khai; không suy đoán kiến trúc nội bộ hoặc chất lượng thực tế của đối thủ.

| Hạng mục | Console Translator (website công khai) | Dịch Game (repository hiện tại) | Đánh giá |
|---|---|---|---|
| Nền tảng app | Website công bố iOS/iPadOS, Android, Windows và macOS. | Repository này chứa app Android phone và Android TV; chưa thấy app iOS/Windows/macOS trong README/cấu trúc đã kiểm tra. | GAP lớn về độ phủ nền tảng. |
| Console | Website công bố PS4/PS5 kết nối qua Remote Play; Xbox dùng app Android/Windows/Mac đọc hình từ app remote play/cửa sổ game. | Android app có engine Chiaki-ng cho Remote Play; README công bố PS5/PS4/Xbox. Cần xác minh console thật. | Phạm vi công bố gần nhau ở PS4/PS5; cơ chế Xbox/compatibility cần thử. |
| PC / Steam | Website công bố dịch game PC/Steam trên Windows/macOS. | README nói rõ chưa hỗ trợ dịch trực tiếp PC/Steam; PC chỉ là nguồn dữ liệu Việt hóa. | GAP chức năng trực tiếp. |
| Nguồn hình và OCR | Website công bố OCR phụ đề, dịch vùng chữ/menu và capture card ở bản desktop. | Android app có MediaProjection/OCR, vùng dịch/menu và Remote Play PixelCopy theo README/source; capture card desktop chưa có. | Có các luồng tương tự trên Android; capture card/desktop là GAP. |
| Dịch thuật | Website công bố dịch thường, AI theo game, bộ Việt hóa cho một số game, giọng đọc. | Repository có dịch local/CloudAPI/Gemini, kho game/gói Việt hóa, TTS và ASR. | Tương đồng về hướng tính năng; chất lượng/dữ liệu chưa được benchmark độc lập. |
| TV | Website công bố Android TV/Google TV, LG webOS và Samsung Tizen (từ đời 2017 theo FAQ). | Có app Android TV; README mô tả LG thử nghiệm và Samsung source package/developer mode. | Android TV có nền tảng; LG/Samsung cần kiểm thử TV thực, chưa được coi là ngang mức hỗ trợ. |
| Quy trình ghép nối | Website có hướng dẫn từng bước, mã 8 số/tài khoản phụ PSN và lựa chọn chơi trên TV hoặc điện thoại. | Repository có ghép nối PSN/Remote Play và pairing TV; cần kiểm tra end-to-end trên thiết bị. | Cần so sánh UX thực tế bằng test case. |
| Mô hình giá | Website công bố Premium và PRO trả phí cho một số tính năng. | Repository hiện mô tả các engine local/Gemini/CloudAPI; chưa xác minh hệ thống billing/subscription tương đương. | Không được tuyên bố parity thương mại; cần xác định chiến lược miễn phí rõ ràng. |

## Kết luận kỹ thuật
1. Không sao chép mã nguồn, giao diện độc quyền, nội dung hoặc thương hiệu của sản phẩm tham chiếu.
2. Ưu tiên nâng độ ổn định Android hiện có trước khi mở rộng nền tảng.
3. Các GAP lớn cần roadmap riêng: PC/Steam native, iOS/macOS, capture-card desktop, và kiểm thử TV LG/Samsung thực.
4. Website tham chiếu không chứng minh mọi tính năng đều hoạt động trên mọi thiết bị; các tuyên bố tương thích phải được thử độc lập.

## Nguồn công khai
- Trang chủ / FAQ: https://ps5tiengviet.com/
- Hướng dẫn Android: https://ps5tiengviet.com/android-guide.html
- Trang desktop: https://ps5tiengviet.com/desktop.html
- Kho game: https://ps5tiengviet.com/games.html
