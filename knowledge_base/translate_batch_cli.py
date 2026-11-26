"""Translate a bounded number of staged strings and resume safely on the next run."""
from __future__ import annotations

import argparse
import json

from .batch_translate import BatchTranslator
from .db import KnowledgeBase
from .providers import GeminiProvider


def main() -> int:
    parser = argparse.ArgumentParser(description="Translate a bounded batch of staged game strings with Gemini.")
    parser.add_argument("--db", required=True)
    parser.add_argument("--game-id", required=True)
    parser.add_argument("--model", required=True, help="Gemini model name enabled for your API key")
    parser.add_argument("--batch-size", type=int, default=50)
    parser.add_argument("--max-output-tokens", type=int, default=512)
    parser.add_argument("--retries", type=int, default=2)
    args = parser.parse_args()
    try:
        kb = KnowledgeBase(args.db)
        worker = BatchTranslator(
            kb, GeminiProvider.from_env(), model=args.model,
            max_output_tokens=args.max_output_tokens, retries=args.retries,
        )
        report = worker.run(args.game_id, limit=args.batch_size)
    except Exception as exc:
        print(json.dumps({"status": "FAILED", "error": str(exc)}, ensure_ascii=False, indent=2))
        return 2
    print(json.dumps(report.__dict__, ensure_ascii=False, indent=2))
    return 0 if report.failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
