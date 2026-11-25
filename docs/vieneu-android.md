# VieNeu Nano (thử nghiệm)
Nguồn: https://github.com/pnnbao97/VieNeu-TTS
Model: https://huggingface.co/pnnbao-ump/VieNeu-TTS-v3-Nano

Kiểm tra trên máy Python được hỗ trợ:
```sh
python -m pip install vieneu
python tools/vieneu_probe.py --out nano-probe --steps 8
```
Công cụ tạo WAV thử từng giọng có sẵn và báo thời gian tải, RTF. Không phải benchmark Android.

Nano dùng flow-matching ONNX, không tương thích trực tiếp với OfflineTts VITS/Piper. Cần port tokenizer/chuẩn hóa tiếng Việt, các graph suy luận và vòng lặp lấy mẫu từ SDK theo phiên bản được ghim; đối chiếu đầu ra PCM 24kHz với mẫu Python. Kiểm tra giấy phép từng model và dữ liệu giọng trước khi phân phối. Sau đó kiểm thử ARM64, RAM, pin, nhiệt, hủy lượt đọc và phát âm khi chuyển game. Chỉ bật tùy chọn Nano sau khi runtime thật hoạt động; giữ Piper và TTS hệ thống làm dự phòng. Không gửi lời thoại lên máy chủ nếu người dùng chọn offline.

Hiện chưa có runtime Nano trong APK và chưa chạy probe tải model.
