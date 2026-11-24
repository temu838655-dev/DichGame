# Mã nguồn tương ứng của DichGame trên Google Play

| Tệp AAB đã ký | Mã phiên bản | Mã nguồn | SHA-256 | Build |
|---|---|---|---|---|
| DichGame-Play-1000027.aab | 1000027 | [af29ecc](https://github.com/temu838655-dev/DichGame/tree/af29ecc7af6582a345195b8e0939500e0ec3a537) | `53766342003112d1a2db2d0019efd0b5d78470892432319b2dfa2d214a0d5ccd` | [Run 45467305566](https://github.com/temu838655-dev/DichGame/actions/runs/45467305566) |
| DichGame-Play-Remote-Play.aab | 1000025 | [19bbaa5](https://github.com/temu838655-dev/DichGame/tree/19bbaa50564dc040f785b17b80d2b985e4d21714) | `0360060f0c6bc7d5be2b1d2e57d70ee972bd0b73e1f0476fa9a87a507e99f74f` | [Run 45463756741](https://github.com/temu838655-dev/DichGame/actions/runs/45463756741) |

Có thể lấy phần mã do DichGame duy trì và các submodule dùng khi dựng như sau.

```bash
git clone https://github.com/temu838655-dev/DichGame.git
cd DichGame
git checkout af29ecc7af6582a345195b8e0939500e0ec3a537
git submodule update --init third_party/chiaki-ng
git -C third_party/chiaki-ng submodule update --init \
  third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl
```

Chiaki-ng được ghim tại [43728af2e639fecb8a7af1cd68c29820a40de973](https://github.com/streetpea/chiaki-ng/tree/43728af2e639fecb8a7af1cd68c29820a40de973). [Workflow chính xác của bản dựng](https://github.com/temu838655-dev/DichGame/blob/af29ecc7af6582a345195b8e0939500e0ec3a537/.github/workflows/build-play.yml) chỉ ra phiên bản công cụ, phụ thuộc được tải trong khi dựng, các bản vá cho PCM và bộ giải mã video, cách dựng gói TV và cách xuất AAB. Dùng `-PbuildNo=1000007` để tái tạo mã phiên bản của bản dựng này. Khóa cá nhân của nhà phát triển không cần thiết để biên dịch mã nguồn.

Xem [thông báo giấy phép](../NOTICE.md) và [toàn văn AGPL-3.0](../LICENSE).
