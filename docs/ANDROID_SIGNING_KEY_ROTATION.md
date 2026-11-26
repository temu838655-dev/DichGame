# Bảo mật khóa ký Android — bắt buộc trước bản phát hành tiếp theo

## Tình trạng

Keystore Android cũ `keystore/dichgame.jks` và thông tin mật khẩu mặc định đã từng nằm trong repository công khai. File đã được xóa khỏi nhánh `main`, Gradle không còn dùng mật khẩu mặc định, và workflow không còn được phép phát hành bằng khóa cũ.

**Xóa file khỏi `main` không xóa các commit cũ.** Coi khóa cũ là đã bị lộ vĩnh viễn. Không dùng nó để ký bất kỳ APK nào nữa.

## Cần cấu hình GitHub Secrets

Người quản trị repository phải tạo một keystore riêng mới (hoặc thiết lập quy trình ký/luân chuyển khóa có lineage phù hợp) và thêm đủ bốn Actions secrets tại:

Repository → Settings → Secrets and variables → Actions → New repository secret

- `KEYSTORE_BASE64`: nội dung Base64 của file keystore mới.
- `KEYSTORE_PASSWORD`: mật khẩu keystore.
- `KEY_ALIAS`: alias của khóa.
- `KEY_PASSWORD`: mật khẩu của alias.

Không commit file keystore, mật khẩu, Base64 hoặc private key vào repository, issue, log hay chat công khai.

## Cảnh báo tương thích cập nhật

APK build-2021 được ký bằng khóa cũ đã lộ. Nếu ký bản mới bằng một khóa hoàn toàn khác, Android sẽ không cho cài đè lên ứng dụng hiện tại. Trước khi phát hành cho người dùng hiện tại, phải có kế hoạch ký luân chuyển tương thích và kiểm thử cài đặt nâng cấp; nếu không có, cần thông báo người dùng sao lưu dữ liệu và gỡ bản cũ trước khi cài bản mới.

Không được bỏ qua kiểm tra chứng thư của workflow để ép phát hành.

## Dọn lịch sử Git

File đã bị xóa khỏi nhánh hiện tại nhưng vẫn tồn tại trong lịch sử commit công khai. Chủ repository cần thực hiện lọc lịch sử (ví dụ `git filter-repo` loại bỏ `keystore/dichgame.jks`) rồi force-push các nhánh/tag liên quan, phối hợp để người cộng tác clone lại repository. Nếu không thể viết lại lịch sử an toàn, liên hệ GitHub Support để xử lý nội dung nhạy cảm trong cache.

## Điều kiện mở khóa phát hành

- [ ] Khóa cũ bị thu hồi/không còn được sử dụng.
- [ ] Lịch sử công khai được dọn hoặc đã có xác nhận xử lý từ GitHub.
- [ ] Bốn GitHub Secrets mới được cấu hình.
- [ ] Có phương án tương thích nâng cấp cho người cài build-2021.
- [ ] Build ký bằng khóa mới, xác minh chữ ký và thử cài đè/nâng cấp trên thiết bị thật.
