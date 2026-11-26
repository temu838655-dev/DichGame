import tempfile
import unittest
from pathlib import Path

from knowledge_base.db import KnowledgeBase, normalize_text


class KnowledgeBaseTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.db = KnowledgeBase(Path(self.temp_dir.name) / "test.db")
        self.db.add_game("sample-game", "Sample Game", "PC")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_normalization_preserves_numbers_and_meaningful_punctuation(self):
        self.assertEqual(normalize_text("  TAKE   5 ! "), "take 5!")

    def test_exact_lookup_is_game_scoped_and_case_insensitive(self):
        self.db.add_translation("sample-game", "Open the Door", "Mở cửa", translation_status="VERIFIED")
        self.assertEqual(self.db.lookup_exact("sample-game", " OPEN THE DOOR ")["translated_text"], "Mở cửa")
        self.assertIsNone(self.db.lookup_exact("another-game", "Open the Door"))

    def test_exact_external_id_lookup_is_game_scoped(self):
        self.db.add_translation(
            'sample-game', 'Hey! Eyes open!', 'Này! Tỉnh táo lên!',
            external_id='C0000_B381007C', translation_status='VERIFIED',
        )
        self.assertEqual(
            self.db.lookup_external_id('sample-game', 'C0000_B381007C')['translated_text'],
            'Này! Tỉnh táo lên!',
        )
        self.assertIsNone(self.db.lookup_external_id('other-game', 'C0000_B381007C'))
    def test_rejected_translation_is_never_returned(self):
        self.db.add_translation("sample-game", "Bad line", "Sai", translation_status="REJECTED")
        self.assertIsNone(self.db.lookup_exact("sample-game", "Bad line"))

    def test_fts_indexes_inserted_translation(self):
        self.db.add_translation("sample-game", "Find the hidden key", "Tìm chiếc chìa khóa bí mật")
        rows = self.db.search_fts("sample-game", "hidden key")
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["translated_text"], "Tìm chiếc chìa khóa bí mật")

    def test_cache_is_separated_by_context_and_tracks_use(self):
        self.db.cache_put("sample-game", "Hello", "Xin chào", "test-model", context="shop")
        self.db.cache_put("sample-game", "Hello", "Chào", "test-model", context="combat")
        self.assertEqual(self.db.cache_get("sample-game", "HELLO", "shop"), "Xin chào")
        self.assertEqual(self.db.cache_get("sample-game", "hello", "combat"), "Chào")
        self.assertIsNone(self.db.cache_get("sample-game", "hello", "menu"))

    def test_source_provenance_is_persisted(self):
        source_id = self.db.add_source(
            "Community pack", "CC-BY-4.0", source_url="https://example.org/pack",
            author_name="Translator", verification_status="PENDING",
        )
        translation_id = self.db.add_translation(
            "sample-game", "Save game", "Lưu trò chơi", source_id=source_id,
        )
        with self.db.connect() as connection:
            row = connection.execute(
                "SELECT source_id FROM translations WHERE translation_id=?",
                (translation_id,),
            ).fetchone()
        self.assertEqual(row["source_id"], source_id)


if __name__ == "__main__":
    unittest.main()
