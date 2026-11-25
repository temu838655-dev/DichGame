"""Community contribution and human-review workflow.

Authentication and reviewer authorization are supplied by the host app.
This module never treats a public submitter as a reviewer.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

from .db import KnowledgeBase


class ModerationPermissionError(PermissionError):
    pass


@dataclass(frozen=True)
class ReviewOutcome:
    review_id: int
    translation_status: str


ReviewerAuthorizer = Callable[[str], bool]


class CommunityModeration:
    def __init__(
        self,
        knowledge_base: KnowledgeBase,
        reviewer_authorizer: ReviewerAuthorizer,
    ) -> None:
        self.kb = knowledge_base
        self.reviewer_authorizer = reviewer_authorizer

    def submit(
        self,
        game_id: str,
        source_text: str,
        translated_text: str,
        *,
        contributor_id: str,
        context: str | None = None,
        speaker: str | None = None,
        source_id: int | None = None,
    ) -> int:
        if not contributor_id.strip():
            raise ValueError("contributor_id is required")
        # Contributions are always pending community content, never verified.
        return self.kb.add_translation(
            game_id, source_text, translated_text,
            context=context, speaker=speaker, source_id=source_id,
            contributor_id=contributor_id.strip(),
            translation_source="COMMUNITY",
            translation_status="COMMUNITY",
        )

    def report(
        self,
        translation_id: int,
        reporter_id: str,
        reason: str,
        *,
        details: str | None = None,
    ) -> int:
        if not reporter_id.strip() or not reason.strip():
            raise ValueError("reporter_id and reason are required")
        with self.kb.transaction() as db:
            exists = db.execute(
                "SELECT 1 FROM translations WHERE translation_id=?",
                (translation_id,),
            ).fetchone()
            if exists is None:
                raise ValueError("translation does not exist")
            cursor = db.execute(
                """INSERT INTO translation_reports(
                       translation_id, reporter_id, reason, details
                   ) VALUES (?, ?, ?, ?)""",
                (translation_id, reporter_id, reason.strip(), details),
            )
            return int(cursor.lastrowid)

    def review(
        self,
        translation_id: int,
        reviewer_id: str,
        decision: str,
        *,
        note: str | None = None,
    ) -> ReviewOutcome:
        if not reviewer_id.strip() or not self.reviewer_authorizer(reviewer_id):
            raise ModerationPermissionError("Reviewer is not authorized")
        decision = decision.strip().upper()
        if decision not in {"APPROVE", "REJECT", "REQUEST_CHANGES"}:
            raise ValueError("Unsupported review decision")

        with self.kb.transaction() as db:
            row = db.execute(
                "SELECT translation_status FROM translations WHERE translation_id=?",
                (translation_id,),
            ).fetchone()
            if row is None:
                raise ValueError("translation does not exist")
            if row["translation_status"] not in {"COMMUNITY", "AI_UNVERIFIED", "AI_REVIEWED"}:
                raise ValueError("Only pending or reviewable translations can be moderated")
            cursor = db.execute(
                """INSERT INTO translation_reviews(
                       translation_id, reviewer_id, decision, note
                   ) VALUES (?, ?, ?, ?)""",
                (translation_id, reviewer_id, decision, note),
            )
            new_status = {
                "APPROVE": "VERIFIED",
                "REJECT": "REJECTED",
                "REQUEST_CHANGES": "COMMUNITY",
            }[decision]
            db.execute(
                """UPDATE translations SET translation_status=?,
                       verified_at=CASE WHEN ?='VERIFIED'
                           THEN strftime('%Y-%m-%dT%H:%M:%fZ','now')
                           ELSE NULL END,
                       updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')
                   WHERE translation_id=?""",
                (new_status, new_status, translation_id),
            )
            if decision in {"APPROVE", "REJECT"}:
                db.execute(
                    """UPDATE translation_reports SET status='RESOLVED',
                           resolved_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')
                       WHERE translation_id=? AND status='OPEN'""",
                    (translation_id,),
                )
            return ReviewOutcome(int(cursor.lastrowid), new_status)

    def pending_queue(self, limit: int = 50) -> list[dict]:
        limit = max(1, min(int(limit), 200))
        with self.kb.connect() as db:
            rows = db.execute(
                """SELECT t.*, COUNT(DISTINCT r.report_id) AS open_reports,
                          MAX(r.created_at) AS latest_report_at
                   FROM translations t
                   LEFT JOIN translation_reports r
                     ON r.translation_id=t.translation_id AND r.status='OPEN'
                   WHERE t.translation_status IN ('COMMUNITY','AI_UNVERIFIED','AI_REVIEWED')
                   GROUP BY t.translation_id
                   ORDER BY open_reports DESC, t.created_at ASC
                   LIMIT ?""",
                (limit,),
            ).fetchall()
            return [dict(row) for row in rows]

    def report_queue(self, limit: int = 50) -> list[dict]:
        limit = max(1, min(int(limit), 200))
        with self.kb.connect() as db:
            rows = db.execute(
                """SELECT r.*, t.game_id, t.source_text, t.translated_text
                   FROM translation_reports r
                   JOIN translations t ON t.translation_id=r.translation_id
                   WHERE r.status='OPEN'
                   ORDER BY r.created_at ASC LIMIT ?""",
                (limit,),
            ).fetchall()
            return [dict(row) for row in rows]
