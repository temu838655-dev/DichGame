#!/usr/bin/env python3
"""Vá bộ giải mã hình Android của chiaki-ng (third_party/chiaki-ng/android/app/src/main/cpp/video-decoder.c).

Lỗi gốc: set_surface(NULL) khoá codec_mutex rồi gọi kill_decoder, nhưng kill_decoder lại khoá chính mutex đó thêm lần nữa.
Mutex tạo không đệ quy, nên luồng giao diện treo cứng mỗi khi surface bị huỷ (xoay màn hình, tắt màn hình). Nhánh return sau đó
cũng không mở khoá, nên cả luồng nhận hình sẽ kẹt theo. Ngoài ra khi gặp lại đúng surface đang dùng (chỉ đổi kích thước lúc xoay)
thì không được gọi setOutputSurface lần nữa. Script dừng build nếu mã gốc khác đi.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "third_party/chiaki-ng/android/app/src/main/cpp/video-decoder.c"
src = path.read_text(encoding="utf-8")

if "Phải gọi khi đang giữ codec_mutex" in src:
    print("video-decoder.c đã được vá")
    raise SystemExit(0)

def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Không vá an toàn được video-decoder.c ({label}): cần đúng 1 chỗ khớp, thấy {count}")
    return source.replace(old, new, 1)

start = src.find("static void kill_decoder(AndroidChiakiVideoDecoder *decoder)")
end = src.find("void android_chiaki_video_decoder_fini")
if start < 0 or end < 0 or end < start:
    raise SystemExit("Không vá an toàn được video-decoder.c (kill_decoder): không tìm thấy hàm")
NEW_KILL = '/* Phải gọi khi đang giữ codec_mutex. Hàm tự mở khoá mutex (luồng ra cần mutex này để thoát) và luôn trả lại ở trạng thái mở khoá.\n * Bản gốc tự khoá lại ở đầu hàm trong khi set_surface(NULL) đã khoá sẵn: mutex không đệ quy nên treo cứng cả luồng giao diện\n * mỗi khi surface bị huỷ (xoay màn hình, màn hình tắt), và nhánh return sau đó cũng quên mở khoá. */\nstatic void kill_decoder(AndroidChiakiVideoDecoder *decoder)\n{\n\tdecoder->shutdown_output = true;\n\tssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, 1000);\n\tif(codec_buf_index >= 0)\n\t{\n\t\tCHIAKI_LOGI(decoder->log, "Video Decoder sending EOS buffer");\n\t\tAMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, 0, decoder->timestamp_cur++, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);\n\t\tAMediaCodec_stop(decoder->codec);\n\t\tchiaki_mutex_unlock(&decoder->codec_mutex);\n\t\tchiaki_thread_join(&decoder->output_thread, NULL);\n\t}\n\telse\n\t{\n\t\tCHIAKI_LOGE(decoder->log, "Failed to get input buffer for shutting down Video Decoder!");\n\t\tAMediaCodec_stop(decoder->codec);\n\t\tchiaki_mutex_unlock(&decoder->codec_mutex);\n\t\tchiaki_thread_join(&decoder->output_thread, NULL);\n\t}\n\tAMediaCodec_delete(decoder->codec);\n\tdecoder->codec = NULL;\n\tif(decoder->window)\n\t{\n\t\tANativeWindow_release(decoder->window);\n\t\tdecoder->window = NULL;\n\t}\n\tdecoder->shutdown_output = false;\n}\n\n'
src = src[:start] + NEW_KILL + src[end:]

src = replace_once(src, """	if(decoder->codec)
		kill_decoder(decoder);
	chiaki_mutex_fini(&decoder->codec_mutex);""", """	chiaki_mutex_lock(&decoder->codec_mutex);
	if(decoder->codec)
		kill_decoder(decoder);	// mở khoá mutex khi trả về
	else
		chiaki_mutex_unlock(&decoder->codec_mutex);
	chiaki_mutex_fini(&decoder->codec_mutex);""", "fini")

src = replace_once(src, """		if(decoder->codec)
		{
			kill_decoder(decoder);
			CHIAKI_LOGI(decoder->log, "Decoder shut down after surface was removed");
		}
		return;""", """		if(decoder->codec)
		{
			kill_decoder(decoder);	// mở khoá mutex khi trả về
			CHIAKI_LOGI(decoder->log, "Decoder shut down after surface was removed");
		}
		else
			chiaki_mutex_unlock(&decoder->codec_mutex);
		return;""", "set_surface NULL")

src = replace_once(src, """		ANativeWindow *new_window = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
		AMediaCodec_setOutputSurface(decoder->codec, new_window);
		ANativeWindow_release(decoder->window);
		decoder->window = new_window;""", """		ANativeWindow *new_window = ANativeWindow_fromSurface(env, surface);
		if(new_window && new_window == decoder->window)
		{
			// cùng một surface (chỉ đổi kích thước khi xoay màn hình): không đụng tới bộ giải mã
			ANativeWindow_release(new_window);
			goto beach;
		}
		if(new_window && AMediaCodec_setOutputSurface(decoder->codec, new_window) == AMEDIA_OK)
		{
			ANativeWindow_release(decoder->window);
			decoder->window = new_window;
		}
		else
		{
			CHIAKI_LOGE(decoder->log, "AMediaCodec_setOutputSurface() failed, keeping previous surface");
			if(new_window)
				ANativeWindow_release(new_window);
		}""", "swap surface")

path.write_text(src, encoding="utf-8")
print("Đã vá video-decoder.c")
