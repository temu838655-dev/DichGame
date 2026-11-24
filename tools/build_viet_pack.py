#!/usr/bin/env python3
"""Build a DichGame cloud pack from authorized UTF-8 CSV language exports."""
import argparse
import csv
import hashlib
import io
import json
import re
import tempfile
import unicodedata
from pathlib import Path
from urllib.parse import urlsplit

MAX_LINES = 100_000
MAX_BYTES = 24_000_000


def clean(text):
    return re.sub(r"[\r\n\t]+", " ", text).strip()


def identity(text):
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", text).strip().lower())


def read_language(path, text_column):
    rows = {}
    with Path(path).open(encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file, strict=True)
        if reader.fieldnames != ["id", text_column]:
            raise ValueError(f"{path}: expected header id,{text_column}")
        for row in reader:
            if None in row or any(v is None for v in row.values()):
                raise ValueError(f"{path}:{reader.line_num}: malformed row")
            key = row["id"].strip()
            value = clean(row[text_column])
            if not key or not value or any(ord(c) < 32 for c in key + value):
                raise ValueError(f"{path}:{reader.line_num}: empty or control character")
            if key in rows and rows[key] != value:
                raise ValueError(f"{path}:{reader.line_num}: conflicting duplicate ID {key!r}")
            rows[key] = value
    return rows


def pair(source, target):
    """Never pair by row position; omit all ambiguous source strings."""
    candidates = {}
    conflicts = set()
    for key in sorted(source.keys() & target.keys()):
        original, vi = source[key], target[key]
        canonical = identity(original)
        if canonical in candidates and candidates[canonical][1] != vi:
            conflicts.add(canonical)
        else:
            candidates.setdefault(canonical, (original, vi))
    pairs = [value for k, value in sorted(candidates.items()) if k not in conflicts]
    report = {
        "source_ids": len(source),
        "target_ids": len(target),
        "missing_translation_ids": len(source.keys() - target.keys()),
        "unmatched_target_ids": len(target.keys() - source.keys()),
        "ambiguous_source_strings_excluded": len(conflicts),
        "exported_pairs": len(pairs),
    }
    return pairs, report


