# Backup and offline synchronization

## Local SQLite backup

```python
from knowledge_base import create_backup, restore_backup, validate_backup

create_backup("translations.db", "backups/translations-2026-11-25.db")
validate_backup("backups/translations-2026-11-25.db")

# Stop the app and close all connections before restoring:
restore_backup("backups/translations-2026-11-25.db", "translations.db")
```

Backup uses SQLite's online backup API, then checks database integrity.
Restore validates the source and required schema tables before atomically
replacing the destination. Keep multiple dated backups and store copies on a
separate device or drive. Restore must be performed while the application has
closed all connections to the destination database.

## Signed per-game pack

```python
from knowledge_base import KnowledgeBase, export_game_pack, import_game_pack

kb = KnowledgeBase("translations.db")
secret = load_secret_from_os_key_store()  # at least 32 random bytes
export_game_pack(kb, "my-game", secret, "exports/my-game.json")
counts = import_game_pack(kb, "exports/from-another-device.json", secret)
```

The HMAC-SHA256 signature detects tampering and wrong keys. Packs contain a
per-game snapshot of source provenance, translations, glossary, characters and
OCR dictionary. Import skips duplicate translations and glossary/character/OCR
keys. Source records are re-created as PENDING on the receiving database.
Verified translations are downgraded to COMMUNITY by default; preserve VERIFIED
only when the signing key belongs to a trusted reviewer-controlled device and
the caller explicitly passes `allow_verified=True`.

Keep the signing key secret and distribute it only to trusted devices. Anyone
who obtains the shared key can create packs that pass signature verification.
Do not hard-code the key in source code or commit it to Git.

## Scope and limitations

- This is offline file-based transfer, not a hosted sync server.
- Packs are full snapshots, not incremental deltas; conflict resolution is
  conservative skip-on-duplicate, not a merge UI.
- The signing key is shared-secret HMAC, not public-key signatures or user
  identity proof.
- Do not use untrusted packs with `allow_verified=True`.
- API keys, cache entries, daily AI usage counters, feedback and moderation
  reports are not exported in game packs.


## Export a verified pack for the Android app

The Android app already supports importing a scoped CSV file from its existing
Import Pack screen. To bridge this Python knowledge base to that importer:

```python
from knowledge_base import KnowledgeBase, export_android_csv

kb = KnowledgeBase("translations.db")
export_android_csv(kb, "my-game", "exports/my-game-verified.csv")
```

The export uses the app's `source,vi,game` CSV header. It includes only
`VERIFIED` translations, and if a translation points to an imported source,
that source must also have `verification_status='VERIFIED'`. AI-unverified
and pending community entries are deliberately omitted. Transfer the CSV to
the phone and import it with the app's existing file-import flow.

This is a file-based bridge, not automatic live synchronization. The Python
knowledge base still does not run inside the Android app process.
