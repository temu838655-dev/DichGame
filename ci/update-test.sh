#!/usr/bin/env bash
# Thử cập nhật trong app trên máy giả lập, thật từ đầu đến cuối:
#  1. cài bản cũ (mã 21), mở app: app hỏi một máy chủ giả (thay GitHub) và hiện "Có bản mới";
#  2. bấm "Cập nhật ngay": app tải file APK bản mới (mã 22) về, chưa được cấp quyền cài đặt nên hiện bước "Cho phép cài đặt";
#  3. mở trang cài đặt của Android, cấp quyền, quay lại: app tự giao file cho trình cài đặt của Android;
#  4. bấm nút xác nhận của Android; kiểm tra app đã lên mã 22 và mở lại thì báo "mới nhất".
# Phần TV làm tương tự với app Dịch game TV. Ảnh và nhật ký nằm trong thư mục shots-update/.
# Dùng: update-test.sh setup | phone | tv | finish   (workflow chạy từng chế độ riêng, mỗi chế độ có giới hạn thời gian)
set -u
MODE="${1:-}"
OUT=shots-update
HOST=10.0.2.2
PORT=8765
FAIL="$OUT/update-fail.txt"
mkdir -p "$OUT"

fail() { echo "$*" >> "$FAIL"; }
A() { timeout 60 adb "$@"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; }
closedlg() { A shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; }
dump() { A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; A pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; }
savelog() { A logcat -d -s DG_UPD:I > "$OUT/update-$1-log.txt" 2>&1; }
# chờ đến khi nhật ký DG_UPD có dòng khớp mẫu; $1 = mẫu, $2 = số giây tối đa
wait_log() {
  local i
  for i in $(seq 1 "$2"); do
    if A logcat -d -s DG_UPD:I 2>/dev/null | grep -q -E "$1"; then return 0; fi
    sleep 1
  done
  return 1
}
# chờ chữ khớp mẫu trên màn hình rồi bấm vào đó; $1 = mẫu, $2 = số giây tối đa
tap_text() {
  local i xy
  for i in $(seq 1 "$2"); do
    dump
    xy=$(python3 ci/uitap.py "$OUT/ui.xml" "$1" 2>/dev/null) && { A shell input tap $xy; return 0; }
    sleep 1
  done
  return 1
}
has_text() { dump; python3 ci/uitap.py "$OUT/ui.xml" --has "$1"; }
vcode() { A shell dumpsys package "$1" 2>/dev/null | grep -o -m1 "versionCode=[0-9]*" | head -1 | cut -d= -f2; }

