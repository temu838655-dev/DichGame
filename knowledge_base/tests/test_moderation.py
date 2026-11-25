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
