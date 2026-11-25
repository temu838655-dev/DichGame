# Release Notes — AI Translation & Narration Repair

## Unreleased — 2026-11-26

Branch: `fix/ai-translation-tts-400-latency`

### Fixed in source
- Improved Gemini HTTP error reporting with provider message, status and selected detail fields.
- Prevented generic HTTP 400 responses from triggering the safety-settings retry path.
- Validated Gemini translation and TTS model identifiers before building endpoint URLs.
- Bounded Gemini translation output to 512 tokens.
- Selected low-latency defaults for new installations: `gemini-3.5-flash-lite` translation and `gemini-3.8-flash-lite-tts` narration. Existing saved model selections are not overwritten.
- Ensured non-empty translations are eligible for narration even when marked low-confidence.
- Restricted TTS endpoint fallback so invalid API-key responses do not cause an unnecessary second request.
- Reduced the waiting translation queue and prevented an old result from replacing current dialogue when newer work is pending.

### Tests added
- Gemini error-detail extraction and safety-settings fallback classification.
- TTS Interactions fallback classification.
- Gemini generateContent request JSON and output-token bound.
- Narration eligibility for uncertain and empty translations.

### Validation status
- GitHub Actions: pending for this branch.
- Real Gemini API: not verified; requires a valid API key with access to the selected models.
- Android device TTS/audio focus/background behavior: not verified.
- APK Release: not published. Do not treat this unreleased branch as a production build.
