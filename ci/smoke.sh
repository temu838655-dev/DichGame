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
}

set_prefs ""
run_screens a-default
set_prefs ci/prefs-full.xml
run_screens b-full

# Kho game Việt hoá: lọc theo nền tảng và bảng chi tiết một game (extra chỉ có ở bản debug)
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.KhoGameActivity" --es plat xbox >/dev/null 2>&1; sleep 3; shot kho-xbox
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.KhoGameActivity" --es detail "Elden Ring" >/dev/null 2>&1; sleep 3; shot kho-detail-1
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
