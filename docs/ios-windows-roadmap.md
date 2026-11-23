# DichGame cho iOS và Windows — bản nền tảng đầu tiên

Ứng dụng Android hiện tại trong app/ và tv/ là bản độc lập. ios/ và windows/ là hai ứng dụng native mới. Không thể lấy APK Android đổi đuôi để cài lên iPhone hoặc Windows.

## Phạm vi bản này

| Khả năng | Android | iOS bước đầu | Windows bước đầu |
|---|---|---|---|
| Chọn tên game làm ngữ cảnh dịch | Có | Nhập tên | Nhập tên |
| Dịch hình game bằng Gemini | Có | Chọn ảnh từ Photos | Chụp màn hình chính hoặc mở ảnh |
| Thử khóa Gemini tiếp theo khi 401/403/429 | Có | Có | Có |
| Lưu khóa trong thiết bị | Cài đặt Android | Keychain | Windows DPAPI |
| Đọc tiếng Việt | Nhiều TTS | AVSpeech vi-VN | Giọng vi-VN đã cài trên Windows |
| Quét game liên tục, phụ đề nổi | Có (theo quyền Android) | Chưa | Chưa |
| Remote Play tích hợp trong app | Android/Chiaki | Chưa | Chưa |
| Kho game, bộ nhớ dịch và thẻ nhân vật đầy đủ | Có | Chưa đồng bộ | Chưa đồng bộ |

Các bản iOS/Windows mới không tự động có chức năng Android. Chỉ dịch hình người dùng lựa chọn, không chụp âm thầm hoặc gửi dữ liệu khi chưa bấm dịch.

## iOS

Yêu cầu: máy Mac, Xcode và XcodeGen.

1. Cài Xcode và XcodeGen; chạy brew install xcodegen.
2. Vào thư mục ios/ rồi chạy xcodegen generate.
3. Mở DichGameiOS.xcodeproj, chọn iOS Simulator và bấm Run.
4. GitHub Actions build-ios.yml tạo artifact DichGame-iOS-Simulator. File này CHỈ dành cho Simulator, không thể cài trực tiếp lên iPhone.
5. Để dùng iPhone thật, mở Signing & Capabilities trong Xcode, đăng nhập Apple Account, cấu hình Team, Bundle ID và quyền ký ứng dụng. Phát hành TestFlight/App Store cần Apple Developer Program, App Store Connect và các bước kiểm duyệt.

Để dịch trực tiếp khi mở một game trong ứng dụng iOS khác, cần khảo sát ReplayKit Broadcast Upload Extension. Không được hứa có overlay hệ thống tương đương Android trước khi thử nghiệm kỹ và xem xét App Store Review.

## Windows

Yêu cầu: Windows 10/11 x64. Có thể biên dịch bằng Visual Studio 2022/.NET 8.

1. Download artifact DichGame-Windows-x64 từ GitHub Actions hoặc chạy dotnet publish đối với windows/DichGame.Windows/DichGame.Windows.csproj.
2. Mở DichGame.Windows.exe. Bản artifact được publish self-contained (không cần tự cài .NET runtime).
3. Nhập tên game và một/nhiều khóa Gemini, nhấn lưu khóa vào DPAPI.
4. Chụp màn hình chính bằng nút (ứng dụng tự ẩn trong lúc chụp) hoặc mở ảnh có sẵn; nhấn Dịch.
5. Giọng đọc dùng giọng vi-VN đã cài trên Windows. Nếu không có, hãy cài tại cài đặt giọng của hệ điều hành.

Bản .exe đầu tiên chưa có chứng chỉ ký số; SmartScreen có thể cảnh báo. Để phân phối rộng, cần ký mã hoặc đóng gói qua MSIX/Store.

## Lộ trình nâng cấp lên chức năng tương đương Android

1. Chia sẻ được gói thuật ngữ, hồ sơ, bộ nhớ dịch và tên giọng nhân vật giữa ba nền tảng.
2. Trên Windows: bổ sung API Windows.Graphics.Capture để chọn cửa sổ game, trích chữ từ video liên tục và hiển thị phụ đề nổi theo quyền người dùng.
3. Trên iOS: thử nghiệm ReplayKit broadcast extension và hiển thị phụ đề trong những bề mặt hệ điều hành cho phép.
4. Port Remote Play engine chiaki-ng sang iOS và Windows, nghiên cứu hỗ trợ tay cầm, codec và ghép nối; không dùng trực tiếp Android JNI.
5. Xây dựng kiểm thử trên thiết bị thật, ký ứng dụng, chuẩn bị quyền riêng tư, phát hành TestFlight/App Store và bản Windows đã ký.

Không lưu khóa Gemini hoặc chứng chỉ Apple trong repository. Mã nguồn không tự cung cấp tài khoản Developer, Mac cho ký ứng dụng hay thông tin App Store Connect của chủ sở hữu.
