import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from verify_pack import verify

class VerifyPackTest(unittest.TestCase):
    def make_pack(self, root, row_game="GTA VI"):
        data = ("source,vi,game\nHello,Chao," + row_game + "\n").encode()
        (root / "pack.csv").write_bytes(data)
        (root / "pack.dichgame.json").write_text(json.dumps({
            "schema": 1, "game": "GTA VI", "version": 1,
            "url": "https://example.com/gta-vi/v1/pack.csv",
            "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()
        }), encoding="utf-8")

    def test_valid_and_wrong_game(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.make_pack(root)
            self.assertEqual(verify(root, "GTA VI")["rows"], 1)
            with self.assertRaises(ValueError):
                verify(root, "GTA V")

    def test_mixed_game_even_with_valid_hash(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.make_pack(root, "GTA V")
            with self.assertRaises(ValueError):
                verify(root, "GTA VI")

    def test_corruption(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.make_pack(root)
            (root / "pack.csv").write_bytes(b"corrupted")
            with self.assertRaises(ValueError):
                verify(root, "GTA VI")
