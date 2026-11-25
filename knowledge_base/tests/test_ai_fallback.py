import tempfile
import unittest
from pathlib import Path

from knowledge_base.ai_fallback import (
    AIBudgetExceededError,
    AIFallback,
    AIUnavailableError,
    ProviderResponse,
)
from knowledge_base.db import KnowledgeBase
from knowledge_base.translation_memory import TranslationMemory


class FakeProvider:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = 0
        self.last_options = None

    def translate(self, source_text, context, *, model, max_output_tokens):
        self.calls += 1
        self.last_options = (model, max_output_tokens)
        item = self.responses.pop(0)
        if isinstance(item, Exception):
            raise item
        return item


class AIFallbackTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.kb = KnowledgeBase(Path(self.temp_dir.name) / "ai.db")
        self.kb.add_game("ai-game", "AI Game", "PC")
        self.memory = TranslationMemory(self.kb)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_provider_success_is_cached_and_usage_recorded(self):
        provider = FakeProvider([ProviderResponse("  Mở cửa  ", 12, 8)])
        ai = AIFallback(
            self.kb, self.memory, provider, model="mock-model",
            daily_request_limit=3, max_retries=0, max_output_tokens=96,
        )
        result = ai.translate("ai-game", "Open the door", context="quest")
        cached = ai.translate("ai-game", "Open the door", context="quest")
        self.assertEqual(result.translated_text, "Mở cửa")
        self.assertFalse(result.cache_hit)
        self.assertTrue(cached.cache_hit)
        self.assertEqual(provider.calls, 1)
        self.assertEqual(provider.last_options, ("mock-model", 96))
        with self.kb.connect() as db:
            row = db.execute(
                "SELECT request_count, input_tokens, output_tokens FROM ai_usage_daily"
            ).fetchone()
        self.assertEqual(tuple(row), (1, 12, 8))

    def test_retry_counts_each_attempt(self):
        provider = FakeProvider([
            RuntimeError("temporary failure"),
            ProviderResponse("Nhiệm vụ hoàn tất", 5, 6),
        ])
        ai = AIFallback(
            self.kb, self.memory, provider, model="mock",
            daily_request_limit=3, max_retries=1, retry_backoff_seconds=0,
        )
        result = ai.translate("ai-game", "Quest complete")
        self.assertEqual(result.translated_text, "Nhiệm vụ hoàn tất")
        self.assertEqual(provider.calls, 2)
        with self.kb.connect() as db:
            count = db.execute("SELECT request_count FROM ai_usage_daily").fetchone()[0]
        self.assertEqual(count, 2)

    def test_daily_budget_stops_new_provider_calls(self):
        provider = FakeProvider([
            ProviderResponse("Bắt đầu", 2, 2),
            ProviderResponse("Không được gọi", 2, 2),
        ])
        ai = AIFallback(
            self.kb, self.memory, provider, model="mock",
            daily_request_limit=1, max_retries=0,
        )
        ai.translate("ai-game", "Start")
        with self.assertRaises(AIBudgetExceededError):
            ai.translate("ai-game", "Begin")
        self.assertEqual(provider.calls, 1)

    def test_offline_mode_can_use_cache_but_not_call_provider_on_miss(self):
        provider = FakeProvider([ProviderResponse("unused")])
        self.kb.cache_put("ai-game", "Cached line", "Dòng đã lưu", "mock")
        ai = AIFallback(
            self.kb, self.memory, provider, model="mock", offline_mode=True,
        )
        result = ai.translate("ai-game", "Cached line")
        self.assertTrue(result.cache_hit)
        with self.assertRaises(AIUnavailableError):
            ai.translate("ai-game", "No cached line")
        self.assertEqual(provider.calls, 0)

    def test_input_limit_is_enforced(self):
        provider = FakeProvider([])
        ai = AIFallback(
            self.kb, self.memory, provider, model="mock", max_input_chars=4,
        )
        with self.assertRaises(ValueError):
            ai.translate("ai-game", "This is too long")
        self.assertEqual(provider.calls, 0)


if __name__ == "__main__":
    unittest.main()
