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

## Remaining phases

1. Provider-neutral AI adapters with budget controls, retries and timeouts.
2. Signed incremental packs, synchronization and backup recovery.
3. Community review workflow and performance/quality benchmarks.
4. Integrate the Python knowledge base into the Android/desktop app translation
   pipeline and validate end-to-end OCR-to-subtitle behavior.

The package remains a standalone Python component. The provider adapter,
application integration, sync, backups and community moderation are not yet
implemented.
