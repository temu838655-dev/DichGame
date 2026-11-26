# BUG_TRACKER.md

> Các dòng lịch sử phía trên được giữ lại. Bảng cập nhật hiện hành bên dưới supersede trạng thái cũ của cùng mã lỗi.

Cập nhật: 2026-11-26 (UTC+8)
Phạm vi: audit repository Dịch Game by Huyy Đâyy

| ID | Mức độ | Vấn đề / bằng chứng | Nguyên nhân hoặc ảnh hưởng | Trạng thái |
|---|---|---|---|---|
| SEC-001 | CRITICAL | `keystore/dichgame.jks` đã được commit trong repository public; Gradle còn fallback mật khẩu/alias mặc định. | Khóa cũ đã lộ; không thể thu hồi chứng thư đối với cài đặt sideload hiện có. | BLOCKED — cần chủ sở hữu quyết định giữ khóa trong GitHub Secrets để tương thích (chấp nhận rủi ro) hay chuyển khóa kèm migration package/dữ liệu. Workflow release mới gate bằng 4 secrets. |
| SEC-002 | HIGH | Phone manifest vẫn đặt `android:usesCleartextTraffic="true"` toàn app. PR #85 thêm chặn CloudAPI bearer credential qua HTTP và chỉ gửi PCM tới host TV private/local. | HTTP cleartext còn cần cho receiver trong LAN; chưa thể tắt global nếu chưa tách transport/đánh giá đầy đủ endpoint và backward compatibility. | OPEN — mitigation một phần đang ở PR #85; cần review/merge sau khi CI hoàn tất và thiết kế giới hạn cleartext sâu hơn. |
| QA-001 | HIGH | Smoke run 45703102492 attempt 2 kết thúc cancelled ngay ở bước “Build bản debug”; emulator chưa chạy. | CI smoke chưa có kết quả chức năng. Đã gửi rerun job mới nhất 139527252026; chờ attempt mới. | OPEN |
| QA-002 | HIGH | Chưa có bằng chứng kiểm thử Android arm64, Android TV và PS4/PS5/Xbox thật. | CI không thay thế kiểm thử phần cứng/Remote Play thực tế. | BLOCKED — cần thiết bị, console, firmware, mạng và tài khoản thực tế. |
| REL-001 | MEDIUM | Main build run 45707264385 (#1897) đang chạy; signing-gate step đã success nhưng các bước unit test/build/verify/release chưa hoàn tất. | Chưa xác minh kết luận Release bị skip khi secrets thiếu. | OPEN — kiểm tra lại run sau khi kết thúc. |
| SEC-003 | HIGH | PR #85 thay TV token/LG key/browser receiver tokens sang AES-GCM Android Keystore; thêm HTTPS validation cho CloudAPI và private-host validation cho TV PCM. | Giảm rò rỉ token và bearer credential; cần kiểm tra migration, clear/remove và tích hợp trên thiết bị. | FIXED (branch only) — PR #85 còn draft/open; JVM unit tests success, build app đang chạy. |
| DOC-001 | LOW | Tài liệu theo dõi audit đã được tạo và cập nhật. | Trạng thái được lưu trong repository. | VERIFIED |

## Quy tắc trạng thái
- OPEN: cần điều tra hoặc sửa.
- FIXED: đã thay đổi nhưng chưa có kiểm thử đủ.
- VERIFIED: có bằng chứng kiểm thử phù hợp.
- BLOCKED: cần quyết định, quyền truy cập hoặc thiết bị bên ngoài.

Không đánh dấu VERIFIED chỉ dựa trên việc source code biên dịch.


---

## Cập nhật hiện hành — 2026-11-26 06:16 UTC+8

| ID | Mức độ | Vấn đề / bằng chứng | Trạng thái hiện hành |
|---|---|---|---|
| SEC-001 | CRITICAL | keystore/dichgame.jks vẫn được track trong repository public. Xóa ở commit mới không xóa khỏi Git history. | BLOCKED — cần chủ sở hữu chọn key compatibility hay package/key migration. Không phát hành production trước quyết định. |
| SEC-002 | HIGH | usesCleartextTraffic=true toàn application; LAN TV receiver cần transport nội bộ. | OPEN — cần inventory endpoint, tách LAN/Internet policy và test backward compatibility. |
| SEC-003 | HIGH | PR #85 AES-GCM Android Keystore, HTTPS CloudAPI, private/local TV PCM host và backup exclusions. Build #1939 success. | PARTIAL — PR #85 mergeable_state=dirty; chưa merge/review xong. |
| SEC-006 | HIGH | LG WebSocket custom trust manager chấp nhận certificate không được xác minh/pinning. | OPEN — cần TOFU/pinning và xác nhận fingerprint; không merge security PR như thể đã xử lý xong. |
| SEC-005 | HIGH | PackStore.fetch trước đây để HttpURLConnection tự follow redirect, có thể cho phép HTTPS downgrade. | FIXED IN PR #86 — manual redirect, tối đa 3 hop, HTTPS-only, chặn userinfo/fragment; Android JVM bridge tests #172 success. Chờ build #1960 cuối. |
| MATCH-001 | MEDIUM | previous_sentence/next_sentence có trong API matcher nhưng bị bỏ qua; exact match có thể chọn nhầm khi câu trùng ngữ cảnh. | FIXED IN PR #86 — context score exact/fuzzy và từ chối ambiguous exact khi không có context match; Knowledge Base Tests #223–#226 success. |
| LIB-002 | HIGH | Catalog.kt có 201 tên game nhưng không phải 201 phrase packs; built-in phrase memory chỉ 58 generic + 18 specific rows trên 6 franchise groups. | OPEN — cần pack inventory thật, nguồn/license, game version và coverage evidence. |
| UI-001 | MEDIUM | Nhãn “Có gói” trước đây có thể hiểu nhầm glossary thành bộ Việt hóa hoàn chỉnh. | FIXED IN PR #86 — đổi thành “Thuật ngữ riêng”, giải thích scope rõ. Chờ Android build #1960. |
| QA-001 | HIGH | Smoke #161 vẫn chạy bước emulator. | OPEN — chưa có kết quả cuối. |
| QA-002 | HIGH | Chưa có test Android arm64, TV vật lý, PS4/PS5/Xbox thật. | BLOCKED — cần thiết bị/console/firmware/mạng thực. |
| QA-003 | HIGH | OCR/AI/ASR/TTS end-to-end chưa được xác minh trên thiết bị. | OPEN — cần test corpus và thiết bị thật. |
| REL-001 | MEDIUM | Main #1913 success nhưng public Release step skip vì signing secrets thiếu; release gần nhất build-1872. | VERIFIED (gate behavior only) — không có Release mới. |
| REL-002 | MEDIUM | PR #85 build #1939 success nhưng mergeable_state=dirty. | OPEN — resolve base conflict và review trước merge. |
| REL-003 | MEDIUM | PR #86 draft, mergeable=true; Android Build #1960 pending. | OPEN — đợi CI latest head, review và merge sau đó. |

Không đánh dấu VERIFIED cho các hạng mục chỉ mới có source code; phải có CI/runtime evidence phù hợp.


---

## CI follow-up — 2026-11-26

| ID | Latest evidence | Current state |
|---|---|---|
| QA-004 | Knowledge Base Tests #227 (45711631198) succeeded on PR head c41a6a35290c547680cac5d7b68de0378b1971c7. | VERIFIED — Python knowledge-base suite only. |
| QA-005 | Android Build #1961 (45711631124) was IN_PROGRESS; JVM/unit-test step running at last poll. | OPEN — no APK build pass claim. |
| QA-006 | Android bridge #173 (45711630980) was IN_PROGRESS; Android JVM tests running at last poll. | OPEN — no test conclusion yet. |
| QA-001 | Emulator Smoke #161 (45708105649) remains IN_PROGRESS. | OPEN — no emulator runtime conclusion. |

Do not infer Android runtime success from Python tests. Re-poll the exact run IDs before changing these statuses.


### Correction — latest CI re-poll (2026-11-26)
- QA-006 Android bridge #173 (45711630980): VERIFIED — Android JVM unit tests completed successfully.
- QA-005 Android Build #1961 (45711631124): OPEN — still running app unit tests; no APK success claim.
- QA-001 Emulator Smoke #161 (45708105649): OPEN — emulator execution still running.

## AI translation / narration regression — 2026-11-26

| ID | Severity | Root cause / evidence | Files changed | Fix | Status |
|---|---|---|---|---|---|
| AI-400-01 | HIGH | Gemini 400 path retried without safety settings for every 400, even when provider error was an invalid key/model or unrelated request error. | `Ai.kt`, `AiRetry.kt`, `AiRetryTest.kt` | Parse provider error JSON; retry without safety settings only when response explicitly rejects that field; pass useful provider details to user. | FIXED IN BRANCH — CI pending |
| AI-MODEL-01 | HIGH | Free-form model value was interpolated directly into the Gemini endpoint path; default alias was not explicitly pinned to a low-latency stable model. | `Ai.kt`, `Prefs.kt`, `GeminiRequestTest.kt` | Validate model identifier; use `gemini-3.5-flash-lite` for new installs; cap output tokens at 512. Existing saved model values remain unchanged. | FIXED IN BRANCH — live API validation pending |
| TTS-01 | HIGH | `CaptureService.showCards` removed every translation marked uncertain before creating the TTS scene. A valid non-empty translation could appear on screen but never enter narration. | `CaptureService.kt`, `TranslationSpeechPolicy.kt`, `TranslationSpeechPolicyTest.kt` | Speak every non-empty translation regardless of confidence warning; retain speaker and delivery metadata; continue skipping empty text. | FIXED IN BRANCH — device playback pending |
| TTS-02 | MEDIUM | Gemini TTS Interactions fallback retried all HTTP 400/404 responses, including invalid API key errors. | `AiSpeech.kt`, `AiRetry.kt`, `AiRetryTest.kt`, `Prefs.kt` | Validate TTS model ID; fallback only on 404 or explicit Interactions payload/endpoint incompatibility; default new installs to Flash-Lite TTS. | FIXED IN BRANCH — live TTS pending |
| LAT-01 | HIGH | Translation queue allowed up to 12 waiting jobs and could display an old result while newer dialogue was already queued. | `CaptureService.kt` | Reduce cap to 6 and discard results older than 8 seconds only when newer work is waiting. | FIXED IN BRANCH — timing benchmark pending |

Status rule: FIXED IN BRANCH is not VERIFIED. Reclassify only after CI, live API, and device evidence are available.

| TTS-03 | HIGH | `Speaker.speakSystem()` returned when Android TextToSpeech was not yet ready, silently discarding early translated lines during asynchronous engine initialization. | `Speaker.kt` | Buffer up to 5 pending system-voice utterances, suppress duplicates, drain after successful initialization, clear on stop/shutdown. | FIXED IN BRANCH — Android/device test pending |


## Enterprise reliability pass — 2026-11-26
| ID | Severity | Finding / root cause | Remediation | Status |
|---|---|---|---|---|
| TV-RELAY-01 | HIGH | TV TCP listener returned permanently after a transient socket exception. | Restart listener with bounded retry delay and explicit shutdown flag. | FIXED ON BRANCH — CI pending |
| TV-RELAY-02 | HIGH | TV UDP discovery returned permanently after a transient socket exception. | Restart discovery socket with bounded retry delay and explicit shutdown flag. | FIXED ON BRANCH — CI pending |
| TV-RELAY-03 | HIGH | One raw thread was created per accepted TV client without a concurrency/queue limit. | Bounded executor (2 core, 4 max, queue 32); reject/close overload sockets. | FIXED ON BRANCH — CI pending |
| CI-SIGN-01 | HIGH | Missing signing secrets caused public branch build to fail before unit tests and APK compilation. | Non-public branches use an ephemeral runner-local CI key and clearly labeled CI-only artifact; public Release remains blocked without private signing secrets. | FIXED ON BRANCH — CI pending |
| SEC-001 | CRITICAL | Old Android signing key/password were exposed in public Git history. | No reuse or key replacement was performed. Owner decision required; a new key is not update-compatible with existing sideload installs absent a supported key-rotation path. | BLOCKED |
| QA-GTA6-01 | HIGH | No GTA 6 game/capture hardware or annotated screenshot corpus available. | Add explicit synthetic test corpus and run on supported hardware before claiming compatibility. | BLOCKED — hardware/corpus |

| OCR-CLOUD-BOX-01 | MEDIUM | Gemini normalized coordinates were converted directly to pixel integers; malformed/NaN/out-of-range or inverted values could create invalid or misleading UI boxes. | Validate finite 0..1000 coordinates, reject inverted/empty boxes, clamp pixel bounds and guarantee minimum 1px geometry; add OcrGeometryTest. | FIXED ON BRANCH — CI pending |
| QA-OCR-3LINE-01 | LOW | Newly added 3-line test fixture encoded literal \\n rather than newline, so test failed for a fixture error. | Correct fixture to use actual line separators; retain the regression test. | FIXED ON BRANCH — CI pending |
