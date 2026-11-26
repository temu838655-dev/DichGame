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
2. Legal-format importers for CSV, JSON, singular PO, XLIFF, TMX and Excel `.xlsx`; source IDs are retained for exact lookup. Blank Excel target cells can be staged in the source queue for later batch translation.
3. Conservative OCR dictionary correction and bounded fuzzy matching.
4. Cache-first translation memory with context isolation, optional expiry and
   persistent duplicate-request leases. See [TRANSLATION_MEMORY.md](TRANSLATION_MEMORY.md).
5. Provider-neutral AI fallback with persistent daily request budgets, bounded
   retries, input/output limits and offline mode. See [AI_FALLBACK.md](AI_FALLBACK.md).
6. Concrete Gemini and OpenAI-compatible HTTPS adapters using Python's standard
   library. See [PROVIDERS.md](PROVIDERS.md).
7. Resumable, bounded AI translation batches for staged source strings, preserving
   exact localization IDs and marking AI output as unverified.
7. Community submissions with contributor attribution, authorized human review,
   rejection, report queues, moderation history and a local maintainer CLI.
   See [MODERATION.md](MODERATION.md).
8. Integrity-checked SQLite backup/restore, HMAC-signed offline packs and
   verified-only CSV export compatible with the Android app's existing importer.
   See [BACKUP_SYNC.md](BACKUP_SYNC.md).

9. Permission-aware game catalog registry and metadata-feed importer, with per-platform localization release records and link-only handling for sources without explicit integration rights. See [CATALOG.md](CATALOG.md). This does not scrape sites or download patch archives.

## Excel source staging

Install `knowledge_base/requirements.txt`, then stage an authorized workbook (including blank Vietnamese cells) with:

```bash
python -m knowledge_base.import_cli --db ./translations.db --game-id gta6 --file ./GTA6_text_EN.xlsx --source-name "Authorized GTA6 localization source" --license-id "PERMISSION-GRANTED" --confirm-rights --source-only
```

The source-only path stores each original `ID`, English string, sheet and group context in `source_strings`; it does not invent translations or mark any text verified. The ID lookup is available through `KnowledgeBase.lookup_external_id` after translated rows are imported.

## GTA VI source staging

The source workbook can be staged without Vietnamese text using `--source-only`; blank target cells are retained in a pending queue rather than silently discarded. Install the optional Excel dependency from `knowledge_base/requirements.txt`. To translate a bounded batch, set `GEMINI_API_KEY` and run `python -m knowledge_base.translate_batch_cli --db ./translations.db --game-id gta6 --model YOUR_ENABLED_GEMINI_MODEL --batch-size 50`. Repeat to resume. This process is not a substitute for rights review or human quality assurance.

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
