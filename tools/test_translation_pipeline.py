import csv
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from prepare_translation_batches import prepare
from collect_translation_batches import collect
from translate_batches import run, validate

class PipelineTest(unittest.TestCase):
    def test_resume_and_restore(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / "source.csv"
            source.write_text("id,source\n1,Hello %s\n2,Hello %s\n3,Goodbye\n", encoding="utf-8")
            prepared, translated = root / "prepared", root / "translated"
            self.assertEqual(prepare(source, prepared, 1)["unique"], 2)
            def fake(rows, *args):
                return [{"id": r["id"], "vi": "Xin chào %s" if "%s" in r["source"] else "Tạm biệt"} for r in rows]
            args = (prepared, translated, "https://example.com/v1/chat/completions", "test", "GameKey", "Game", 1)
            with patch("translate_batches.translate", side_effect=fake) as call:
                self.assertEqual(run(*args), 1)
                self.assertEqual(run(*args), 1)
                self.assertEqual(run(*args), 0)
                self.assertEqual(call.call_count, 2)
            output = root / "vi.csv"
            self.assertEqual(collect(prepared, translated, output)["restored_rows"], 3)
            with output.open(encoding="utf-8", newline="") as f:
                self.assertEqual(list(csv.DictReader(f)), [
                    {"id": "1", "vi": "Xin chào %s"},
                    {"id": "2", "vi": "Xin chào %s"},
                    {"id": "3", "vi": "Tạm biệt"}])
            with self.assertRaises(ValueError):
                run(prepared, translated, args[2], "other-model", "key", "Game", 1)
    def test_invalid_results(self):
        source = [{"id": "1", "source": "Hello %s"}]
        for bad in [[], [{"id":"2","vi":"Chào %s"}], [{"id":"1","vi":"Chào"}], [{"id":"1","vi":""}]]:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                validate(source, bad)

if __name__ == "__main__":
    unittest.main()
