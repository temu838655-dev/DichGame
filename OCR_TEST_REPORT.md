# OCR Test Report

Updated: 2026-11-26 (UTC+8)

## Current implementation
- ML Kit on-device OCR is used for supported scripts; Gemini image OCR is used for configured cloud-only languages.
- OCR results retain block bounding boxes, confidence and line count.
- CaptureService compares stable frame signatures and waits for repeated OCR observations before translating.
- DialogueSplitter orders boxed blocks top-to-bottom and joins multiline/adjacent dialogue according to geometry.
- Cloud OCR images are scaled to a maximum 1280 px edge and input bitmaps are recycled after processing.

## Known verification gaps
- No real GTA 6 screenshots/video corpus is present.
- No completed emulator smoke-test result is available in the currently observed run history.
- Three-line subtitle accuracy, outlined text, bright/dark background contrast and OCR latency need a deterministic image corpus.
- Cloud OCR requires a valid user Gemini key and network access; CI cannot prove provider runtime behavior.

## Required regression matrix
| Case | Expected result | Status |
|---|---|---|
| Single line | Exact text and geometry | Existing unit coverage partial |
| Two lines | Both lines in reading order | Existing DialogueSplitter test |
| Three lines | All lines retained in reading order | Needs dedicated regression test |
| Split blocks | Adjacent lines join only when geometry agrees | Existing geometry logic |
| Separate UI regions | Never merge unrelated menu/objective/dialogue | Needs annotated region corpus |
| Duplicate frames | No duplicate translation request | Existing frame/signature tests partial |
| Invalid cloud coordinates | Reject or clamp NaN, infinity, inverted and out-of-range boxes | Must be verified against hardened branch |
| Bitmap lifecycle | Recycle exactly once on success/failure/rejection | Source reviewed; device soak needed |
| Network interruption | Recover on next scan without wedging busy state | Needs instrumentation test |

## Reporting rule
Do not mark a row PASS without a reproducible test result. Separate JVM tests, emulator tests, cloud-provider tests and physical-device tests.
