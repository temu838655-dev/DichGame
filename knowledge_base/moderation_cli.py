"""Trusted local maintainer CLI for community translation moderation.

This is an operator tool, not a public HTTP API or authentication system.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

from .db import KnowledgeBase
from .moderation import CommunityModeration, ModerationPermissionError


def _moderation(kb: KnowledgeBase) -> CommunityModeration:
    allowed = {
        item.strip()
        for item in os.environ.get("DICHGAME_REVIEWER_IDS", "").split(",")
        if item.strip()
    }
    return CommunityModeration(kb, reviewer_authorizer=lambda reviewer: reviewer in allowed)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Local community translation moderation tools.")
    parser.add_argument("--db", required=True, help="SQLite knowledge-base path")
    commands = parser.add_subparsers(dest="command", required=True)

    submit = commands.add_parser("submit", help="Submit a community translation (never VERIFIED)")
    submit.add_argument("--game-id", required=True)
    submit.add_argument("--source", required=True)
    submit.add_argument("--translation", required=True)
    submit.add_argument("--contributor-id", required=True)
    submit.add_argument("--context")
    submit.add_argument("--speaker")

    report = commands.add_parser("report", help="Report a translation")
    report.add_argument("--translation-id", type=int, required=True)
    report.add_argument("--reporter-id", required=True)
    report.add_argument("--reason", required=True)
    report.add_argument("--details")

    commands.add_parser("queue", help="List pending community and AI-review entries")
    commands.add_parser("reports", help="List open player reports")

    review = commands.add_parser("review", help="Review a pending translation")
    review.add_argument("--translation-id", type=int, required=True)
    review.add_argument("--reviewer-id", required=True)
    review.add_argument("--decision", choices=("APPROVE", "REJECT", "REQUEST_CHANGES"), required=True)
    review.add_argument("--note")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        kb = KnowledgeBase(args.db)
        moderation = _moderation(kb)
        if args.command == "submit":
            translation_id = moderation.submit(
                args.game_id, args.source, args.translation,
                contributor_id=args.contributor_id,
                context=args.context, speaker=args.speaker,
            )
            result = {"translation_id": translation_id, "translation_status": "COMMUNITY"}
        elif args.command == "report":
            report_id = moderation.report(
                args.translation_id, args.reporter_id, args.reason, details=args.details,
            )
            result = {"report_id": report_id, "status": "OPEN"}
        elif args.command == "queue":
            result = moderation.pending_queue()
        elif args.command == "reports":
            result = moderation.report_queue()
        elif args.command == "review":
            outcome = moderation.review(
                args.translation_id, args.reviewer_id, args.decision, note=args.note,
            )
            result = {
                "review_id": outcome.review_id,
                "translation_status": outcome.translation_status,
            }
        else:  # pragma: no cover - argparse enforces known commands
            raise ValueError("Unsupported command")
    except (OSError, ValueError, ModerationPermissionError) as exc:
        print(json.dumps({"status": "ERROR", "error": str(exc)}, ensure_ascii=False))
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
