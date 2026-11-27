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

    def test_corrupt_import_is_skipped_by_all_lookup_paths(self):
        source = "Find the hidden key for {Player}"
        self.kb.add_translation("game", source, "Tìm chìa khóa", external_id="key",
                                translation_status="VERIFIED")
        lookups = {
            "exact": lambda: self.kb.lookup_exact("game", source),
            "id": lambda: self.kb.lookup_external_id("game", "key"),
            "matcher": lambda: self.matcher.lookup("game", source),
            "fuzzy": lambda: self.matcher.lookup("game", source.replace("hidden", "hiden")),
        }
        for name, lookup in lookups.items():
            with self.subTest(path=name):
                self.assertIsNone(lookup())
        valid = "Tìm chìa khóa cho {Player}"
        self.kb.add_translation("game", source, valid, external_id="key")
        for name, lookup in lookups.items():
            with self.subTest(path=name):
                result = lookup()
                row = result.translation if hasattr(result, "translation") else result
                self.assertEqual(row["translated_text"], valid)

    def test_duplicate_fuzzy_rows_do_not_hide_third_conflicting_result(self):
        for vi in ("Mở cổng thành", "Mở cổng thành", "Mở cổng làng"):
            self.kb.add_translation("game", "Open the hidden gate", vi)
        self.assertIsNone(self.matcher.lookup("game", "Open the hiden gate"))

    def test_plain_exact_lookup_checks_case_sensitive_source_arguments(self):
        self.kb.add_translation("game", "Hello {Player}", "Chào {Player}")
        self.assertIsNone(self.kb.lookup_exact("game", "Hello {player}"))

    def test_exact_match_prefers_matching_context(self):
        self.kb.add_translation("game", "Open the gate", "Mở cổng", speaker="Guard",
                                translation_status="COMMUNITY")
        self.kb.add_translation("game", "Open the gate", "Mở cửa", speaker="Player",
                                translation_status="COMMUNITY")
        result = self.matcher.lookup("game", "Open the gate", speaker="Player")
        self.assertEqual(result.translation["translated_text"], "Mở cửa")
        self.assertEqual(result.method, "EXACT")

    def test_exact_match_uses_previous_and_next_dialogue_context(self):
        self.kb.add_translation(
            "game", "I understand.", "Tôi hiểu.", previous_sentence="Where are you going?",
            next_sentence="Then let's move.", translation_status="COMMUNITY",
        )
        self.kb.add_translation(
            "game", "I understand.", "Ta hiểu rồi.", previous_sentence="Who are you?",
            next_sentence="Prove it.", translation_status="COMMUNITY",
        )
        result = self.matcher.lookup(
            "game", "I understand.", previous_sentence="Who are you?",
            next_sentence="Prove it.",
        )
        self.assertEqual(result.translation["translated_text"], "Ta hiểu rồi.")

    def test_exact_ambiguous_translations_without_context_match_fall_back(self):
        self.kb.add_translation(
            "game", "Open the gate", "Mở cổng", speaker="Guard",
            translation_status="VERIFIED",
        )
        self.kb.add_translation(
            "game", "Open the gate", "Mở cửa", speaker="Player",
            translation_status="COMMUNITY",
        )
        result = self.matcher.lookup("game", "Open the gate", speaker="Merchant")
        self.assertIsNone(result)

    def test_single_conflicting_speaker_is_not_reused_even_with_matching_scene(self):
        self.kb.add_translation("game", "Listen carefully", "Con nghe đây", speaker="Child", scene_id="camp")
        for source in ("Listen carefully", "Listen carefuly"):
            self.assertIsNone(self.matcher.lookup("game", source, speaker="King", scene_id="camp"))

    def test_unknown_speaker_does_not_resolve_conflicting_localizations(self):
        self.kb.add_translation("game", "Listen", "Con nghe đây", speaker="Child", translation_status="VERIFIED")
        self.kb.add_translation("game", "Listen", "Trẫm nghe đây", speaker="King")
        self.assertIsNone(self.matcher.lookup("game", "Listen"))

    def test_changed_runtime_arguments_and_quantities_never_fuzzy_match(self):
        self.kb.add_translation("game", "Collect 20 items for {Player}", "Thu thập 20 vật phẩm cho {Player}")
        for source in ("Collect 21 items for {Player}", "Collect 20 items for {player}"):
            self.assertIsNone(self.matcher.lookup("game", source, fuzzy_threshold=0.75))

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
