import tempfile
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from knowledge_base.db import KnowledgeBase
from knowledge_base.translation_memory import TranslationMemory


class TranslationMemoryTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.kb = KnowledgeBase(Path(self.temp_dir.name) / "memory.db")
        self.kb.add_game("memory-game", "Memory Game", "PC")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_cache_hit_skips_provider(self):
        memory = TranslationMemory(self.kb)
        first = memory.get_or_translate(
            "memory-game", "New quest", lambda text, context: "Nhiệm vụ mới",
            model="fake-model",
        )
        second = memory.get_or_translate(
            "memory-game", " NEW QUEST ", lambda text, context: self.fail("provider called on cache hit"),
            model="other-model",
        )
        self.assertEqual(first.translated_text, "Nhiệm vụ mới")
        self.assertFalse(first.cache_hit)
        self.assertTrue(second.cache_hit)

    def test_concurrent_identical_requests_call_provider_once(self):
        memory = TranslationMemory(
            self.kb, wait_timeout_seconds=5, poll_interval_seconds=0.02,
        )
        count = 0
        count_lock = threading.Lock()

        def provider(text, context):
            nonlocal count
            with count_lock:
                count += 1
            time.sleep(0.2)
            return "Mở cánh cổng"

        def request(_):
            return memory.get_or_translate(
                "memory-game", "Open the gate", provider, model="fake-model",
                context="chapter-1",
            ).translated_text

        with ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(request, range(8)))
        self.assertEqual(results, ["Mở cánh cổng"] * 8)
        self.assertEqual(count, 1)

    def test_context_isolation_and_failed_provider_not_cached(self):
        memory = TranslationMemory(self.kb)
        shop = memory.get_or_translate(
            "memory-game", "Welcome", lambda text, context: "Chào mừng đến cửa hàng",
            model="fake", context="shop",
        )
        combat = memory.get_or_translate(
            "memory-game", "Welcome", lambda text, context: "Chuẩn bị chiến đấu",
            model="fake", context="combat",
        )
        self.assertNotEqual(shop.translated_text, combat.translated_text)

        def broken_provider(text, context):
            raise RuntimeError("provider offline")

        with self.assertRaises(RuntimeError):
            memory.get_or_translate(
                "memory-game", "Uncached line", broken_provider, model="fake",
            )
        self.assertIsNone(self.kb.cache_get("memory-game", "Uncached line"))
        with self.kb.connect() as db:
            locks = db.execute("SELECT count(*) FROM translation_request_locks").fetchone()[0]
        self.assertEqual(locks, 0)

    def test_expired_cache_entry_is_ignored(self):
        self.kb.cache_put("memory-game", "Continue", "Tiếp tục", "old-model")
        with self.kb.transaction() as db:
            db.execute(
                "UPDATE translation_cache SET created_at='2000-01-01T00:00:00.000Z'"
            )
        memory = TranslationMemory(self.kb, max_cache_age_seconds=60)
        result = memory.get_or_translate(
            "memory-game", "Continue", lambda text, context: "Tiếp tục trò chơi",
            model="new-model",
        )
        self.assertFalse(result.cache_hit)
        self.assertEqual(result.translated_text, "Tiếp tục trò chơi")


if __name__ == "__main__":
    unittest.main()
