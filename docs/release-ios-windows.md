# DichGame — phát hành iOS và Windows

## Trang tải

**[GitHub Releases](https://github.com/temu838655-dev/DichGame/releases)**

| Nền tảng | Tag / tệp | Tình trạng |
|---|---|---|
| Android và Android TV | build-N; DichGame.apk, DichGame-TV.apk | Bản chính, app Android dùng tag này để cập nhật |
| iPhone | ios-preview-N; DichGame-iPhone-unsigned.ipa | CHƯA KÝ: không thể cài trực tiếp trên iPhone thông thường |
| iOS Simulator | ios-preview-N; DichGame-iOS-Simulator.zip | Chỉ chạy trên Simulator với Xcode/Mac |
| Windows x64 | windows-preview-N; DichGame.Windows.exe, DichGame-Windows-x64.zip | Có thể chạy thử; trạng thái ký ghi ở từng release |

iOS và Windows được đưa lên GitHub Releases theo chế độ **prerelease**,
nhờ đó không thay đổi bản Android mới nhất hoặc cơ chế cập nhật build-N.
File SHA256SUMS.txt giúp kiểm tra tính toàn vẹn.

## iOS — công bố IPA

Workflow .github/workflows/build-ios.yml build app iPhone arm64 chưa ký và
iOS Simulator. Khi cả hai job thành công trên nhánh main, job
publish-ios-preview tự động đưa file lên release ios-preview-N.

**IPA chưa ký không thể cài bằng cách tải và bấm mở trên iPhone.**

Để chạy iPhone thật, cần cấu hình code signing bằng Xcode/Apple Developer.
Workflow .github/workflows/build-ios-signed-ipa.yml hỗ trợ Ad Hoc với
certificate .p12 và provisioning profile trong GitHub Secrets;
IPA đã ký chỉ cài được trên những thiết bị có UDID trong profile.
Để phân phối rộng, cần TestFlight / App Store và các bước xác nhận của Apple.
Không công khai bản Ad Hoc chứa thông tin thiết bị thử nghiệm.

## Windows — ký số đáng tin cậy bằng Azure Artifact Signing

Workflow .github/workflows/build-windows.yml có hai chế độ:

- Mặc định: build EXE chưa ký. Release ghi cảnh báo nhà phát hành chưa xác định.
- Khi cấu hình: đăng nhập Azure bằng GitHub OIDC, ký EXE bằng Azure Artifact Signing,
  kiểm tra Get-AuthenticodeSignature phải là Valid rồi mới upload và phát hành.
  Nếu ký không thành công thì job thất bại, không có bản release ghi sai tình trạng.

### Cấu hình để bật ký số Windows

1. Tạo tài khoản Azure Artifact Signing, xác minh danh tính nhà phát hành và
   certificate profile Public Trust theo điều kiện Microsoft.
2. Thiết lập Microsoft Entra application và federated credential cho GitHub;
   cấp quyền Artifact Signing Certificate Profile Signer đúng phạm vi profile.
3. GitHub → Settings → Secrets and variables → Actions → Secrets:
   - AZURE_CLIENT_ID
   - AZURE_TENANT_ID
   - AZURE_SUBSCRIPTION_ID
4. GitHub → Settings → Secrets and variables → Actions → Variables:
   - WINDOWS_ARTIFACT_SIGNING_ENABLED = true
   - WINDOWS_ARTIFACT_SIGNING_ENDPOINT = URL endpoint theo vùng Azure
   - WINDOWS_ARTIFACT_SIGNING_ACCOUNT = tên account Artifact Signing
   - WINDOWS_ARTIFACT_SIGNING_CERTIFICATE_PROFILE = tên certificate profile
5. Chạy workflow Build Windows (x64) and publish preview trên main hoặc
   đẩy thay đổi ở thư mục windows lên main. Chữ ký được timestamp và kiểm tra
   Authenticode trước khi tạo release.

**Ký số không bảo đảm SmartScreen ngừng cảnh báo ngay.** Publisher và
tệp tin cần tích lũy uy tín thực tế. Chứng chỉ tự ký không có public trust.

Tài liệu chính thức:
- https://learn.microsoft.com/windows/apps/package-and-deploy/code-signing-options
- https://learn.microsoft.com/windows/apps/package-and-deploy/smartscreen-reputation
- https://github.com/Azure/artifact-signing-action

## Giao diện mới

iOS dùng SwiftUI native, nền đen sâu, vàng champagne, hero giới thiệu,
card ngữ cảnh game, xem trước ảnh, dịch AI, kết quả tiếng Việt, nút đọc/chép,
quản lý khóa bằng Keychain.

Windows dùng WPF dashboard tối/vàng, hero trang chủ và bố cục hai cột:
ngữ cảnh game, ảnh game, dịch, kết quả và lưu khóa mã hóa bằng DPAPI.

**Chức năng iOS/Windows hiện chưa ngang với Android:** mới dịch ảnh chọn tay,
chưa có Remote Play hay OCR liên tục và lớp phụ đề nổi trên game khác.

Không đưa certificate, private key, provisioning profile hoặc API key vào
GitHub commit. Chỉ dùng Secrets / GitHub OIDC và tài khoản đã xác minh.
