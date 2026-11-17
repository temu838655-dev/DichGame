#!/usr/bin/env bash
# Chạy bên trong máy giả lập Android: cài app, mở từng màn hình, chụp ảnh, ghi lại lỗi (crash) nếu có.
# Ảnh và nhật ký nằm trong thư mục shots/.
set -u
PKG=com.dichgame.app
OUT=shots
APK=app/build/outputs/apk/debug/app-universal-debug.apk
mkdir -p "$OUT"

adb wait-for-device
adb install -r "$APK" || { echo "Cài APK thất bại" > "$OUT/crash.txt"; exit 0; }
adb root >/dev/null 2>&1 || true
adb wait-for-device
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell settings put global hide_error_dialogs 1 2>/dev/null || true  # ẩn hộp thoại "không phản hồi" của launcher giả lập khỏi ảnh chụp
adb logcat -c

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
start() { adb shell am start -W -n "$PKG/.$1" >/dev/null 2>&1; sleep 3; }
swipe_up() { adb shell input swipe 540 1800 540 600 400; sleep 1; }
set_prefs() {  # $1 = tệp XML cài đặt, không truyền = xóa sạch (cài đặt mặc định)
  adb shell am force-stop "$PKG"
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && rm -f shared_prefs/dg.xml'"
  if [ -n "${1:-}" ]; then
    adb shell "run-as $PKG sh -c 'cat > shared_prefs/dg.xml'" < "$1"
  fi
}

run_screens() {  # $1 = tiền tố tên ảnh
  start MainActivity; shot "$1-home"; swipe_up; shot "$1-home-2"
  for a in TranslateActivity SettingsActivity TvSetupActivity GuideActivity FeaturesActivity KhoGameActivity PackActivity LicensesActivity PsConnectActivity PsPairActivity; do
    start "$a"; shot "$1-$a-1"; swipe_up; shot "$1-$a-2"; swipe_up; shot "$1-$a-3"
    adb shell am force-stop "$PKG"
  done
  start WebTranslateActivity; sleep 6; shot "$1-web"
  adb shell am force-stop "$PKG"
  for d in adjust menu settings running; do
    adb shell am start -W -n "$PKG/.WebTranslateActivity" --es demo "$d" >/dev/null 2>&1; sleep 6; shot "$1-web-$d"
    adb shell am force-stop "$PKG"
  done
}

set_prefs ""
run_screens a-default
set_prefs ci/prefs-full.xml
run_screens b-full

# Dịch video web bằng OCR, thử thật: trang có chữ tiếng Anh -> chụp khung -> đọc chữ -> dịch ra tiếng Việt (nhật ký DG_OCR)
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$PKG/.WebTranslateActivity" --es demo ocrtest >/dev/null 2>&1
for i in $(seq 1 60); do
  sleep 5
  adb logcat -d -s DG_OCR:I > "$OUT/ocr-web.txt" 2>&1
  grep -q "DG_OCR.*vi: " "$OUT/ocr-web.txt" && break
done
shot web-ocrtest
cat "$OUT/ocr-web.txt"
adb logcat -d 2>&1 | grep -E "ANR|FATAL|AndroidRuntime|DG_OCR|mlkit|Translat" | tail -60 > "$OUT/ocr-web-full.txt"
grep -q "DG_OCR.*ocr doc duoc" "$OUT/ocr-web.txt" || echo "OCR video web không đọc được chữ trong khung" >> "$OUT/ocr-fail.txt"
grep -q "DG_OCR.*vi: " "$OUT/ocr-web.txt" || echo "Dịch video web bằng OCR không ra bản dịch" >> "$OUT/ocr-fail.txt"
adb shell am force-stop "$PKG"