def build(source, target, game, version, url, output, allow_partial=False):
    game = game.strip()
    if not game or any(ord(c) < 32 for c in game):
        raise ValueError("Game must be a nonempty single-line name")
    if not 1 <= version <= 2_147_483_647:
        raise ValueError("Version must be a positive Android Int")
    parsed = urlsplit(url)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username is not None
            or parsed.password is not None or "#" in url
            or any(c.isspace() or ord(c) < 32 for c in url)):
        raise ValueError("Use a direct HTTPS CSV URL without credentials or fragment")
    pairs, report = pair(read_language(source, "source"), read_language(target, "vi"))
    if not allow_partial and any(report[k] for k in (
            "missing_translation_ids", "unmatched_target_ids", "ambiguous_source_strings_excluded")):
        raise ValueError("Unmatched or ambiguous data: " + json.dumps(report) +
                         ". Review inputs or explicitly use --allow-partial.")
    if not 1 <= len(pairs) <= MAX_LINES:
        raise ValueError("Pack must contain 1–100000 unambiguous pairs")
    stream = io.StringIO(newline="")
    writer = csv.writer(stream, lineterminator="\r\n")
    writer.writerow(["source", "vi", "game"])
    writer.writerows((src, vi, game) for src, vi in pairs)
    data = stream.getvalue().encode("utf-8")
    if len(data) > MAX_BYTES:
        raise ValueError("Pack exceeds Android's 24 MB limit")
    manifest = {"schema": 1, "game": game, "version": version, "url": url,
                "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    output = Path(output)
    # Exclusive directory creation: cannot overwrite an older published pack.
    output.mkdir(parents=True, exist_ok=False)
    try:
        (output / "pack.csv").write_bytes(data)
        for name, payload in [("pack.dichgame.json", manifest), ("report.json", report)]:
            (output / name).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8")
    except Exception:
        for name in ("pack.csv", "pack.dichgame.json", "report.json"):
            (output / name).unlink(missing_ok=True)
        output.rmdir()
        raise
    return report


def self_test():
    import unittest

    class BuilderTests(unittest.TestCase):
        def test_id_join(self):
            pairs, report = pair({"2": "Start", "1": "Continue"}, {"1": "Tiếp tục", "2": "Bắt đầu"})
            self.assertEqual(dict(pairs), {"Start": "Bắt đầu", "Continue": "Tiếp tục"})
            self.assertEqual(report["exported_pairs"], 2)

        def test_ambiguous(self):
            pairs, report = pair({"1": "Run!", "2": " RUN! ", "3": "Stop"},
                                 {"1": "Chạy!", "2": "Khởi chạy!", "3": "Dừng"})
            self.assertEqual(pairs, [("Stop", "Dừng")])
            self.assertEqual(report["ambiguous_source_strings_excluded"], 1)

        def test_missing_id(self):
            _, report = pair({"1": "Start"}, {"2": "Bắt đầu"})
            self.assertEqual(report["missing_translation_ids"], 1)
            self.assertEqual(report["unmatched_target_ids"], 1)

        def test_manifest_and_csv(self):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                en, vi = root / "en.csv", root / "vi.csv"
                en.write_text('id,source\n1,"Hello, friend"\n', encoding="utf-8")
                vi.write_text('id,vi\n1,"Chào, bạn"\n', encoding="utf-8")
                out = root / "out"
                build(en, vi, "Example Game", 1, "https://example.com/pack.csv", out)
                data = (out / "pack.csv").read_bytes()
                m = json.loads((out / "pack.dichgame.json").read_text(encoding="utf-8"))
                self.assertEqual(m["bytes"], len(data))
                self.assertEqual(m["sha256"], hashlib.sha256(data).hexdigest())
                self.assertEqual(list(csv.reader(io.StringIO(data.decode("utf-8"))))[1],
                                 ["Hello, friend", "Chào, bạn", "Example Game"])
                with self.assertRaises(FileExistsError):
                    build(en, vi, "Example Game", 1, "https://example.com/pack.csv", out)
                with self.assertRaises(ValueError):
                    build(en, vi, "Example Game", 1, "http://example.com/pack.csv", root / "bad")

        def test_duplicate_id_and_partial(self):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                en, vi = root / "en.csv", root / "vi.csv"
                en.write_text("id,source\n1,Start\n1,Stop\n", encoding="utf-8")
                with self.assertRaises(ValueError):
                    read_language(en, "source")
                en.write_text("id,source\n1,Start\n2,Stop\n", encoding="utf-8")
                vi.write_text("id,vi\n1,Bắt đầu\n", encoding="utf-8")
                with self.assertRaises(ValueError):
                    build(en, vi, "Game", 1, "https://example.com/pack.csv", root / "out")
                self.assertFalse((root / "out").exists())
                report = build(en, vi, "Game", 1, "https://example.com/pack.csv",
                               root / "out", allow_partial=True)
                self.assertEqual(report["exported_pairs"], 1)

    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(BuilderTests))
    return 0 if result.wasSuccessful() else 1


def main():
    import sys
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, help="CSV id,source")
    parser.add_argument("--vi", required=True, help="CSV id,vi")
    parser.add_argument("--game", required=True)
    parser.add_argument("--version", required=True, type=int)
    parser.add_argument("--url", required=True, help="Final HTTPS URL of pack.csv")
    parser.add_argument("--output", required=True, help="New output directory")
    parser.add_argument("--allow-partial", action="store_true")
    parser.add_argument("--authorized", action="store_true",
                        help="Confirm permission to use and distribute both inputs")
    args = parser.parse_args()
    if not args.authorized:
        parser.error("Only use data you have permission to distribute; confirm --authorized")
    try:
        report = build(args.source, args.vi, args.game, args.version, args.url,
                       args.output, args.allow_partial)
    except (ValueError, OSError, csv.Error) as error:
        parser.error(str(error))
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
