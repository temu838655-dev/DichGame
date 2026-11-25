import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from knowledge_base.catalog import CatalogRepository, CatalogValidationError


class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = CatalogRepository(self.root / "catalog.sqlite")
        self.registry = self.root / "sources.json"
        self.registry.write_text(json.dumps({"sources": [
            {"source_id": "link", "display_name": "Link source",
             "homepage_url": "https://example.org", "integration_mode": "link_only",
             "metadata_sync_allowed": False, "content_download_allowed": False,
             "permission_note": "Link only"},
            {"source_id": "feed", "display_name": "Authorized feed",
             "homepage_url": "https://example.org", "integration_mode": "authorized_feed",
             "metadata_sync_allowed": True, "content_download_allowed": False,
             "permission_note": "Metadata feed authorized"}
        ]}), encoding="utf-8")
        self.repo.register_sources(self.registry)

    def tearDown(self):
        self.temp.cleanup()

    def feed(self, source_id="feed", **overrides):
        entry = {
            "game_id": "game-1", "title": "Game One", "platform": "PC",
            "target_platforms": ["PS5", "PS4", "XBOX"],
            "source_url": "https://example.org/game-one", "license_id": "PERMISSION-GRANTED",
            "translation_version": "1.0", "game_version": "1.2.3",
            "author_name": "Translator", "download_permitted": False
        }
        entry.update(overrides)
        path = self.root / "feed.json"
        path.write_text(json.dumps({"source_id": source_id, "entries": [entry]}), encoding="utf-8")
        return path

    def test_imports_authorized_metadata_only(self):
        report = self.repo.import_feed(self.feed(), confirm_authorized=True)
        self.assertEqual(report.imported, 1)
        with self.repo.connect() as db:
            row = db.execute("SELECT * FROM localization_releases").fetchone()
            self.assertEqual(row["status"], "LINK_ONLY")
            self.assertEqual(row["download_permitted"], 0)

    def test_requires_explicit_confirmation(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed())

    def test_link_only_source_cannot_be_imported_automatically(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed("link"), confirm_authorized=True)

    def test_unknown_license_is_rejected(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed(license_id="unknown"), confirm_authorized=True)

    def test_platform_is_validated(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed(platform="PS6"), confirm_authorized=True)

    def test_download_requires_source_permission(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed(download_permitted=True,
                download_url="https://example.org/patch.zip"), confirm_authorized=True)

    def test_permission_flags_must_be_json_booleans(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed(download_permitted="false"), confirm_authorized=True)

    def test_unpermitted_download_url_is_rejected(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed(download_url="https://example.org/patch.zip"),
                                  confirm_authorized=True)

    def test_urls_reject_credentials_and_fragments(self):
        bad = self.root / "bad-sources.json"
        bad.write_text(json.dumps({"sources": [{
            "source_id": "bad", "display_name": "Bad source",
            "homepage_url": "https://user:pass@example.org/path",
            "integration_mode": "link_only", "metadata_sync_allowed": False,
            "content_download_allowed": False, "permission_note": "Test"
        }]}), encoding="utf-8")
        with self.assertRaises(CatalogValidationError):
            self.repo.register_sources(bad)

    def test_pc_localization_source_is_available_to_all_supported_consoles(self):
        report = self.repo.import_feed(self.feed(source_platform="PC"), confirm_authorized=True)
        self.assertEqual(report.imported, 1)
        for target in ("PS5", "PS4", "XBOX"):
            releases = self.repo.releases_for_target("game-1", target)
            self.assertEqual(len(releases), 1)
            self.assertEqual(releases[0]["source_platform"], "PC")
            self.assertEqual(json.loads(releases[0]["target_platforms_json"]), ["PS4", "PS5", "XBOX"])

    def test_target_platforms_can_limit_a_source_to_one_console(self):
        self.repo.import_feed(self.feed(source_platform="STEAM", target_platforms=["PS5"]), confirm_authorized=True)
        self.assertEqual(len(self.repo.releases_for_target("game-1", "PS5")), 1)
        self.assertEqual(self.repo.releases_for_target("game-1", "PS4"), [])

    def test_non_console_gameplay_target_is_rejected(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.releases_for_target("game-1", "PC")

    def test_invalid_target_platform_in_feed_is_rejected(self):
        with self.assertRaises(CatalogValidationError):
            self.repo.import_feed(self.feed(target_platforms=["PC"]), confirm_authorized=True)

    def test_legacy_catalog_migration_does_not_assume_cross_platform_rights(self):
        legacy_path = self.root / "legacy.sqlite"
        with sqlite3.connect(legacy_path) as db:
            db.executescript("""
                PRAGMA foreign_keys=ON;
                CREATE TABLE catalog_sources (
                    source_id TEXT PRIMARY KEY, display_name TEXT NOT NULL, homepage_url TEXT NOT NULL,
                    integration_mode TEXT NOT NULL, metadata_sync_allowed INTEGER NOT NULL DEFAULT 0,
                    content_download_allowed INTEGER NOT NULL DEFAULT 0, permission_note TEXT NOT NULL
                );
                CREATE TABLE game_catalog (game_id TEXT PRIMARY KEY, title TEXT NOT NULL);
                CREATE TABLE localization_releases (
                    release_id INTEGER PRIMARY KEY AUTOINCREMENT, game_id TEXT NOT NULL REFERENCES game_catalog(game_id),
                    platform TEXT NOT NULL, region TEXT, edition TEXT, game_version TEXT, translation_version TEXT,
                    author_name TEXT, source_id TEXT NOT NULL REFERENCES catalog_sources(source_id),
                    source_url TEXT NOT NULL, license_id TEXT NOT NULL, license_url TEXT, attribution TEXT,
                    download_url TEXT, download_permitted INTEGER NOT NULL DEFAULT 0, content_sha256 TEXT,
                    status TEXT NOT NULL DEFAULT 'LINK_ONLY', updated_at TEXT,
                    UNIQUE(game_id, platform, region, edition, translation_version, source_id)
                );
                INSERT INTO catalog_sources VALUES ('legacy','Legacy','https://example.org','manual',1,0,'legacy');
                INSERT INTO game_catalog VALUES ('legacy-game','Legacy Game');
                INSERT INTO localization_releases(game_id, platform, source_id, source_url, license_id)
                    VALUES ('legacy-game','PC','legacy','https://example.org/pc','PERMISSION-GRANTED');
                INSERT INTO localization_releases(game_id, platform, source_id, source_url, license_id)
                    VALUES ('legacy-game','PS5','legacy','https://example.org/ps5','PERMISSION-GRANTED');
            """)
        migrated = CatalogRepository(legacy_path)
        self.assertEqual(migrated.releases_for_target("legacy-game", "PS5")[0]["source_platform"], "PS5")
        self.assertEqual(migrated.releases_for_target("legacy-game", "PS5")[0]["target_platforms_json"], '["PS5"]')
        self.assertEqual(migrated.releases_for_target("legacy-game", "PS4"), [])

    def test_duplicate_release_is_idempotent(self):
        first = self.repo.import_feed(self.feed(), confirm_authorized=True)
        second = self.repo.import_feed(self.feed(), confirm_authorized=True)
        self.assertEqual(first.imported, 1)
        self.assertEqual(second.skipped, 1)


if __name__ == "__main__":
    unittest.main()
