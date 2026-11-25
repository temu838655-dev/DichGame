# Multi-game localization knowledge base

This package is the first implementation slice of the shared game-translation
repository. It is deliberately independent of Android UI and AI vendors.

## Phase 1 scope

- SQLite schema for games, source provenance/licensing, translations, glossary,
  characters, OCR correction dictionary, cache, feedback and import jobs.
- SQLite FTS5 index with insert/update/delete triggers.
- Exact and normalized lookup, game-scoped full-text discovery and context-aware
  cache keys.
- WAL mode, foreign keys, busy timeout and transaction boundaries.
- Standard-library Python only; no API key, network request or generated sample
  translation data is required.

## Run tests

From the repository root:

```bash
python -m unittest discover -s knowledge_base/tests -v
```

SQLite must be built with FTS5 support. The module fails during initialization
rather than silently degrading to a slower or incomplete search mode.

## Data licensing

Every imported source must have an explicit `license_id`. Use a value such as
`CC-BY-4.0`, `MIT`, `GPL-3.0-only`, or `PERMISSION-GRANTED` only when the
source actually grants that permission. Do not ingest content with unknown or
incompatible rights. Keep author attribution and the original source URL.

## Status model

- `VERIFIED`: reviewed by an authorized human reviewer.
- `COMMUNITY`: community-submitted and awaiting verification.
- `AI_REVIEWED`: AI output that passed configured checks/review.
- `AI_UNVERIFIED`: AI output not yet verified.
- `REJECTED`: excluded from normal lookup.

## Implemented phases

1. SQLite knowledge base, provenance, licensing fields and FTS5.
2. Legal-format importers for CSV, JSON, singular PO, XLIFF and TMX.
3. Conservative OCR dictionary correction and bounded fuzzy matching.
4. Cache-first translation memory with context isolation, optional expiry and
   persistent duplicate-request leases. See [TRANSLATION_MEMORY.md](TRANSLATION_MEMORY.md).
5. Provider-neutral AI fallback with persistent daily request budgets, bounded
   retries, input/output limits and offline mode. See [AI_FALLBACK.md](AI_FALLBACK.md).
6. Concrete Gemini and OpenAI-compatible HTTPS adapters using Python's standard
   library. See [PROVIDERS.md](PROVIDERS.md).
7. Community submissions, authorized human review, rejection, report queues and
   moderation history. See [MODERATION.md](MODERATION.md).
8. Integrity-checked SQLite backup/restore, HMAC-signed offline packs and
   verified-only CSV export compatible with the Android app's existing importer.
   See [BACKUP_SYNC.md](BACKUP_SYNC.md).

## Remaining phases

1. Additional local-model adapters and provider-specific quota/pricing controls.
2. Network synchronization service, incremental delta protocol and conflict UI.
3. Community account authentication, abuse/rate controls and reviewer administration.
4. Performance/quality benchmarks and automatic in-process Android/desktop app
   integration through OCR, translation, subtitle display and audio. A verified-
   only CSV bridge to the app's existing import screen is available now.

The package remains a standalone Python component. Hosted sync service, account
authentication, reviewer administration and end-to-end Android/desktop app
integration are not yet implemented.
