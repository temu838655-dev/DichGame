"""Permission-aware game and localization catalog ingestion.

A release's source_platform records where its localization originated. It is
not the gameplay target: authorized text data may be reused across consoles.
"""
from __future__ import annotations
import hashlib, json, re, sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Any

SOURCE_PLATFORMS = {"PC", "STEAM", "PS5", "PS4", "XBOX", "SWITCH", "ANDROID", "IOS", "OTHER"}
SUPPORTED_TARGETS = {"PS5", "PS4", "XBOX"}
PLATFORMS = SOURCE_PLATFORMS
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
                    source_id TEXT PRIMARY KEY, display_name TEXT NOT NULL,
                    homepage_url TEXT NOT NULL,
                    integration_mode TEXT NOT NULL CHECK (integration_mode IN ('official_api','authorized_feed','manual','link_only')),
                    metadata_sync_allowed INTEGER NOT NULL DEFAULT 0 CHECK(metadata_sync_allowed IN (0,1)),
                    content_download_allowed INTEGER NOT NULL DEFAULT 0 CHECK(content_download_allowed IN (0,1)),
                    permission_note TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS game_catalog (
                    game_id TEXT PRIMARY KEY, title TEXT NOT NULL,
                    alternative_titles_json TEXT NOT NULL DEFAULT '[]',
                    developer TEXT, publisher TEXT,
                    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
                    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
                );
                CREATE TABLE IF NOT EXISTS localization_releases (
                    release_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    game_id TEXT NOT NULL REFERENCES game_catalog(game_id) ON DELETE CASCADE,
                    platform TEXT NOT NULL,
                    source_platform TEXT NOT NULL DEFAULT 'OTHER',
                    target_platforms_json TEXT NOT NULL DEFAULT '[]',
                    region TEXT, edition TEXT, game_version TEXT, translation_version TEXT,
                    author_name TEXT, source_id TEXT NOT NULL REFERENCES catalog_sources(source_id),
                    source_url TEXT NOT NULL, license_id TEXT NOT NULL, license_url TEXT,
                    attribution TEXT, download_url TEXT,
                    download_permitted INTEGER NOT NULL DEFAULT 0 CHECK(download_permitted IN (0,1)),
                    content_sha256 TEXT,
                    status TEXT NOT NULL DEFAULT 'LINK_ONLY' CHECK(status IN ('LINK_ONLY','AVAILABLE','OUTDATED','NEEDS_REVIEW')),
                    updated_at TEXT,
                    UNIQUE(game_id, platform, region, edition, translation_version, source_id)
                );
                CREATE INDEX IF NOT EXISTS idx_localization_game_platform ON localization_releases(game_id, platform, status);
            """)
            columns = {row["name"] for row in db.execute("PRAGMA table_info(localization_releases)")}
            if "source_platform" not in columns:
                db.execute("ALTER TABLE localization_releases ADD COLUMN source_platform TEXT NOT NULL DEFAULT 'OTHER'")
                db.execute("UPDATE localization_releases SET source_platform=upper(platform)")
            if "target_platforms_json" not in columns:
                db.execute("""ALTER TABLE localization_releases ADD COLUMN target_platforms_json TEXT NOT NULL DEFAULT '[]'""")
                # Legacy rows retain only an explicitly matching console target.
                # PC/Steam provenance is not proof of console redistribution rights.
                db.execute("""UPDATE localization_releases SET target_platforms_json =
                    CASE upper(platform)
                      WHEN 'PS5' THEN '["PS5"]'
                      WHEN 'PS4' THEN '["PS4"]'
                      WHEN 'XBOX' THEN '["XBOX"]'
                      ELSE '[]'
                    END""")
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
                metadata = _boolean(source, "metadata_sync_allowed", False)
                download = _boolean(source, "content_download_allowed", False)
                if mode == "link_only" and (metadata or download):
                    raise CatalogValidationError("link_only sources cannot enable automatic sync or downloads.")
                if download and not metadata:
                    raise CatalogValidationError("Content downloads require metadata integration permission.")
                db.execute("""
                    INSERT INTO catalog_sources(source_id, display_name, homepage_url, integration_mode,
                        metadata_sync_allowed, content_download_allowed, permission_note)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(source_id) DO UPDATE SET display_name=excluded.display_name,
                        homepage_url=excluded.homepage_url, integration_mode=excluded.integration_mode,
                        metadata_sync_allowed=excluded.metadata_sync_allowed,
                        content_download_allowed=excluded.content_download_allowed,
                        permission_note=excluded.permission_note
                """, (source_id, _required(source, "display_name"), _https(source, "homepage_url"),
                      mode, int(metadata), int(download), _required(source, "permission_note")))
                count += 1
            db.commit()
        return count

    def releases_for_target(self, game_id: str, target_platform: str) -> list[dict[str, Any]]:
        """Find releases by game and supported console target, not source platform."""
        target = target_platform.strip().upper()
        if target not in SUPPORTED_TARGETS:
            raise CatalogValidationError(f"Unsupported gameplay target: {target}")
        with self.connect() as db:
            rows = db.execute("""
                SELECT r.*, s.display_name AS source_name FROM localization_releases r
                JOIN catalog_sources s ON s.source_id=r.source_id
                WHERE r.game_id=? AND r.status IN ('AVAILABLE','LINK_ONLY','NEEDS_REVIEW')
                ORDER BY CASE r.status WHEN 'AVAILABLE' THEN 0 WHEN 'NEEDS_REVIEW' THEN 1 ELSE 2 END,
                         r.updated_at DESC, r.release_id DESC
            """, (game_id,)).fetchall()
        result = []
        for row in rows:
            try:
                targets = json.loads(row["target_platforms_json"])
            except (TypeError, json.JSONDecodeError):
                continue
            if isinstance(targets, list) and target in targets:
                result.append(dict(row))
        return result

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
                game_id, title = _required(item, "game_id"), _required(item, "title")
                source_platform = (item.get("source_platform") or item.get("platform") or "").strip().upper()
                if source_platform not in SOURCE_PLATFORMS:
                    raise CatalogValidationError(f"Unsupported source platform: {source_platform or '(missing)'}")
                raw_targets = item.get("target_platforms")
                if (not isinstance(raw_targets, list) or not raw_targets or
                    any(not isinstance(x, str) or x.strip().upper() not in SUPPORTED_TARGETS for x in raw_targets)):
                    raise CatalogValidationError("target_platforms must be a non-empty list containing only PS5, PS4 and XBOX.")
                targets = sorted({x.strip().upper() for x in raw_targets})
                source_url, license_id = _https(item, "source_url"), _required(item, "license_id")
                if license_id.lower() in {"unknown", "none", "unlicensed", "all-rights-reserved", "n/a"}:
                    raise CatalogValidationError("Unknown or restrictive rights cannot be treated as reusable.")
                download_permitted = _boolean(item, "download_permitted", False)
                download_url = item.get("download_url")
                if download_permitted and not source["content_download_allowed"]:
                    raise CatalogValidationError("Source registry does not permit content downloads.")
                if download_permitted and not download_url:
                    raise CatalogValidationError("A permitted download requires a download_url.")
                if download_url and not download_permitted:
                    raise CatalogValidationError("A download_url cannot be stored unless download permission is explicit.")
                if download_url:
                    download_url = _https({"download_url": download_url}, "download_url")
                license_url = item.get("license_url")
                if license_url is not None:
                    license_url = _https({"license_url": license_url}, "license_url")
                digest = item.get("content_sha256")
                if digest is not None and (not isinstance(digest, str) or not HASH_RE.fullmatch(digest)):
                    raise CatalogValidationError("content_sha256 must be a lowercase SHA-256 hex digest.")
                status = "AVAILABLE" if download_permitted else "LINK_ONLY"
                db.execute("""
                    INSERT INTO game_catalog(game_id, title, alternative_titles_json, developer, publisher)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(game_id) DO UPDATE SET title=excluded.title,
                        alternative_titles_json=excluded.alternative_titles_json,
                        developer=COALESCE(excluded.developer, game_catalog.developer),
                        publisher=COALESCE(excluded.publisher, game_catalog.publisher),
                        updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')
                """, (game_id, title, json.dumps(item.get("alternative_titles", []), ensure_ascii=False),
                      item.get("developer"), item.get("publisher")))
                identity = (game_id, source_platform, item.get("region"), item.get("edition"),
                            item.get("translation_version"), source_id)
                exists = db.execute("""SELECT 1 FROM localization_releases
                    WHERE game_id=? AND platform=? AND region IS ? AND edition IS ?
                      AND translation_version IS ? AND source_id=? LIMIT 1""", identity).fetchone()
                if exists:
                    skipped += 1
                    continue
                db.execute("""
                    INSERT INTO localization_releases(game_id, platform, source_platform, target_platforms_json,
                        region, edition, game_version, translation_version, author_name, source_id,
                        source_url, license_id, license_url, attribution, download_url,
                        download_permitted, content_sha256, status, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (game_id, source_platform, source_platform, json.dumps(targets), item.get("region"),
                      item.get("edition"), item.get("game_version"), item.get("translation_version"),
                      item.get("author_name"), source_id, source_url, license_id, license_url,
                      item.get("attribution"), download_url, int(download_permitted), digest, status,
                      item.get("updated_at")))
                imported += 1
            db.commit()
        return CatalogImportReport(source_id, imported, skipped, feed_hash)

def _required(data: dict[str, Any], key: str) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value.strip():
        raise CatalogValidationError(f"{key} is required.")
    return value.strip()

def _boolean(data: dict[str, Any], key: str, default: bool) -> bool:
    value = data.get(key, default)
    if not isinstance(value, bool):
        raise CatalogValidationError(f"{key} must be a JSON boolean.")
    return value

def _https(data: dict[str, Any], key: str) -> str:
    from urllib.parse import urlsplit
    value = _required(data, key)
    try:
        parsed = urlsplit(value)
        valid = (parsed.scheme == "https" and bool(parsed.hostname) and
                 parsed.username is None and parsed.password is None and
                 parsed.fragment == "" and len(value) <= 2048)
    except ValueError:
        valid = False
    if not valid:
        raise CatalogValidationError(f"{key} must be a safe HTTPS URL without credentials or fragments.")
    return value
