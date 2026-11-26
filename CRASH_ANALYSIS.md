# Crash Analysis

Updated: 2026-11-26 (UTC+8)

## Scope reviewed
Android CaptureService OCR/translation queue, bitmap ownership, audio worker teardown and Android TV receiver lifecycle.

## Findings
1. TV listener and UDP discovery previously exited permanently after a socket exception because their outer loops swallowed the exception and returned.
2. TV listener created one raw thread for every accepted TCP socket with no concurrency or queue bound; a burst could exhaust memory/thread resources.
3. CaptureService has a bounded translation queue and translation watchdog; OCR bitmap input is recycled on success, failure and executor rejection paths.
4. CaptureService teardown stops capture, waits for ASR worker termination, closes PCM queue, removes handler callbacks and releases overlays/projection.
5. Device-level memory, thermal and long-session behavior remains unmeasured.

## Remediation in progress
- TV receiver restart loops and bounded worker pool implemented on branch fix/tv-receiver-resilience-2026-11-26.
- CI build/test result is pending. Do not mark the fix verified until the corresponding workflow passes.

## Diagnostics policy
- Never log Gemini/API keys, pairing tokens, request authorization headers or raw PCM.
- Prefer error class, HTTP status, operation name, elapsed time and a redacted device/session identifier.
- Keep bitmap/frame queues bounded and release native resources in finally/close paths.

## Outstanding tests
- Android arm64 low-memory soak, repeated start/stop, screen rotation, process recreation, network flap, OCR executor rejection, TTS init race, TV client flood and long-running playback.
- GTA 6, PS5/PS4/Xbox and physical TV tests require real hardware and are not simulated by JVM tests.


## Cloud OCR geometry hardening — 2026-11-26
Gemini bounding-box coordinates are untrusted model output. The previous parser multiplied arbitrary JSON doubles by image dimensions and passed the results into Rect. Non-finite, inverted or out-of-range coordinates could silently become incorrect overlay geometry. The parser now rejects invalid coordinates and clips valid boxes to the image, enforcing at least one pixel in each dimension. JVM regression coverage added; CI pending.
