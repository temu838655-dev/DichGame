# DịchGame community Worker

This is the source for the existing `dichgame-community` Cloudflare Worker. It shares the existing D1 database binding (`DB`) and the pre-existing `ADMIN_TOKEN` / `CLIENT_TOKEN` secrets; secret values are never stored in this repository.

The Worker keeps its original community endpoints and adds these authenticated administrator routes for GTA VI:

- `POST /v1/admin/gta6/check` — look up existing statuses for up to 100 original IDs.
- `POST /v1/admin/gta6/batch` — stage source rows or store AI-generated translations in batches of up to 30; rows with translations are marked `AI_UNVERIFIED`, not verified.
- `GET /v1/admin/gta6/pending` — list up to 100 pending or AI-unverified rows.
- `POST /v1/admin/gta6/review` — an administrator can approve or reject a translation. Approval is refused when the Vietnamese cell is empty.

All these routes require the existing `ADMIN_TOKEN`. The importer requires an explicit `--confirm-rights` flag; this records the submitter's declaration but is not independent legal verification.

The public, read-only lookup is a separate Worker in `../dichgame-gta6-api/` and returns only `VERIFIED` translations.
