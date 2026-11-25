import hashlib
import json
import pathlib
import tempfile
import unittest
from build_cloud_catalog import build

class CatalogTest(unittest.TestCase):
    def test_build_and_hash(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d)
            src = root / "game.csv"
            src.write_text("source,vi,game\nHello,Xin chào,Game A\n", encoding="utf-8")
            out = root / "out"
            build([dict(game="Game A", id="game-a", version=1, csv=str(src))], "https://example.com", out)
            m = json.loads((out / "game-a.dichgame.json").read_text())
            payload = (out / m["url"].split("/")[-1]).read_bytes()
            self.assertEqual(m["sha256"], hashlib.sha256(payload).hexdigest())
            self.assertEqual(m["bytes"], len(payload))
            self.assertEqual(json.loads((out / "catalog.json").read_text())["games"][0]["game"], "Game A")

    def test_reject_wrong_game(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d)
            src = root / "game.csv"
            src.write_text("source,vi,game\nHello,Xin chào,Game B\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                build([dict(game="Game A", id="game-a", version=1, csv=str(src))], "https://example.com", root / "out")

if __name__ == "__main__":
    unittest.main()
