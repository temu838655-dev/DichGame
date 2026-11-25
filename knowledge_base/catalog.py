"""Permission-aware game and localization catalog ingestion.

This module imports curated/authorized JSON metadata feeds only. It does not
scrape websites, download patch archives, bypass gates, or rehost translations.
"""
from __future__ import annotations

import hashlib
import json
import re
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PLATFORMS = {"PC", "STEAM", "PS5", "PS4", "XBOX", "SWITCH", "ANDROID", "IOS"}
INTEGRATION_MODES = {"official_api", "authorized_feed", "manual", "link_only"}
HASH_RE = re.compile(r"^[0-9a-f]{64}$")


class CatalogValidationError(ValueError):
    """Raised when catalog metadata is incomplete or not authorized."""


@dataclass(frozen=True)
class CatalogImportReport:
    source_id: str
    imported: int
    skipped: int
    feed_sha256: str


class CatalogRepository:
    def __init__(self, database_path: str | Path):
        self.database_path = str(database_path)
        with self.connect() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS catalog_sources (
                    source_id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    homepage_url TEXT NOT NULL,
                    integration_mode TEXT NOT NULL CHECK (
                        integration_mode IN ('official_api','authorized_feed','manual','link_only')
                    ),
                    metadata_sync_allowed INTEGER NOT NULL DEFAULT 0 CHECK(metadata_sync_allowed IN (0,1)),
                    content_download_allowed INTEGER NOT NULL DEFAULT 0 CHECK(content_download_allowed IN (0,1)),
                    permission_note TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS game_catalog (
                    game_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    alternative_titles_json TEXT NOT NULL DEFAULT '[]',
                    developer TEXT,
                    publisher TEXT,
                    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
                    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
                );
                CREATE TABLE IF NOT EXISTS localization_releases (
                    release_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    game_id TEXT NOT NULL REFERENCES game_catalog(game_id) ON DELETE CASCADE,
                    platform TEXT NOT NULL,
                    region TEXT,
                    edition TEXT,
                    game_version TEXT,
                    translation_version TEXT,
                    author_name TEXT,
                    source_id TEXT NOT NULL REFERENCES catalog_sources(source_id),
                    source_url TEXT NOT NULL,
                    license_id TEXT NOT NULL,
                    license_url TEXT,
                    attribution TEXT,
                    download_url TEXT,
                    download_permitted INTEGER NOT NULL DEFAULT 0 CHECK(download_permitted IN (0,1)),
                    content_sha256 TEXT,
                    status TEXT NOT NULL DEFAULT 'LINK_ONLY' CHECK(status IN ('LINK_ONLY','AVAILABLE','OUTDATED','NEEDS_REVIEW')),
                    updated_at TEXT,
                    UNIQUE(game_id, platform, region, edition, translation_version, source_id)
                );
                CREATE INDEX IF NOT EXISTS idx_localization_game_platform
                    ON localization_releases(game_id, platform, status);
            """)
            db.commit()

    def connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.database_path, timeout=15)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA busy_timeout=15000")
        return db

    def register_sources(self, registry_path: str | Path) -> int:
        payload = json.loads(Path(registry_path).read_text(encoding="utf-8"))
        sources = payload.get("sources") if isinstance(payload, dict) else None
        if not isinstance(sources, list):
            raise CatalogValidationError("Source registry must contain a sources array.")
        count = 0
        with self.connect() as db:
            for source in sources:
                if not isinstance(source, dict):
                    raise CatalogValidationError("Each source definition must be an object.")
                source_id = _required(source, "source_id")
                mode = _required(source, "integration_mode")
                if mode not in INTEGRATION_MODES:
                    raise CatalogValidationError(f"Unsupported integration mode for {source_id}.")
                metadata = bool(source.get("metadata_sync_allowed", False))
                download = bool(source.get("content_download_allowed", False))
                if mode == "link_only" and (metadata or download):
                    raise CatalogValidationError("link_only sources cannot enable automatic sync or downloads.")
                if download and not metadata:
                    raise CatalogValidationError("Content downloads require metadata integration permission.")
                db.execute("""
                    INSERT INTO catalog_sources(
                        source_id, display_name, homepage_url, integration_mode,
                        metadata_sync_allowed, content_download_allowed, permission_note
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(source_id) DO UPDATE SET
                        display_name=excluded.display_name, homepage_url=excluded.homepage_url,
                        integration_mode=excluded.integration_mode,
                        metadata_sync_allowed=excluded.metadata_sync_allowed,
                        content_download_allowed=excluded.content_download_allowed,
                        permission_note=excluded.permission_note
                """, (source_id, _required(source, "display_name"), _https(source, "homepage_url"),
                      mode, int(metadata), int(download), _required(source, "permission_note")))
                count += 1
            db.commit()
        return count

    def import_feed(self, feed_path: str | Path, *, confirm_authorized: bool = False) -> CatalogImportReport:
        if not confirm_authorized:
            raise CatalogValidationError("Confirm that this metadata feed is authorized before importing.")
        raw = Path(feed_path).read_bytes()
        try:
            payload = json.loads(raw.decode("utf-8-sig"))
        except (UnicodeError, json.JSONDecodeError) as exc:
            raise CatalogValidationError("Catalog feed must be valid UTF-8 JSON.") from exc
        if not isinstance(payload, dict) or not isinstance(payload.get("entries"), list):
            raise CatalogValidationError("Catalog feed must contain an entries array.")
        source_id = _required(payload, "source_id")
        feed_hash = hashlib.sha256(raw).hexdigest()
        with self.connect() as db:
            source = db.execute("SELECT * FROM catalog_sources WHERE source_id=?", (source_id,)).fetchone()
            if source is None:
                raise CatalogValidationError("Source is not present in the approved source registry.")
            if not source["metadata_sync_allowed"] or source["integration_mode"] == "link_only":
                raise CatalogValidationError("This source is link-only; automatic metadata import is not permitted.")
            imported = skipped = 0
            for item in payload["entries"]:
                if not isinstance(item, dict):
                    raise CatalogValidationError("Each catalog entry must be an object.")
                game_id = _required(item, "game_id")
                title = _required(item, "title")
                platform = _required(item, "platform").upper()
                if platform not in PLATFORMS:
                    raise CatalogValidationError(f"Unsupported platform: {platform}")
                source_url = _https(item, "source_url")
                license_id = _required(item, "license_id")
                if license_id.lower() in {"unknown", "none", "unlicensed", "all-rights-reserved", "n/a"}:
                    raise CatalogValidationError("Unknown or restrictive rights cannot be treated as reusable.")
                download_permitted = bool(item.get("download_permitted", False))
                download_url = item.get("download_url")
                if download_permitted and not source["content_download_allowed"]:
                    raise CatalogValidationError("Source registry does not permit content downloads.")
                if download_permitted and not download_url:
                    raise CatalogValidationError("A permitted download requires a download_url.")
                if download_url:
                    download_url = _https({"download_url": download_url}, "download_url")
                digest = item.get("content_sha256")
                if digest is not None and (not isinstance(digest, str) or not HASH_RE.fullmatch(digest)):
                    raise CatalogValidationError("content_sha256 must be a lowercase SHA-256 hex digest.")
                status = "AVAILABLE" if download_permitted else "LINK_ONLY"
                db.execute("""
                    INSERT INTO game_catalog(game_id, title, alternative_titles_json, developer, publisher)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(game_id) DO UPDATE SET
                        title=excluded.title, alternative_titles_json=excluded.alternative_titles_json,
                        developer=COALESCE(excluded.developer, game_catalog.developer),
                        publisher=COALESCE(excluded.publisher, game_catalog.publisher),
                        updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')
                """, (game_id, title, json.dumps(item.get("alternative_titles", []), ensure_ascii=False),
                      item.get("developer"), item.get("publisher")))
                cursor = db.execute("""
                    INSERT OR IGNORE INTO localization_releases(
                        game_id, platform, region, edition, game_version, translation_version,
                        author_name, source_id, source_url, license_id, license_url, attribution,
                        download_url, download_permitted, content_sha256, status, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (game_id, platform, item.get("region"), item.get("edition"), item.get("game_version"),
                      item.get("translation_version"), item.get("author_name"), source_id, source_url,
                      license_id, item.get("license_url"), item.get("attribution"), download_url,
                      int(download_permitted), digest, status, item.get("updated_at")))
                if cursor.rowcount:
                    imported += 1
                else:
                    skipped += 1
            db.commit()
        return CatalogImportReport(source_id, imported, skipped, feed_hash)


def _required(data: dict[str, Any], key: str) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value.strip():
        raise CatalogValidationError(f"{key} is required.")
    return value.strip()


def _https(data: dict[str, Any], key: str) -> str:
    value = _required(data, key)
    if not value.startswith("https://") or len(value) > 2048:
        raise CatalogValidationError(f"{key} must be an HTTPS URL.")
    return value
