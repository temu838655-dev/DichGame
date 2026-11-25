import json
import tempfile
import unittest
from pathlib import Path

from knowledge_base.db import KnowledgeBase
from knowledge_base.sync import PackIntegrityError, export_android_csv, export_game_pack, import_game_pack


class SignedPackTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.source = KnowledgeBase(self.root / "source.db")
        self.target = KnowledgeBase(self.root / "target.db")
        self.source.add_game("pack-game", "Pack Game", "PC")
        self.key = b"test signing key with at least 32 bytes long"
        self.authorized_source_id = self.source.add_source(
            "Authorized source", "CC-BY-4.0", author_name="Translator",
            attribution="Credit translator",
        )
        self.source.add_translation(
            "pack-game", "Open inventory", "Mở túi đồ", source_id=self.authorized_source_id,
            contributor_id="translator-42", translation_status="VERIFIED",
        )
        self.pack = self.root / "game-pack.json"

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_signed_pack_import_downgrades_verified_by_default(self):
        export_game_pack(self.source, "pack-game", self.key, self.pack)
        counts = import_game_pack(self.target, self.pack, self.key)
        self.assertEqual(counts["translations"], 1)
        with self.target.connect() as db:
            status = db.execute(
                "SELECT translation_status FROM translations"
            ).fetchone()[0]
        self.assertEqual(status, "COMMUNITY")
        with self.target.connect() as db:
            contributor = db.execute("SELECT contributor_id FROM translations").fetchone()[0]
        self.assertEqual(contributor, "translator-42")

    def test_trusted_pack_can_preserve_verified_status_explicitly(self):
        export_game_pack(self.source, "pack-game", self.key, self.pack)
        import_game_pack(self.target, self.pack, self.key, allow_verified=True)
        with self.target.connect() as db:
            status = db.execute(
                "SELECT translation_status FROM translations"
            ).fetchone()[0]
        self.assertEqual(status, "VERIFIED")

    def test_tampered_pack_and_wrong_key_are_rejected(self):
        export_game_pack(self.source, "pack-game", self.key, self.pack)
        document = json.loads(self.pack.read_text(encoding="utf-8"))
        document["payload"]["game"]["game_name"] = "Tampered Game"
        self.pack.write_text(json.dumps(document), encoding="utf-8")
        with self.assertRaises(PackIntegrityError):
            import_game_pack(self.target, self.pack, self.key)
        with self.assertRaises(PackIntegrityError):
            import_game_pack(self.target, self.pack, b"wrong key that is at least 32 bytes")

    def test_android_csv_exports_only_verified_and_licensed_translations(self):
        pending_source = self.source.add_source(
            "Pending source", "CC-BY-4.0", author_name="Contributor",
        )
        self.source.add_translation(
            "pack-game", "Pending source line", "Dòng chờ duyệt",
            source_id=pending_source, translation_status="VERIFIED",
        )
        self.source.add_translation(
            "pack-game", "AI line", "Dòng AI", translation_status="AI_UNVERIFIED",
        )
        self.source.add_translation(
            "pack-game", "Manual verified line", "Dòng đã kiểm duyệt",
            translation_status="VERIFIED",
        )
        with self.source.transaction() as db:
            db.execute(
                "UPDATE data_sources SET verification_status='VERIFIED' WHERE source_id=?",
                (self.authorized_source_id,),
            )
        output = self.root / "android-import.csv"
        export_android_csv(self.source, "pack-game", output)
        rows = output.read_text(encoding="utf-8-sig").splitlines()
        self.assertEqual(rows[0], "source,vi,game")
        self.assertEqual(len(rows), 3)
        self.assertTrue(any("Open inventory" in row for row in rows))
        self.assertTrue(any("Manual verified line" in row for row in rows))
        self.assertFalse(any("Pending source line" in row for row in rows))
        self.assertFalse(any("AI line" in row for row in rows))

    def test_duplicate_translations_are_skipped(self):
        export_game_pack(self.source, "pack-game", self.key, self.pack)
        import_game_pack(self.target, self.pack, self.key)
        counts = import_game_pack(self.target, self.pack, self.key)
        self.assertEqual(counts["translations"], 0)


if __name__ == "__main__":
    unittest.main()
