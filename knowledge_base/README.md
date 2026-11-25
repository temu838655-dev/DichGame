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

## Next phases

1. Legal-format importers (CSV, JSON, PO, XLIFF, TMX and supported game text formats).
2. OCR correction and conservative fuzzy matching.
3. Translation-memory orchestration and duplicate-request suppression.
4. Provider-neutral AI fallback with budget controls and retries.
5. Signed incremental packs, synchronization and backup recovery.
6. Community review workflow and performance/quality benchmarks.

This initial package does not claim that those later phases are already complete.
