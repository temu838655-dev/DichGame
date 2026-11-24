#!/usr/bin/env python3
"""Collect translated JSON batches into id,vi CSV; fail closed on missing IDs."""
import argparse
import csv
import json
import re
from pathlib import Path
from collections import Counter

PLACEHOLDER = re.compile(r"\{[^{}\n]+\}|%(?:\d+\$)?[sdif]|⟦\d+⟧")

def load(path):
    def unique(pairs):
        result = {}
        for k, v in pairs:
            if k in result:
                raise ValueError("Duplicate JSON key: " + k)
            result[k] = v
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)

def collect(prepared, translated, output):
    prepared, translated = Path(prepared), Path(translated)
    mapping = load(prepared / "id-map.json")
    expected = {}
    for path in sorted(prepared.glob("batch-*.json")):
        for row in load(path):
            key, source = row["id"], row["source"]
            if key in expected:
                raise ValueError("Duplicate source ID: " + key)
            expected[key] = source
    if not expected or set(mapping) != set(expected):
        raise ValueError("Source and ID map mismatch")
    all_ids = set()
    for key, ids in mapping.items():
        if not isinstance(ids, list) or not ids or ids[0] != key:
            raise ValueError("Invalid ID mapping")
        for original in ids:
            if not isinstance(original, str) or not original or original in all_ids:
                raise ValueError("Duplicate or invalid original ID")
            all_ids.add(original)
    results = {}
    for path in sorted(translated.glob("batch-*.json")):
        for row in load(path):
            if not isinstance(row, dict) or set(row) != {"id", "vi"}:
                raise ValueError("Expected objects with id and vi")
            key, vi = row["id"], row["vi"]
            if not isinstance(key, str) or key not in expected or key in results:
                raise ValueError("Unknown or duplicate translated ID")
            if not isinstance(vi, str) or not vi.strip():
                raise ValueError("Empty translation: " + key)
            if Counter(PLACEHOLDER.findall(expected[key])) != Counter(PLACEHOLDER.findall(vi)):
                raise ValueError("Placeholder mismatch: " + key)
            results[key] = vi.strip()
    missing = set(expected) - set(results)
    if missing:
        raise ValueError(f"Missing {len(missing)} translations")
    with Path(output).open("x", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "vi"])
        for key, ids in mapping.items():
            writer.writerows((original, results[key]) for original in ids)
    return {"unique_translations": len(results), "restored_rows": len(all_ids)}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("prepared")
    p.add_argument("translated")
    p.add_argument("output")
    a = p.parse_args()
    try:
        print(json.dumps(collect(a.prepared, a.translated, a.output)))
    except (ValueError, OSError, TypeError, KeyError) as e:
        p.error(str(e))

if __name__ == "__main__":
    main()
