# GTA VI private translation API

This Worker reads the `dichgame-community` D1 database and exposes only GTA VI translations whose text and source provenance are both administrator-verified. Source strings, pending work and AI-unverified translations are never returned by public routes.

## Routes

- `GET /v1/gta6/health` — health check.
- `GET /v1/gta6/manifest` — total and verified counts.
- `GET /v1/gta6/lookup?id=<original-id>&text=<ocr-text>` — exact original ID first, then exact normalized text. Only rows with both `status='VERIFIED'` and `provenance_status='VERIFIED'` are returned.
- `GET /v1/gta6/lines?after=<numeric-cursor>&limit=200` — paginated verified rows for offline pack download.

## Storage

Apply `migrations/0001_gta6_localization.sql` to the existing `dichgame-community` D1 database. The migration creates an isolated table and indexes; it does not modify the existing community translation tables.

## App integration

The Android app calls the lookup route only when the selected game is Grand Theft Auto VI and the source language is English. The optional `translateFullById` entry point lets a future game-text hook supply a stable engine string ID. Current OCR-only calls do not know the engine ID and therefore use exact text lookup; they do not pretend to have matched an ID.

## Rights and publishing

No GTA VI source workbook or translated dialogue is committed here. The API serves only rows explicitly marked `VERIFIED`. Once served to clients, any returned translation can technically be copied from network traffic or app memory; this is private hosting, not DRM or guaranteed non-extractability.
