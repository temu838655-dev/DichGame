import tempfile
import unittest
from pathlib import Path

from knowledge_base.db import KnowledgeBase
from knowledge_base.moderation import CommunityModeration, ModerationPermissionError


class CommunityModerationTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.kb = KnowledgeBase(Path(self.temp_dir.name) / "moderation.db")
        self.kb.add_game("moderation-game", "Moderation Game", "PC")
        self.service = CommunityModeration(
            self.kb, reviewer_authorizer=lambda reviewer: reviewer == "trusted-reviewer",
        )

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_existing_database_gets_additive_contributor_migration(self):
        with self.kb.connect() as db:
            db.execute("ALTER TABLE translations DROP COLUMN contributor_id")
            db.execute("DELETE FROM schema_migrations WHERE version=5")
        upgraded = KnowledgeBase(self.kb.database_path)
        with upgraded.connect() as db:
            columns = {row[1] for row in db.execute("PRAGMA table_info(translations)")}
            migration = db.execute(
                "SELECT 1 FROM schema_migrations WHERE version=5"
            ).fetchone()
        self.assertIn("contributor_id", columns)
        self.assertIsNotNone(migration)

    def test_submission_is_never_auto_verified(self):
        translation_id = self.service.submit(
            "moderation-game", "Open the door", "Mở cửa",
            contributor_id="community-user",
        )
        with self.kb.connect() as db:
            status = db.execute(
                "SELECT translation_status FROM translations WHERE translation_id=?",
                (translation_id,),
            ).fetchone()[0]
        self.assertEqual(status, "COMMUNITY")
        with self.kb.connect() as db:
            contributor = db.execute(
                "SELECT contributor_id FROM translations WHERE translation_id=?",
                (translation_id,),
            ).fetchone()[0]
        self.assertEqual(contributor, "community-user")

    def test_only_authorized_reviewer_can_approve(self):
        translation_id = self.service.submit(
            "moderation-game", "Save game", "Lưu trò chơi",
            contributor_id="player-1",
        )
        with self.assertRaises(ModerationPermissionError):
            self.service.review(translation_id, "random-user", "APPROVE")
        outcome = self.service.review(
            translation_id, "trusted-reviewer", "APPROVE", note="Checked in game",
        )
        self.assertEqual(outcome.translation_status, "VERIFIED")
        with self.kb.connect() as db:
            verified_at = db.execute(
                "SELECT verified_at FROM translations WHERE translation_id=?",
                (translation_id,),
            ).fetchone()[0]
        self.assertIsNotNone(verified_at)

    def test_ai_reviewed_entries_are_visible_in_pending_queue(self):
        translation_id = self.kb.add_translation(
            "moderation-game", "Continue", "Tiếp tục",
            translation_source="AI", translation_status="AI_REVIEWED",
        )
        self.assertEqual(self.service.pending_queue()[0]["translation_id"], translation_id)

    def test_reported_verified_translation_can_be_rejected_after_review(self):
        translation_id = self.kb.add_translation(
            "moderation-game", "Open the vault", "Mở kho báu",
            translation_status="VERIFIED",
        )
        with self.assertRaises(ValueError):
            self.service.review(translation_id, "trusted-reviewer", "REJECT")
        self.service.report(translation_id, "player-9", "WRONG_CONTEXT")
        outcome = self.service.review(translation_id, "trusted-reviewer", "REJECT")
        self.assertEqual(outcome.translation_status, "REJECTED")
        with self.kb.connect() as db:
            status = db.execute(
                "SELECT translation_status FROM translations WHERE translation_id=?",
                (translation_id,),
            ).fetchone()[0]
        self.assertEqual(status, "REJECTED")

    def test_reports_are_queued_and_resolved_by_review(self):
        translation_id = self.service.submit(
            "moderation-game", "Wrong line", "Sai dòng",
            contributor_id="player-2",
        )
        report_id = self.service.report(
            translation_id, "player-3", "MISTRANSLATION", details="Context is a warning",
        )
        self.assertEqual(len(self.service.report_queue()), 1)
        self.assertEqual(len(self.service.pending_queue()), 1)
        self.service.review(translation_id, "trusted-reviewer", "REJECT")
        self.assertEqual(self.service.report_queue(), [])
        with self.kb.connect() as db:
            status = db.execute(
                "SELECT status FROM translation_reports WHERE report_id=?",
                (report_id,),
            ).fetchone()[0]
        self.assertEqual(status, "RESOLVED")


if __name__ == "__main__":
    unittest.main()
