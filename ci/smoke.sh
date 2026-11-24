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
adb shell settings put secure immersive_mode_confirmations confirmed 2>/dev/null || true  # bỏ hộp thoại "Viewing full screen" che ảnh chụp lúc xoay ngang
adb logcat -c

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
start() { adb shell am start -W -n "$PKG/.$1" >/dev/null 2>&1; sleep 3; }
swipe_up() { adb shell input swipe 540 1800 540 600 400; sleep 1; }
start_page() { adb shell am start -W -n "$PKG/.SettingsActivity" --es page "$1" >/dev/null 2>&1; sleep 3; }
set_prefs() {  # $1 = tệp XML cài đặt, không truyền = xóa sạch (cài đặt mặc định)
  adb shell am force-stop "$PKG"
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && rm -f shared_prefs/dg.xml'"
  if [ -n "${1:-}" ]; then
    adb shell "run-as $PKG sh -c 'cat > shared_prefs/dg.xml'" < "$1"
  fi
}

run_screens() {  # $1 = tiền tố tên ảnh
  start MainActivity; sleep 4; shot "$1-home"; swipe_up; shot "$1-home-2"; swipe_up; shot "$1-home-3"
  for a in TranslateActivity SettingsActivity TvSetupActivity GuideActivity FeaturesActivity KhoGameActivity PackActivity LicensesActivity PsConnectActivity PsPairActivity; do
    start "$a"; shot "$1-$a-1"; swipe_up; shot "$1-$a-2"; swipe_up; shot "$1-$a-3"
    adb shell am force-stop "$PKG"
  done
  for br in lg samsung; do
    adb shell am start -W -n "$PKG/.TvSetupActivity" --es brand "$br" >/dev/null 2>&1; sleep 3
    shot "$1-tv-$br-1"; swipe_up; shot "$1-tv-$br-2"; swipe_up; shot "$1-tv-$br-3"
    adb shell am force-stop "$PKG"
  done
  for pg in voice subs profile about; do
    start_page "$pg"; shot "$1-set-$pg-1"; swipe_up; shot "$1-set-$pg-2"; swipe_up; shot "$1-set-$pg-3"
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

# Chế độ Thuyết minh trong màn chơi, thử thật: phụ đề tiếng Việt vẽ sẵn -> PixelCopy -> đọc chữ -> KHÔNG dịch, đi thẳng tới đọc to (nhật ký DG_LIVE)
adb logcat -c
adb shell am start -W -n "$PKG/.LiveDemoActivity" --es mode narrate >/dev/null 2>&1
for i in $(seq 1 40); do
  sleep 4
  adb logcat -d -s DG_LIVE:I > "$OUT/narrate.txt" 2>&1
  grep -qE "DG_LIVE.*(thuyet minh|doc): " "$OUT/narrate.txt" && break
done
sleep 3
adb logcat -d -s DG_LIVE:I > "$OUT/narrate.txt" 2>&1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1
shot narrate-live
cat "$OUT/narrate.txt" | cut -c1-200
grep -qiE "(thuyet minh|doc): .*(đâu|dau|tối|toi|bạn|ban|gần|gan)" "$OUT/narrate.txt" || echo "Thuyết minh: không đọc được phụ đề tiếng Việt vẽ sẵn" >> "$OUT/narrate-fail.txt"
grep -q "DG_LIVE.*dich: " "$OUT/narrate.txt" && echo "Thuyết minh: câu tiếng Việt bị đem đi dịch" >> "$OUT/narrate-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/narrate-fail.txt"
[ -s "$OUT/narrate-fail.txt" ] || rm -f "$OUT/narrate-fail.txt"
adb shell am force-stop "$PKG"

# Dịch chữ giao diện game, thử thật: SurfaceView vẽ menu tiếng Anh -> PixelCopy -> OCR -> lọc (bỏ ID/chỉ số) -> dịch (nhật ký DG_UITR)
adb logcat -c
adb shell am start -W -n "$PKG/.LiveDemoActivity" --es mode ui >/dev/null 2>&1
for i in $(seq 1 60); do
  sleep 5
  adb logcat -d -s DG_UITR:I > "$OUT/uitr-live.txt" 2>&1
  grep -q "DG_UITR.*dich: " "$OUT/uitr-live.txt" && break
done
sleep 4
adb logcat -d -s DG_UITR:I > "$OUT/uitr-live.txt" 2>&1
shot uitr-live
cat "$OUT/uitr-live.txt"
grep -q "DG_UITR.*dich: " "$OUT/uitr-live.txt" || echo "Dịch giao diện: không ra bản dịch nào cho menu thử" >> "$OUT/uitr-live-fail.txt"
grep -q "DG_UITR.*dich: ID4521" "$OUT/uitr-live.txt" && echo "Dịch giao diện: mã ID bị đem đi dịch" >> "$OUT/uitr-live-fail.txt"
grep -q "DG_UITR.*dich: ATK 42" "$OUT/uitr-live.txt" && echo "Dịch giao diện: chỉ số viết tắt bị đem đi dịch" >> "$OUT/uitr-live-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/uitr-live-fail.txt"
[ -s "$OUT/uitr-live-fail.txt" ] || rm -f "$OUT/uitr-live-fail.txt"
adb shell am force-stop "$PKG"

# Màn chơi Remote Play (xem thử, không kết nối máy thật): thẻ "Đang tìm máy…", thanh công cụ 9 nút, khung OCR hiện rồi ẩn đi, ngang
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo adjust >/dev/null 2>&1
sleep 1; shot play-demo
sleep 1; shot play-demo-adjust
sleep 1; shot play-demo-adjust2
sleep 3; shot play-demo-hidden
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo land >/dev/null 2>&1
sleep 3; shot play-demo-land
adb logcat -d -s DG_PLAY:I > "$OUT/play.txt" 2>&1
cat "$OUT/play.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 > "$OUT/play-fail.txt"
[ -s "$OUT/play-fail.txt" ] || rm -f "$OUT/play-fail.txt"
adb shell settings put system user_rotation 0 >/dev/null 2>&1
adb shell am force-stop "$PKG"

# Thanh công cụ màn chơi khi xoay ngang (giống thanh mẫu): mở -> tự thu lại khi để yên -> chạm chấm để mở lại -> bấm "Dịch hết chữ"
# (thanh thu lại sau thao tác, dịch một lần rồi tự tắt) -> bấm nút PS. Bấm đúng các nút thật; nhật ký DG_PLAY.
# Chụp ảnh ngay khi nhật ký báo từng trạng thái (bản xem thử cách nhau vài giây nên kịp chụp).
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo tools >/dev/null 2>&1
waitlog() { for i in $(seq 1 $2); do adb logcat -d -s DG_PLAY:I > "$OUT/tools.txt" 2>&1; grep -q "$1" "$OUT/tools.txt" && return 0; sleep 0.5; done; return 1; }
waitlog "tools-dau" 40; shot tools-open
waitlog "tools-sau-5s" 40; shot tools-collapsed
waitlog "tools-mo-lai" 60; shot tools-reopened
waitlog "mot-lan chay" 60; sleep 0.5; shot tools-once
for i in $(seq 1 24); do
  adb logcat -d -s DG_PLAY:I > "$OUT/tools.txt" 2>&1
  grep -q "mot-lan da-tu-tat" "$OUT/tools.txt" && break
  sleep 5
done
cat "$OUT/tools.txt"
grep -q "tools-dau open=true top=true handle=false land=true" "$OUT/tools.txt" || echo "Thanh công cụ: lúc đầu không mở đủ (hàng trên + thanh) khi xoay ngang" >> "$OUT/tools-fail.txt"
grep -q "tools-sau-5s open=false top=false handle=true land=true" "$OUT/tools.txt" || echo "Thanh công cụ: để yên 5 giây không tự thu lại hoặc không hiện chấm mở lại" >> "$OUT/tools-fail.txt"
grep -q "tools-mo-lai open=true top=true handle=false land=true" "$OUT/tools.txt" || echo "Thanh công cụ: chạm chấm không mở lại được" >> "$OUT/tools-fail.txt"
grep -q "mot-lan chay=true once=true" "$OUT/tools.txt" || echo "Dịch hết chữ: bấm nút không bật chế độ dịch một lần" >> "$OUT/tools-fail.txt"
grep -q "tools-dang-5s open=true" "$OUT/tools.txt" || echo "Thanh công cụ: tự thu quá sớm sau khi bấm" >> "$OUT/tools-fail.txt"
grep -q "tools-sau-bam open=false" "$OUT/tools.txt" || echo "Thanh công cụ: bấm Dịch hết chữ xong thanh không tự thu lại" >> "$OUT/tools-fail.txt"
grep -q "ps-tap session=false" "$OUT/tools.txt" || echo "Nút PS: bấm không có phản hồi" >> "$OUT/tools-fail.txt"
grep -q "mot-lan da-tu-tat" "$OUT/tools.txt" || echo "Dịch hết chữ: chế độ một lần không tự tắt sau khi xong hoặc khi không thấy chữ" >> "$OUT/tools-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/tools-fail.txt"
[ -s "$OUT/tools-fail.txt" ] || rm -f "$OUT/tools-fail.txt"
adb shell am force-stop "$PKG"

# Thanh công cụ khi xoay dọc, CHƯA kết nối máy chơi game: mở -> để yên thì tự thu lại -> bấm thanh kéo để mở lại -> lại tự thu lại
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo toolsp >/dev/null 2>&1
waitlog2() { for i in $(seq 1 $2); do adb logcat -d -s DG_PLAY:I > "$OUT/toolsp.txt" 2>&1; grep -q "$1" "$OUT/toolsp.txt" && return 0; sleep 0.5; done; return 1; }
waitlog2 "toolsp-dau" 40; shot toolsp-open
waitlog2 "toolsp-sau-5s " 60; shot toolsp-collapsed
waitlog2 "toolsp-mo-lai" 40; shot toolsp-reopened
waitlog2 "toolsp-sau-5s-lan-2" 60
cat "$OUT/toolsp.txt"
grep -q "toolsp-dau open=true bar=true land=false connected=false" "$OUT/toolsp.txt" || echo "Thanh công cụ (dọc): lúc đầu không mở" >> "$OUT/toolsp-fail.txt"
grep -q "toolsp-sau-5s open=false bar=false land=false" "$OUT/toolsp.txt" || echo "Thanh công cụ (dọc): để yên 5 giây không tự thu lại (khi chưa kết nối)" >> "$OUT/toolsp-fail.txt"
grep -q "toolsp-mo-lai open=true bar=true" "$OUT/toolsp.txt" || echo "Thanh công cụ (dọc): bấm thanh kéo không mở lại được" >> "$OUT/toolsp-fail.txt"
grep -q "toolsp-sau-5s-lan-2 open=false bar=false" "$OUT/toolsp.txt" || echo "Thanh công cụ (dọc): mở lại rồi để yên không tự thu lại lần nữa" >> "$OUT/toolsp-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/toolsp-fail.txt"
[ -s "$OUT/toolsp-fail.txt" ] || rm -f "$OUT/toolsp-fail.txt"
adb shell am force-stop "$PKG"

# "Dịch hết chữ" thử thật: SurfaceView vẽ menu tiếng Anh -> PixelCopy -> OCR (không loại vùng phụ đề) -> lọc -> dịch -> giữ bản dịch -> tự tắt (nhật ký DG_UITR)
adb logcat -c
adb shell am start -W -n "$PKG/.LiveDemoActivity" --es mode uionce >/dev/null 2>&1
for i in $(seq 1 60); do
  sleep 4
  adb logcat -d -s DG_UITR:I > "$OUT/uionce.txt" 2>&1
  grep -q "mot-lan: dich xong" "$OUT/uionce.txt" && break
done
sleep 1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
shot uionce-hold
for i in $(seq 1 12); do
  sleep 3
  adb logcat -d -s DG_UITR:I > "$OUT/uionce.txt" 2>&1
  grep -q "mot-lan da-tu-tat" "$OUT/uionce.txt" && break
done
shot uionce-after
cat "$OUT/uionce.txt"
grep -q "DG_UITR.*dich: " "$OUT/uionce.txt" || echo "Dịch hết chữ: không ra bản dịch nào cho menu thử" >> "$OUT/uionce-fail.txt"
grep -q "mot-lan: dich xong" "$OUT/uionce.txt" || echo "Dịch hết chữ: không báo dịch xong để chuyển sang giữ bản dịch" >> "$OUT/uionce-fail.txt"
grep -q "mot-lan da-tu-tat" "$OUT/uionce.txt" || echo "Dịch hết chữ: giữ xong không tự tắt" >> "$OUT/uionce-fail.txt"
grep -q "DG_UITR.*dich: ID4521" "$OUT/uionce.txt" && echo "Dịch hết chữ: mã ID bị đem đi dịch" >> "$OUT/uionce-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/uionce-fail.txt"
[ -s "$OUT/uionce-fail.txt" ] || rm -f "$OUT/uionce-fail.txt"
adb shell am force-stop "$PKG"

# Chỉnh khung đọc phụ đề: kéo quá to -> bấm Đặt lại -> bấm Xong (bấm đúng các nút thật; nhật ký DG_PLAY)
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo adjust2 >/dev/null 2>&1
# Mốc của bản xem thử: 1,2s bật chỉnh khung · 2,2s kéo quá to · 12s bấm Đặt lại · 20s bấm Xong · 20,7s ghi nhật ký
sleep 5; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; shot adjust-big
sleep 6; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; shot adjust-reset
sleep 10
adb logcat -d -s DG_PLAY:I > "$OUT/adjust.txt" 2>&1
cat "$OUT/adjust.txt"
grep -q "to khung=0.0,0.02,1.0,1.0 thanh=true" "$OUT/adjust.txt" || echo "Chỉnh khung: không thấy thanh Xong/Đặt lại khi đang chỉnh" >> "$OUT/adjust-fail.txt"
grep -q "dat-lai khung=0.05,0.7,0.95,0.97" "$OUT/adjust.txt" || echo "Chỉnh khung: nút Đặt lại không đưa khung về mặc định" >> "$OUT/adjust-fail.txt"
grep -q "sau-xong dang-chinh=false thanh=false" "$OUT/adjust.txt" || echo "Chỉnh khung: nút Xong không kết thúc chỉnh khung" >> "$OUT/adjust-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/adjust-fail.txt"
[ -s "$OUT/adjust-fail.txt" ] || rm -f "$OUT/adjust-fail.txt"
adb shell am force-stop "$PKG"

# Bảng Cài đặt trong màn chơi (Dịch/Thuyết minh, giọng đọc, chất lượng hình): chụp đầu bảng rồi cuộn xuống
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo settings >/dev/null 2>&1
sleep 4; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; shot settings-1
adb shell am force-stop "$PKG"
# Thuyết minh + giọng AI offline (chưa tải gói giọng)
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo settings --es dg_engine offline --ez dg_narrate true >/dev/null 2>&1
sleep 4; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; shot settings-narrate
adb shell am force-stop "$PKG"
# Dịch + giọng cơ bản của máy
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo settings --es dg_engine system --ez dg_narrate false >/dev/null 2>&1
sleep 4; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; shot settings-system
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/settings-fail.txt"
[ -s "$OUT/settings-fail.txt" ] || rm -f "$OUT/settings-fail.txt"
adb shell am force-stop "$PKG"
set_prefs ci/prefs-full.xml

# Xoay màn hình liên tiếp trong màn chơi: Activity và SurfaceView phải giữ nguyên (không tạo lại), không văng
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo spin >/dev/null 2>&1
sleep 2; shot spin-1
sleep 2; shot spin-2
sleep 6
adb logcat -d -s DG_PLAY:I > "$OUT/spin.txt" 2>&1
cat "$OUT/spin.txt"
shot spin-end
tao=$(grep -c "tao man choi" "$OUT/spin.txt" || true)
huy=$(grep -c "huy man choi" "$OUT/spin.txt" || true)
xoay=$(grep -c " xoay land=" "$OUT/spin.txt" || true)
sv_tao=$(grep -c "surface-tao" "$OUT/spin.txt" || true)
sv_huy=$(grep -c "surface-huy" "$OUT/spin.txt" || true)
echo "spin: activity tao=$tao huy=$huy; xoay=$xoay; surface tao=$sv_tao huy=$sv_huy"
[ "$tao" = "1" ] || echo "Xoay màn hình: Activity bị tạo $tao lần (phải đúng 1)" >> "$OUT/spin-fail.txt"
[ "$huy" = "0" ] || echo "Xoay màn hình: Activity bị huỷ $huy lần (phải 0)" >> "$OUT/spin-fail.txt"
[ "$xoay" -ge 4 ] || echo "Xoay màn hình: chỉ nhận $xoay lần đổi hướng (cần ít nhất 4)" >> "$OUT/spin-fail.txt"
[ "$sv_huy" = "0" ] || echo "Xoay màn hình: surface video bị huỷ $sv_huy lần (phải 0)" >> "$OUT/spin-fail.txt"
[ "$sv_tao" = "1" ] || echo "Xoay màn hình: surface video bị tạo $sv_tao lần (phải đúng 1)" >> "$OUT/spin-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/spin-fail.txt"
[ -s "$OUT/spin-fail.txt" ] || rm -f "$OUT/spin-fail.txt"
adb shell am force-stop "$PKG"

# Lớp phủ dịch chữ giao diện (vẽ vài khối chữ giả lên hình đen: chỉ kiểm tra vẽ/bố cục/không văng, KHÔNG kiểm tra đọc chữ OCR)
adb logcat -c
adb shell am start -W -n "$PKG/.PsPlayActivity" --es dg_demo uitr >/dev/null 2>&1
sleep 4
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1  # tắt hộp thoại "launcher không phản hồi" của máy giả lập nếu có
sleep 1; shot uitr-demo
adb logcat -d -s DG_PLAY:I DG_UITR:I > "$OUT/uitr.txt" 2>&1
cat "$OUT/uitr.txt"
grep -q "lop-phu-giao-dien da ve" "$OUT/uitr.txt" || echo "Lớp phủ dịch giao diện: không vẽ được" >> "$OUT/uitr-fail.txt"
grep -q "DG_UITR.*ve 5 khoi" "$OUT/uitr.txt" || echo "Lớp phủ dịch giao diện: đã đặt 5 khối nhưng View không vẽ (kích thước 0, bị xoá hoặc không hiện)" >> "$OUT/uitr-fail.txt"
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/uitr-fail.txt"
[ -s "$OUT/uitr-fail.txt" ] || rm -f "$OUT/uitr-fail.txt"
adb shell am force-stop "$PKG"

# Bảng "Chất lượng hình" (độ phân giải, FPS, codec, tốc độ mạng, hướng màn hình): chụp đầu bảng rồi cuộn xuống
adb logcat -c
adb shell am start -W -n "$PKG/.PsConnectActivity" --es dg_demo quality >/dev/null 2>&1
sleep 4; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; shot quality-1
swipe_up; shot quality-2
swipe_up; shot quality-3
adb logcat -d 2>&1 | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 >> "$OUT/quality-fail.txt"
[ -s "$OUT/quality-fail.txt" ] || rm -f "$OUT/quality-fail.txt"
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
start_page voice; shot offline-chua-tai
adb shell settings put global hide_error_dialogs 1 2>/dev/null || true  # ẩn hộp thoại "không phản hồi" của launcher giả lập khỏi ảnh chụp
adb shell settings put secure immersive_mode_confirmations confirmed 2>/dev/null || true
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.SettingsActivity" --es page voice --ez selftest true >/dev/null 2>&1
for i in $(seq 1 90); do
  sleep 5
  adb logcat -d -s DG_SELFTEST:I > "$OUT/selftest.txt" 2>&1
  grep -qE "DG_SELFTEST.*(OK|FAIL)" "$OUT/selftest.txt" && break
done
cat "$OUT/selftest.txt"
grep -qE "DG_SELFTEST.*OK" "$OUT/selftest.txt" || echo "Giọng offline không chạy được trên máy giả lập" > "$OUT/selftest-fail.txt"
adb exec-out run-as "$PKG" cat files/selftest.wav > "$OUT/selftest.wav" 2>/dev/null || true
adb shell am force-stop "$PKG"
start_page voice; shot offline-da-tai

adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|dichgame" > "$OUT/logcat-filtered.txt" 2>&1
# chỉ coi là lỗi khi nhật ký crash có nội dung thật
grep -q "dichgame" "$OUT/crash.txt" || : > "$OUT/crash.txt"
ls -la "$OUT"
exit 0
