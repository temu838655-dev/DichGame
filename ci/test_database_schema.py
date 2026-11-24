"""Host SQLite checks using the exact SQL schema in GameDatabase.kt.
Android lifecycle/file migration still requires on-device testing.
"""
import re
import sqlite3
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/dichgame/app/GameDatabase.kt"

class DatabaseTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        schema = re.findall(r'db\.execSQL\("(CREATE (?:TABLE|INDEX) [^"]+)"\)', SOURCE.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(schema), 8)
        for statement in schema:
            self.db.execute(statement)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def learned(self, profile, key, vi, manual=0):
        self.db.execute(
            "INSERT OR IGNORE INTO learned(profile,lookup_key,game,game_norm,original,vi,original_norm,vi_norm,manual) VALUES(?,?,?,?,?,?,?,?,?)",
            (profile,key,"Game","game","Original line",vi,"original line",vi.lower(),manual))

    def test_manual_correction_and_scope(self):
        self.learned("A","en:person:line","bản máy")
        self.db.execute("UPDATE learned SET vi=?,manual=1 WHERE profile=? AND lookup_key=?",("bản sửa","A","en:person:line"))
        self.learned("A","en:person:line","bản AI mới")
        self.learned("B","en:person:line","hồ sơ khác")
        self.learned("A","zh:person:line","tiếng Trung")
        rows = self.db.execute("SELECT profile,lookup_key,vi,manual FROM learned ORDER BY id").fetchall()
        self.assertEqual(rows,[("A","en:person:line","bản sửa",1),("B","en:person:line","hồ sơ khác",0),("A","zh:person:line","tiếng Trung",0)])
        self.db.execute("DELETE FROM learned WHERE profile=? AND game_norm=?",("A","game"))
        self.assertEqual(self.db.execute("SELECT profile FROM learned").fetchall(),[("B",)])

    def test_pack_replace_and_profile_separation(self):
        q = "INSERT OR REPLACE INTO pack_line(profile,canon,original,vi) VALUES(?,?,?,?)"
        self.db.execute(q,("A","hello","Hello","xin chào"))
        self.db.execute(q,("A","hello","Hello","chào bạn"))
        self.db.execute(q,("B","hello","Hello","chào"))
        self.assertEqual(self.db.execute("SELECT vi FROM pack_line WHERE profile=?",("A",)).fetchall(),[("chào bạn",)])
        self.assertEqual(self.db.execute("SELECT count(*) FROM pack_line").fetchone()[0],2)

    def test_migration_transaction_rolls_back_and_retries(self):
        try:
            with self.db:
                self.learned("A","legacy","cũ")
                self.db.execute("INSERT INTO migration VALUES(?,?)",("learned","A"))
                raise RuntimeError("interrupted")
        except RuntimeError:
            pass
        self.assertEqual(self.db.execute("SELECT count(*) FROM learned").fetchone()[0],0)
        self.assertEqual(self.db.execute("SELECT count(*) FROM migration").fetchone()[0],0)
        with self.db:
            self.learned("A","legacy","cũ")
            self.db.execute("INSERT INTO migration VALUES(?,?)",("learned","A"))
        self.assertEqual(self.db.execute("SELECT count(*) FROM migration").fetchone()[0],1)

    def test_indexed_exact_lookup(self):
        plan = self.db.execute("EXPLAIN QUERY PLAN SELECT vi FROM learned WHERE profile=? AND lookup_key=?",("A","key")).fetchall()
        self.assertTrue(any("INDEX" in row[3].upper() for row in plan))

if __name__ == "__main__":
    unittest.main()
