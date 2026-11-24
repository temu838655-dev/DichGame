#!/usr/bin/env python3
"""Prepare authorized game text for batch translation without making API calls."""
import argparse
import csv
import json
from pathlib import Path


def prepare(source, output, max_rows=100, max_chars=12000):
    if not 1 <= max_rows <= 1000 or not 1 <= max_chars <= 100000:
        raise ValueError("Invalid batch limits")
    rows = {}
    with Path(source).open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f, strict=True)
        if reader.fieldnames != ["id", "source"]:
            raise ValueError("Expected id,source CSV")
        for row in reader:
            if None in row or any(v is None for v in row.values()):
                raise ValueError("Malformed CSV row")
            key, value = row["id"].strip(), row["source"].strip()
            if not key or not value or key in rows:
                raise ValueError("Empty text or duplicate ID")
            rows[key] = value
    if not rows:
        raise ValueError("Empty input")
    unique = {}
    for key, value in rows.items():
        unique.setdefault(value, []).append(key)
    batches, current, size = [], [], 2
    for value, ids in unique.items():
        item = {"id": ids[0], "source": value}
        cost = len(json.dumps(item, ensure_ascii=False)) + 2
        if cost + 2 > max_chars:
            raise ValueError("Single row exceeds character limit: " + ids[0])
        if current and (len(current) >= max_rows or size + cost > max_chars):
            batches.append(current)
            current, size = [], 2
        current.append(item)
        size += cost
    if current:
        batches.append(current)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    for i, batch in enumerate(batches, 1):
        (output / f"batch-{i:05d}.json").write_text(
            json.dumps(batch, ensure_ascii=False), encoding="utf-8")
    mapping = {ids[0]: ids for ids in unique.values()}
    (output / "id-map.json").write_text(json.dumps(mapping, ensure_ascii=False), encoding="utf-8")
    return {"rows": len(rows), "unique": len(unique), "batches": len(batches)}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("source")
    p.add_argument("output")
    p.add_argument("--max-rows", type=int, default=100)
    p.add_argument("--max-chars", type=int, default=12000)
    p.add_argument("--authorized", action="store_true")
    a = p.parse_args()
    if not a.authorized:
        p.error("Confirm permission to process the input with --authorized")
    try:
        print(json.dumps(prepare(a.source, a.output, a.max_rows, a.max_chars)))
    except (ValueError, OSError, csv.Error) as e:
        p.error(str(e))


if __name__ == "__main__":
    main()