# Dịch phụ đề trong màn chơi (không cần ghi màn hình): SurfaceView vẽ sẵn phụ đề -> PixelCopy -> OCR -> dịch -> hiện (nhật ký DG_LIVE)
adb logcat -c
adb shell am start -W -n "$PKG/.LiveDemoActivity" >/dev/null 2>&1
for i in $(seq 1 60); do
  sleep 5
  adb logcat -d -s DG_LIVE:I > "$OUT/live.txt" 2>&1
  grep -q "DG_LIVE.*dich: " "$OUT/live.txt" && break
done
sleep 2
shot live-demo
cat "$OUT/live.txt"
grep -q "DG_LIVE.*doc: " "$OUT/live.txt" || echo "Dịch trong màn chơi: không đọc được chữ từ SurfaceView" >> "$OUT/live-fail.txt"
grep -q "DG_LIVE.*dich: " "$OUT/live.txt" || echo "Dịch trong màn chơi: không ra bản dịch" >> "$OUT/live-fail.txt"
adb shell am force-stop "$PKG"

# Màn chơi Remote Play (xem thử, không kết nối máy thật): thẻ "Đang tìm máy…", thanh công cụ 9 nút, khung OCR hiện rồi ẩn đi, ngang
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo adjust >/dev/null 2>&1
sleep 1; shot play-demo
sleep 2.5; shot play-demo-adjust
sleep 3.5; shot play-demo-hidden
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo land >/dev/null 2>&1
sleep 3; shot play-demo-land
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 > "$OUT/play-fail.txt"
[ -s "$OUT/play-fail.txt" ] || rm -f "$OUT/play-fail.txt"
adb shell settings put system user_rotation 0 >/dev/null 2>&1
adb shell am force-stop "$PKG"

# Kho game Việt hoá: lọc theo nền tảng và bảng chi tiết một game (extra chỉ có ở bản debug)
adb shell am force-stop "$PKG"
# ảnh bìa tải từ Steam: chờ lâu hơn lần đầu, các lần sau lấy từ bộ nhớ đệm
adb shell am start -W -n "$PKG/.KhoGameActivity" --es plat steam >/dev/null 2>&1; sleep 30; shot kho-steam
swipe_up; sleep 12; shot kho-steam-2
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.KhoGameActivity" --es plat xbox >/dev/null 2>&1; sleep 8; shot kho-xbox
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.KhoGameActivity" --es detail "Elden Ring" >/dev/null 2>&1; sleep 8; shot kho-detail-1
swipe_up; shot kho-detail-2
adb shell am force-stop "$PKG"

# Thử giọng offline thật trên máy giả lập: app tự tải gói giọng, nạp sherpa-onnx và đọc thử một câu (SelfTest.kt)
set_prefs ci/prefs-offline.xml
start SettingsActivity; shot offline-chua-tai
adb shell settings put global hide_error_dialogs 1 2>/dev/null || true  # ẩn hộp thoại "không phản hồi" của launcher giả lập khỏi ảnh chụp
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.SettingsActivity" --ez selftest true >/dev/null 2>&1
for i in $(seq 1 90); do
  sleep 5
  adb logcat -d -s DG_SELFTEST:I > "$OUT/selftest.txt" 2>&1
  grep -qE "DG_SELFTEST.*(OK|FAIL)" "$OUT/selftest.txt" && break
done
cat "$OUT/selftest.txt"
grep -qE "DG_SELFTEST.*OK" "$OUT/selftest.txt" || echo "Giọng offline không chạy được trên máy giả lập" > "$OUT/selftest-fail.txt"
adb exec-out run-as "$PKG" cat files/selftest.wav > "$OUT/selftest.wav" 2>/dev/null || true
adb shell am force-stop "$PKG"
start SettingsActivity; shot offline-da-tai

adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|dichgame" > "$OUT/logcat-filtered.txt" 2>&1
# chỉ coi là lỗi khi nhật ký crash có nội dung thật
grep -q "dichgame" "$OUT/crash.txt" || : > "$OUT/crash.txt"
ls -la "$OUT"
exit 0
