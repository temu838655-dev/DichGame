# Mã nguồn tương ứng của DichGame trên Google Play

Gói `DichGame-Play-Remote-Play.aab` của `com.dichgame.app` được dựng từ [commit 19bbaa50564dc040f785b17b80d2b985e4d21714](https://github.com/temu838655-dev/DichGame/tree/19bbaa50564dc040f785b17b80d2b985e4d21714). [Nhật ký build và kiểm thử](https://github.com/temu838655-dev/DichGame/actions/runs/45463756741). SHA-256 của AAB đã ký là `0360060f0c6bc7d5be2b1d2e57d70ee972bd0b73e1f0476fa9a87a507e99f74f`.

Có thể lấy phần mã do DichGame duy trì và các submodule dùng khi dựng như sau.

```bash
git clone https://github.com/temu838655-dev/DichGame.git
cd DichGame
git checkout 19bbaa50564dc040f785b17b80d2b985e4d21714
git submodule update --init third_party/chiaki-ng
git -C third_party/chiaki-ng submodule update --init \
  third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl
```

Chiaki-ng được ghim tại [43728af2e639fecb8a7af1cd68c29820a40de973](https://github.com/streetpea/chiaki-ng/tree/43728af2e639fecb8a7af1cd68c29820a40de973). [Workflow chính xác của bản dựng](https://github.com/temu838655-dev/DichGame/blob/19bbaa50564dc040f785b17b80d2b985e4d21714/.github/workflows/build-play.yml) chỉ ra phiên bản công cụ, phụ thuộc được tải trong khi dựng, các bản vá cho PCM và bộ giải mã video, cách dựng gói TV và cách xuất AAB. Dùng `-PbuildNo=1000005` để tái tạo mã phiên bản của bản dựng này. Khóa cá nhân của nhà phát triển không cần thiết để biên dịch mã nguồn.

Xem [thông báo giấy phép](../NOTICE.md) và [toàn văn AGPL-3.0](../LICENSE).
