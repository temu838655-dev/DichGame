import sqlite3
import tempfile
import unittest
from pathlib import Path

from knowledge_base.backup import create_backup, restore_backup, validate_backup
from knowledge_base.db import KnowledgeBase


class BackupTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.live = self.root / "live.db"
        self.backup = self.root / "backup.db"
        self.kb = KnowledgeBase(self.live)
        self.kb.add_game("backup-game", "Backup Game", "PC")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_backup_and_restore_round_trip(self):
        self.kb.add_translation("backup-game", "Continue", "Tiếp tục")
        create_backup(self.live, self.backup)
        validate_backup(self.backup)
        self.kb.add_translation("backup-game", "Quit", "Thoát")
        restore_backup(self.backup, self.live)
        with self.kb.connect() as db:
            count = db.execute(
                "SELECT count(*) FROM translations WHERE game_id='backup-game'"
            ).fetchone()[0]
        self.assertEqual(count, 1)

    def test_restore_rejects_invalid_database_without_overwriting_live(self):
        self.backup.write_text("not a sqlite database", encoding="utf-8")
        with self.assertRaises(sqlite3.DatabaseError):
            restore_backup(self.backup, self.live)
        with self.kb.connect() as db:
            self.assertEqual(
                db.execute("SELECT game_name FROM games WHERE game_id='backup-game'").fetchone()[0],
                "Backup Game",
            )

    def test_backup_destination_must_differ_from_live_database(self):
        with self.assertRaises(ValueError):
            create_backup(self.live, self.live)


if __name__ == "__main__":
    unittest.main()
