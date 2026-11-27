import tempfile
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from knowledge_base.db import KnowledgeBase, normalize_text, sha256_text
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
        self.assertEqual(second.model, "fake-model")

    def test_argument_case_isolated_across_restart_for_source_and_context(self):
        for name in ("Player", "player"):
            source = "Hello {" + name + "}"
            context = "cue {" + name + "}"
            self.kb.cache_put("memory-game", source, "Chào {" + name + "}", "m")
            self.kb.cache_put("memory-game", "Ready", name, "m", context)
        restarted = KnowledgeBase(self.kb.database_path)
        for name in ("Player", "player"):
            self.assertEqual(restarted.cache_get("memory-game", "Hello {" + name + "}"), "Chào {" + name + "}")
            self.assertEqual(restarted.cache_get("memory-game", "Ready", "cue {" + name + "}"), name)

    def test_legacy_folded_key_requires_matching_source_and_output(self):
        source = "Open {Gate}"
        with self.kb.transaction() as db:
            db.execute(
                "INSERT INTO translation_cache(game_id,text_hash,source_text,translated_text,model) VALUES(?,?,?,?,?)",
                ("memory-game", sha256_text(normalize_text(source)), source, "Mở {Gate}", "legacy"),
            )
        self.assertIsNone(self.kb.cache_get("memory-game", "Open {gate}"))
        self.assertEqual(self.kb.cache_get("memory-game", source), "Mở {Gate}")
        with self.kb.transaction() as db:
            db.execute("UPDATE translation_cache SET translated_text='Mở {gate}'")
        self.assertIsNone(self.kb.cache_get("memory-game", source))

    def test_invalid_output_is_not_remembered_and_recovery_is_cached(self):
        source = "Give {player} 2 <b>coins</b> %s %d"
        good = "Tặng 2 <b>xu</b> cho {player} %s %d"
        memory = TranslationMemory(self.kb)
        for broken in (good.replace("{player}", "{Player}"), good.replace("2", "3"),
                       good.replace("<b>xu</b>", "</b>xu<b>"), good.replace("%s %d", "%d %s")):
            with self.assertRaises(ValueError):
                memory.get_or_translate("memory-game", source, lambda *_: broken, model="test")
            self.assertIsNone(self.kb.cache_get("memory-game", source))
        result = memory.get_or_translate("memory-game", source, lambda *_: good, model="test")
        self.assertFalse(result.cache_hit)
        self.assertEqual(self.kb.cache_get("memory-game", source), good)
        with self.kb.transaction() as db:
            db.execute("UPDATE translation_cache SET translated_text='Hỏng'")
        self.assertIsNone(self.kb.cache_get("memory-game", source))

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
        hit = memory.get_or_translate(
            "memory-game", "Continue", lambda *_: self.fail("refreshed entry expired immediately"),
            model="another-model",
        )
        self.assertTrue(hit.cache_hit)
        self.assertEqual(hit.model, "new-model")


if __name__ == "__main__":
    unittest.main()
