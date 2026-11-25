"""CLI for importing an authorized game catalog metadata feed."""
from __future__ import annotations

import argparse
import json
import sys

from .catalog import CatalogRepository, CatalogValidationError


def main() -> int:
    parser = argparse.ArgumentParser(description="Import an authorized DịchGame catalog metadata feed.")
    parser.add_argument("--db", required=True)
    parser.add_argument("--registry", required=True)
    parser.add_argument("--feed", required=True)
    parser.add_argument("--confirm-authorized", action="store_true",
                        help="Confirm the metadata feed is authorized for this use.")
    args = parser.parse_args()
    try:
        repo = CatalogRepository(args.db)
        registered = repo.register_sources(args.registry)
        report = repo.import_feed(args.feed, confirm_authorized=args.confirm_authorized)
    except (CatalogValidationError, OSError, ValueError) as exc:
        print(json.dumps({"status": "REJECTED", "error": str(exc)}, ensure_ascii=False, indent=2))
        return 2
    print(json.dumps({"registered_sources": registered, **report.__dict__},
                     ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
