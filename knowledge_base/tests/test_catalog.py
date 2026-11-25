import json
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

    def test_duplicate_release_is_idempotent(self):
        first = self.repo.import_feed(self.feed(), confirm_authorized=True)
        second = self.repo.import_feed(self.feed(), confirm_authorized=True)
        self.assertEqual(first.imported, 1)
        self.assertEqual(second.skipped, 1)


if __name__ == "__main__":
    unittest.main()
