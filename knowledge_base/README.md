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
8. Community submissions with contributor attribution, authorized human review,
   rejection, report queues, moderation history and a local maintainer CLI.
   See [MODERATION.md](MODERATION.md).
9. Integrity-checked SQLite backup/restore, HMAC-signed offline packs and
   verified-only CSV export compatible with the Android app's existing importer.
   See [BACKUP_SYNC.md](BACKUP_SYNC.md).

10. Permission-aware game catalog registry and metadata-feed importer, with per-platform localization release records and link-only handling for sources without explicit integration rights. See [CATALOG.md](CATALOG.md). This does not scrape sites or download patch archives.

## GTA VI private cloud import

The Android app uses the read-only Cloudflare Worker API at `https://dichgame-gta6-api.temu838655.workers.dev` for verified GTA VI English-to-Vietnamese exact-text lookup. The API also accepts an optional original string ID, which takes priority when a future game hook can provide one. OCR-only calls do not know that ID and therefore use exact text lookup.

The existing private admin Worker stages and uploads workbook batches into the separate `gta6_localization` table, retaining the declared source name, license identifier, URL and attribution. Source permission is stored as `DECLARED` until an administrator approves it alongside the translation. No source workbook or dialogue dataset is committed to this public repository. Only rows with both translation and provenance status `VERIFIED` are returned by the public lookup API.

To stage the workbook or translate blank Vietnamese cells locally, set `DICHGAME_ADMIN_TOKEN` to the existing DịchGame Worker `ADMIN_TOKEN`. For Gemini translation also set `GEMINI_API_KEY` and `GEMINI_MODEL`, then run:

```bash
python -m pip install -r knowledge_base/requirements.txt
python -m knowledge_base.gta6_cloud_sync --file ./GTA6_text_EN.xlsx --source-name "GTA6_text_EN.xlsx" --license-id "PERMISSION-GRANTED" --confirm-rights --max-rows 50
```

That command stages source rows only. To translate the blank cells with Gemini, add `--translate`. Repeat the command to resume; rows already `AI_UNVERIFIED` or `VERIFIED` are skipped. Use `--max-rows 0` only when intentionally processing every remaining row. `--translate` sends up to 10 source lines per Gemini request by default (configurable with `--translation-batch-size`), preserving IDs and checking timing tags, placeholders and numbers before upload. API quota and cost can still be substantial; start with a small batch. AI output remains unverified until a human reviewer approves it in the authenticated `/admin` dashboard.

## Remaining phases

1. Batch-oriented Gemini request packing, provider quota/cost controls and additional local-model adapters.
2. Incremental offline pack download from the GTA VI API and conflict handling.
3. Community account authentication and abuse/rate controls.
4. A game-engine hook that supplies GTA VI string IDs at runtime. OCR-only text capture does not expose those IDs yet; it uses exact normalized text lookup instead.
5. Full end-to-end benchmarks and broader Android/desktop integration.
