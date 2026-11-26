# GTA 6 Localization — scope and verification

Updated: 2026-11-26 (UTC+8)

## Important platform limitation
No GTA 6 executable, capture session, licensed game assets, or console hardware was available to this repository CI session. The project can process screenshots/audio from supported capture paths, but this document does not claim GTA 6-specific compatibility or validated terminology coverage.

## Localization requirements
- Preserve character, location, vehicle, mission and item names unless a reviewed glossary entry explicitly localizes them.
- Keep dialogue tone and punctuation; do not invent or omit content.
- Treat subtitle/dialogue, mission/objective, phone/SMS, email, menu, map prompt and system notification as distinct UI regions.
- Do not place phone/objective text in the dialogue subtitle region.
- Preserve Unicode Vietnamese combining marks and render with a font containing the required glyphs.
- Fit translated text to its source region with wrapping and bounded font scaling; never strip Vietnamese diacritics.

## Current source capabilities observed
- OCR returns text blocks with bounding boxes and line counts.
- DialogueSplitter orders boxed blocks vertically, retains multiline block text and joins geometrically adjacent dialogue blocks.
- CaptureService has a bounded translation queue, stale-result suppression, subtitle fusion and an independent menu/region overlay path.
- Existing glossary/catalog metadata is not evidence of complete GTA 6 dialogue coverage.

## Validation status
| Scenario | Status | Evidence / limitation |
|---|---|---|
| 1–3 OCR lines | Partial source support | DialogueSplitter handles multiline blocks; add/execute three-line regression corpus |
| Mission/objective separation | Partial | UI labels are excluded from speaker parsing; per-region placement needs captured game frames |
| Phone/SMS/email region | Not verified | Requires annotated screenshots and renderer routing test |
| Character/name preservation | Partial | Speaker book and glossary exist; GTA 6 glossary not validated |
| Vietnamese Unicode on TV | Not verified on hardware | Requires Android TV/TV font and resolution matrix |
| GTA 6 real gameplay | Not tested | No game/device/capture session available |

## Test corpus to add
Use synthetic, non-copyrighted screenshots with annotated bounding boxes for: three-line dialogue, speaker label + dialogue, mission title + objective, phone message, email, menu selection, bright/dark backgrounds, outlined text, and 16:9/21:9/4:3 layouts. Record source text, expected text, region class, bounding boxes and expected Vietnamese output.
