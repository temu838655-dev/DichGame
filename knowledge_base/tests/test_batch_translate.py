import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from knowledge_base.ai_fallback import ProviderHTTPError, ProviderResponse
from knowledge_base.batch_translate import BatchTranslator
from knowledge_base.db import KnowledgeBase


class FakeProvider:
    def translate(self, source_text, context, *, model, max_output_tokens):
        return ProviderResponse("Bản dịch: " + source_text, 3, 5)


class BatchTranslateTests(unittest.TestCase):
    def test_quota_pauses_provider_but_reuses_local_translation(self):
        with tempfile.TemporaryDirectory() as tmp:
            kb = KnowledgeBase(Path(tmp) / "kb.db")
            kb.add_game("g", "Test", "PC")
            with kb.transaction() as db:
                db.executemany(
                    "INSERT INTO source_strings(game_id, external_id, source_text) VALUES ('g', ?, ?)",
                    [("a", "Open"), ("b", "Close"), ("c", "Wait")],
                )
            kb.add_translation("g", "Wait", "Chờ", external_id="c")
            provider = Mock()
            provider.translate.side_effect = ProviderHTTPError("HTTP 429", status=429)
            report = BatchTranslator(kb, provider, model="test", pause_seconds=0).run("g")
            self.assertEqual(provider.translate.call_count, 1)
            self.assertEqual((report.failed, report.deferred, report.reused_existing), (1, 1, 1))
            self.assertEqual({row["external_id"] for row in kb.pending_source_strings("g")}, {"a", "b"})

    def test_batch_translation_preserves_id_and_resumes(self):
        with tempfile.TemporaryDirectory() as tmp:
            kb = KnowledgeBase(Path(tmp) / "kb.db")
            kb.add_game("gta6", "Grand Theft Auto VI", "PC")
            source_id = kb.add_source("Test permission", "TEST-PERMISSION")
            with kb.transaction() as db:
                db.execute(
                    "INSERT INTO source_strings(game_id, external_id, source_text, context, sheet_name, row_number, source_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    ("gta6", "C0000_B381007C", "Hey! Eyes open!", "Thoại | Core C0000", "Core", 2, source_id),
                )
            worker = BatchTranslator(kb, FakeProvider(), model="test-model", pause_seconds=0)
            report = worker.run("gta6", limit=10)
            self.assertEqual((report.translated, report.failed), (1, 0))
            found = kb.lookup_external_id("gta6", "C0000_B381007C")
            self.assertEqual(found["translated_text"], "Bản dịch: Hey! Eyes open!")
            self.assertEqual(found["translation_status"], "AI_UNVERIFIED")
            self.assertEqual(kb.pending_source_strings("gta6"), [])


if __name__ == "__main__":
    unittest.main()
