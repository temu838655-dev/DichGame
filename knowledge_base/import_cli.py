"""Command line entry point for local, permission-checked localization imports."""
from __future__ import annotations

import argparse
import json
import sys

from .db import KnowledgeBase
from .importers import ImportValidationError, TranslationImporter


def main() -> int:
    parser = argparse.ArgumentParser(description="Import a legally reusable game localization file.")
    parser.add_argument("--db", required=True, help="Path to the knowledge-base SQLite database")
    parser.add_argument("--game-id", required=True)
    parser.add_argument("--file", required=True)
    parser.add_argument("--source-name", required=True)
    parser.add_argument("--license-id", required=True, help="Specific license or permission identifier")
    parser.add_argument("--source-url")
    parser.add_argument("--author")
    parser.add_argument("--license-url")
    parser.add_argument("--attribution")
    parser.add_argument("--source-only", action="store_true", help="Stage original Excel strings, including rows without Vietnamese translations")
    parser.add_argument("--confirm-rights", action="store_true",
                        help="Confirm that you have the right to import and reuse this content")
    args = parser.parse_args()

    try:
        kb = KnowledgeBase(args.db)
        importer = TranslationImporter(kb)
        import_method = importer.import_source_workbook if args.source_only else importer.import_file
        report = import_method(
            args.game_id, args.file, source_name=args.source_name,
            license_id=args.license_id, source_url=args.source_url,
            author_name=args.author, license_url=args.license_url,
            attribution=args.attribution, confirm_rights=args.confirm_rights,
        )
    except (ImportValidationError, OSError, ValueError) as exc:
        print(json.dumps({"status": "REJECTED", "error": str(exc)}, ensure_ascii=False, indent=2))
        return 2
    print(json.dumps(report.__dict__, ensure_ascii=False, indent=2))
    return 0 if report.status == "COMPLETED" else 1


if __name__ == "__main__":
    sys.exit(main())