run_case() {  # $1 = tên ngắn (phone/tv), $2 = gói, $3 = Activity, $4 = apk cũ
  local name="$1" pkg="$2" act="$3" old="$4" i xy
  echo "[$name] bắt đầu $(date +%T)" >> "$OUT/update-log.txt"
  A uninstall "$pkg" >/dev/null 2>&1
  A install -r "$old" >/dev/null 2>&1 || { fail "[$name] cài bản cũ thất bại"; return; }
  A shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  A shell appops set "$pkg" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
  A shell appops set "$pkg" REQUEST_INSTALL_PACKAGES deny 2>/dev/null || true
  local v0; v0=$(vcode "$pkg")
  echo "[$name] mã phiên bản trước: $v0" >> "$OUT/update-log.txt"
  A logcat -c
  A shell am start -n "$pkg/$act" --es dg_update_api "http://$HOST:$PORT/releases.json" >/dev/null 2>&1
  sleep 4; closedlg

  if ! wait_log "co-ban-moi build=2" 60; then
    fail "[$name] app không báo có bản mới"
    shot "update-$name-0-no-update"; A logcat -d 2>&1 | tail -150 > "$OUT/update-$name-fulllog.txt"; savelog "$name"; return
  fi
  sleep 2; closedlg; shot "update-$name-1-found"
  if [ "$name" = phone ]; then
    has_text "Có bản mới" || fail "[$name] không thấy chữ 'Có bản mới' trên màn hình"
    tap_text "Cập nhật ngay" 20 || { fail "[$name] không thấy nút 'Cập nhật ngay'"; return; }
  else
    has_text "Có bản mới" || fail "[$name] không thấy nút cập nhật trên màn hình TV"
    tap_text "Có bản mới" 20 || { fail "[$name] không bấm được nút cập nhật TV"; return; }
  fi

  # đang tải (chụp một ảnh giữa chừng nếu bắt kịp), rồi tải xong thì cần quyền cài đặt
  sleep 1; shot "update-$name-2-downloading"
  if ! wait_log "can-quyen-cai-dat" 180; then
    fail "[$name] tải xong mà không tới bước xin quyền cài đặt"
    shot "update-$name-2b-stuck"; savelog "$name"; return
  fi
  sleep 1; closedlg; shot "update-$name-3-permission"
  if [ "$name" = phone ]; then
    has_text "Cho phép cài đặt" || fail "[$name] không thấy bước 'Cho phép cài đặt'"
    tap_text "Mở cài đặt" 20 || { fail "[$name] không thấy nút 'Mở cài đặt'"; return; }
  else
    tap_text "Cho phép cài đặt" 20 || { fail "[$name] không bấm được nút xin quyền trên TV"; return; }
  fi
  sleep 3; shot "update-$name-4-android-settings"
  # người dùng bật công tắc rồi quay lại app
  A shell appops set "$pkg" REQUEST_INSTALL_PACKAGES allow
  A shell input keyevent KEYCODE_BACK
  if ! wait_log "da-giao-cho-trinh-cai-dat" 40; then
    fail "[$name] quay lại app mà không thấy app giao file cho trình cài đặt"
    shot "update-$name-4b-stuck"; savelog "$name"; return
  fi

  # hộp thoại xác nhận của Android (nút có thể là Update / Install / Cập nhật / Cài đặt)
  for i in $(seq 1 45); do
    [ "$(vcode "$pkg")" != "$v0" ] && break
    dump
    if python3 ci/uitap.py "$OUT/ui.xml" --has "^(Update|Install|Cập nhật|Cài đặt)$"; then
      [ -f "$OUT/update-$name-5-installer.png" ] || shot "update-$name-5-installer"
      xy=$(python3 ci/uitap.py "$OUT/ui.xml" "^(Update|Install|Cập nhật|Cài đặt)$") && A shell input tap $xy
    fi
    sleep 2
  done
  savelog "$name-before-restart"
  local v1; v1=$(vcode "$pkg")
  echo "[$name] mã phiên bản sau: $v1" >> "$OUT/update-log.txt"
  if [ "$v1" = "$v0" ] || [ -z "$v1" ]; then fail "[$name] cài xong mà mã phiên bản không đổi ($v0 -> $v1)"; return; fi

  # mở lại bản mới: phải báo là đang dùng bản mới nhất
  A logcat -c
  A shell am start -n "$pkg/$act" --es dg_update_api "http://$HOST:$PORT/releases.json" >/dev/null 2>&1
  if ! wait_log "moi-nhat" 40; then fail "[$name] mở lại bản mới mà app vẫn không báo là mới nhất"; fi
  sleep 2; closedlg; shot "update-$name-6-after"
  has_text "Có bản mới" && fail "[$name] bản mới mở lên vẫn còn nhắc 'Có bản mới'"
  savelog "$name"
  echo "[$name] xong $(date +%T)" >> "$OUT/update-log.txt"
}

case "$MODE" in
  setup)
    : > "$FAIL"
    A wait-for-device
    A root >/dev/null 2>&1 || true
    A wait-for-device
    A shell settings put global hide_error_dialogs 1 2>/dev/null || true
    # máy chủ giả đóng vai trang Releases của GitHub: danh sách bản phát hành + file APK
    mkdir -p srv
    cp dist/new.apk srv/DichGame.apk
    cp dist/tv-new.apk srv/DichGame-TV.apk
    python3 - "$HOST" "$PORT" <<'PY'
import json, os, sys
host, port = sys.argv[1], sys.argv[2]
def asset(name):
    return {"name": name, "size": os.path.getsize("srv/" + name), "state": "uploaded",
            "browser_download_url": f"http://{host}:{port}/{name}"}
rel = [{"tag_name": "build-2", "name": "Bản dựng 2", "html_url": f"http://{host}:{port}/",
        "body": "Thử cập nhật trong app\n\nTải và cài ngay trong app, không cần mở trình duyệt.",
        "assets": [asset("DichGame.apk"), asset("DichGame-TV.apk")]}]
json.dump(rel, open("srv/releases.json", "w"), ensure_ascii=False)
PY
    # chạy thẳng python ở nền (không bọc trong cd && ...) để $! là đúng tiến trình máy chủ, tắt được ở bước finish
    python3 -m http.server "$PORT" --bind 0.0.0.0 --directory srv > "$OUT/server.log" 2>&1 < /dev/null &
    echo $! > "$OUT/server.pid"
    disown
    sleep 2
    curl -s "http://127.0.0.1:$PORT/releases.json" | head -c 300 > "$OUT/releases-served.txt"
    ;;
  phone) run_case phone com.dichgame.app .MainActivity dist/old.apk ;;
  tv)    run_case tv com.dichgame.tv .TvActivity dist/tv-old.apk ;;
  finish)
    A logcat -d 2>&1 | grep -E "FATAL EXCEPTION" -A8 | head -30 > "$OUT/crash.txt"
    A logcat -d 2>&1 | tail -250 > "$OUT/final-logcat.txt"
    kill "$(cat "$OUT/server.pid")" 2>/dev/null || true
    pkill -f "http.server $PORT" 2>/dev/null || true
    rm -f "$OUT/ui.xml" "$OUT/server.pid"
    [ -s "$FAIL" ] || rm -f "$FAIL"
    ;;
  *) echo "dùng: $0 setup|phone|tv|finish"; exit 2 ;;
esac
exit 0
