import json
import tempfile
import unittest
from pathlib import Path

from knowledge_base.db import KnowledgeBase
from knowledge_base.matcher import TranslationMatcher


class MatcherTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.kb = KnowledgeBase(Path(self.tmp.name) / "matcher.db")
        self.kb.add_game("game", "Test Game", "PC")
        self.matcher = TranslationMatcher(self.kb)

    def tearDown(self):
        self.tmp.cleanup()

    def test_exact_match_prefers_matching_context(self):
        self.kb.add_translation("game", "Open the gate", "Mở cổng", speaker="Guard",
                                translation_status="COMMUNITY")
        self.kb.add_translation("game", "Open the gate", "Mở cửa", speaker="Player",
                                translation_status="COMMUNITY")
        result = self.matcher.lookup("game", "Open the gate", speaker="Player")
        self.assertEqual(result.translation["translated_text"], "Mở cửa")
        self.assertEqual(result.method, "EXACT")

    def test_ocr_correction_only_uses_high_confidence_curated_variants(self):
        with self.kb.transaction() as db:
            db.execute(
                "INSERT INTO ocr_dictionary(game_id,correct_word,incorrect_variants_json,confidence_score) VALUES(?,?,?,?)",
                ("game", "YOU", json.dumps(["Y0U"]), 0.99),
            )
            db.execute(
                "INSERT INTO ocr_dictionary(game_id,correct_word,incorrect_variants_json,confidence_score) VALUES(?,?,?,?)",
                ("game", "DIED", json.dumps(["D1ED"]), 0.70),
            )
        corrected, changes = self.matcher.correct_ocr("game", "Y0U D1ED")
        self.assertEqual(corrected, "YOU D1ED")
        self.assertEqual(len(changes), 1)

    def test_ambiguous_ocr_variant_is_not_changed(self):
        with self.kb.transaction() as db:
            db.execute(
                "INSERT INTO ocr_dictionary(game_id,correct_word,incorrect_variants_json,confidence_score) VALUES(?,?,?,?)",
                ("game", "ITEM", json.dumps(["lTEM"]), 0.99),
            )
            db.execute(
                "INSERT INTO ocr_dictionary(game_id,correct_word,incorrect_variants_json,confidence_score) VALUES(?,?,?,?)",
                ("game", "1TEM", json.dumps(["lTEM"]), 0.99),
            )
        corrected, changes = self.matcher.correct_ocr("game", "lTEM")
        self.assertEqual(corrected, "lTEM")
        self.assertEqual(changes, [])

    def test_fuzzy_match_finds_minor_ocr_error(self):
        self.kb.add_translation("game", "Find the hidden key", "Tìm chìa khóa bí mật",
                                translation_status="VERIFIED")
        result = self.matcher.lookup("game", "Find the hiden key", fuzzy_threshold=0.80)
        self.assertIsNotNone(result)
        self.assertEqual(result.method, "FUZZY")
        self.assertEqual(result.translation["translated_text"], "Tìm chìa khóa bí mật")

    def test_unrelated_text_does_not_get_forced_match(self):
        self.kb.add_translation("game", "Find the hidden key", "Tìm chìa khóa bí mật")
        result = self.matcher.lookup("game", "The dragon has arrived")
        self.assertIsNone(result)

    def test_lookup_is_scoped_to_selected_game(self):
        self.kb.add_translation("game", "Open inventory", "Mở túi đồ")
        self.kb.add_game("other", "Other Game", "PC")
        self.assertIsNone(self.matcher.lookup("other", "Open inventory"))


if __name__ == "__main__":
    unittest.main()
