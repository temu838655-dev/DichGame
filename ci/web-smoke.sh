#!/usr/bin/env bash
set -euo pipefail
APK=app/build/outputs/apk/debug/app-universal-debug.apk
PKG=com.dichgame.app
OUT=shots-web
mkdir -p "$OUT"
adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || true

run_demo() {
  local demo="$1"
  local name="$2"
  local expected="$3"
  adb shell am force-stop "$PKG" || true
  adb logcat -c
  adb shell am start -W -n "$PKG/.WebTranslateActivity" --es demo "$demo"
  local success=0
  for i in $(seq 1 34); do
    sleep 5
    adb logcat -d -s DG_OCR:I > "$OUT/$name.txt" || true
    if grep -q "$expected" "$OUT/$name.txt"; then success=1; break; fi
  done
  adb exec-out screencap -p > "$OUT/$name.png" || true
  adb logcat -d | grep -E 'FATAL EXCEPTION|AndroidRuntime|DG_OCR|mlkit|Translation' | tail -150 > "$OUT/$name-full.txt" || true
  cat "$OUT/$name.txt"
  if [ "$success" != 1 ]; then
    echo "Web $demo chưa hoàn thành: thiếu $expected" >&2
    cat "$OUT/$name-full.txt" >&2
    exit 1
  fi
  if grep -q 'FATAL EXCEPTION' "$OUT/$name-full.txt"; then
    echo "App bị crash khi kiểm thử $name" >&2
    exit 1
  fi
}
# Thử OCR từ PixelCopy và đọc thực qua ML Kit rồi dịch.
run_demo ocrtest ocr 'DG_OCR.*vi: '
# Thử CC thật từ document.querySelector(...).innerText và bridge WebView.
run_demo ccdemo cc 'DG_OCR.*vi: '
# Thuyết minh phải dịch phụ đề tiếng Anh rồi mới đọc tiếng Việt.
run_demo ccnarrate narrate 'DG_OCR.*vi: '
