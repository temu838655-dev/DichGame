import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from knowledge_base.db import KnowledgeBase
from knowledge_base.moderation_cli import main


class ModerationCliTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.db = str(Path(self.temp_dir.name) / "cli.db")
        KnowledgeBase(self.db).add_game("cli-game", "CLI Game", "PC")

    def tearDown(self):
        self.temp_dir.cleanup()

    def run_cli(self, *args, env=None):
        output = io.StringIO()
        with patch.dict(os.environ, env or {}, clear=True), contextlib.redirect_stdout(output):
            code = main(["--db", self.db, *args])
        return code, json.loads(output.getvalue())

    def test_submit_queue_report_and_authorized_review(self):
        code, submitted = self.run_cli(
            "submit", "--game-id", "cli-game", "--source", "Open gate",
            "--translation", "Mở cổng", "--contributor-id", "player-1",
        )
        self.assertEqual(code, 0)
        self.assertEqual(submitted["translation_status"], "COMMUNITY")
        translation_id = submitted["translation_id"]

        code, queue = self.run_cli("queue")
        self.assertEqual(code, 0)
        self.assertEqual(queue[0]["contributor_id"], "player-1")

        code, report = self.run_cli(
            "report", "--translation-id", str(translation_id),
            "--reporter-id", "player-2", "--reason", "MISTRANSLATION",
        )
        self.assertEqual(code, 0)
        self.assertEqual(report["status"], "OPEN")

        code, reports = self.run_cli("reports")
        self.assertEqual(code, 0)
        self.assertEqual(reports[0]["translation_id"], translation_id)

        code, denied = self.run_cli(
            "review", "--translation-id", str(translation_id),
            "--reviewer-id", "outsider", "--decision", "APPROVE",
        )
        self.assertEqual(code, 2)
        self.assertEqual(denied["status"], "ERROR")

        code, approved = self.run_cli(
            "review", "--translation-id", str(translation_id),
            "--reviewer-id", "trusted", "--decision", "APPROVE",
            env={"DICHGAME_REVIEWER_IDS": "trusted"},
        )
        self.assertEqual(code, 0)
        self.assertEqual(approved["translation_status"], "VERIFIED")

        code, queue = self.run_cli("queue")
        self.assertEqual(code, 0)
        self.assertEqual(queue, [])

        code, reports = self.run_cli("reports")
        self.assertEqual(code, 0)
        self.assertEqual(reports, [])


if __name__ == "__main__":
    unittest.main()
