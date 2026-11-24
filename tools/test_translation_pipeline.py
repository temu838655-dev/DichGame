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
    def test_context_isolation(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / "source.csv"
            source.write_text("id,source,context\n1,Get down!,police\n2,Get down!,friend\n3,Get down!,police\n", encoding="utf-8")
            self.assertEqual(prepare(source, root / "prepared")["unique"], 2)
            mapping = json.loads((root / "prepared/id-map.json").read_text())
            self.assertEqual(mapping, {"1": ["1", "3"], "2": ["2"]})

    def test_game_isolation(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / "source.csv"
            source.write_text("id,source\n1,Hello\n", encoding="utf-8")
            prepare(source, root / "prepared")
            args = (root / "prepared", root / "translated", "https://example.com/v1/chat/completions", "test", "key")
            with patch("translate_batches.translate", return_value=[{"id":"1","vi":"Chào"}]) as call:
                run(*args, "Game A", 1)
                with self.assertRaises(ValueError):
                    run(*args, "Game B", 1)
                self.assertEqual(call.call_count, 1)
            with self.assertRaises(ValueError):
                collect(root / "prepared", root / "translated", root / "wrong.csv", "Game B")
            self.assertFalse((root / "wrong.csv").exists())
            self.assertEqual(collect(root / "prepared", root / "translated", root / "right.csv", "Game A")["restored_rows"], 1)
            mapping = root / "prepared/id-map.json"
            mapping.write_text(json.dumps({"1": ["1", "2"]}), encoding="utf-8")
            with self.assertRaises(ValueError):
                collect(root / "prepared", root / "translated", root / "tampered.csv", "Game A")
            self.assertFalse((root / "tampered.csv").exists())

    def test_invalid_results(self):
        source = [{"id": "1", "source": "Hello %s"}]
        for bad in [[], [{"id":"2","vi":"Chào %s"}], [{"id":"1","vi":"Chào"}], [{"id":"1","vi":""}]]:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                validate(source, bad)

if __name__ == "__main__":
    unittest.main()
