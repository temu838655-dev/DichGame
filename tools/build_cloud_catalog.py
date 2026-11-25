#!/usr/bin/env python3
"""Build a static, read-only DichGame catalog from authorized CSV files."""
import argparse
import csv
import hashlib
import io
import json
import pathlib
import re
from urllib.parse import urlsplit

def build(spec, base, out):
    u = urlsplit(base)
    if u.scheme != "https" or not u.hostname or u.username or u.password or u.query or u.fragment:
        raise ValueError("HTTPS base URL required")
    base = base.rstrip("/")
    games = []
    seen = set()
    ids = set()
    if len(spec) > 2000:
        raise ValueError("Too many games")
    out.mkdir(parents=True, exist_ok=False)
    for item in spec:
        game, slug, version = item["game"], item["id"], item["version"]
        if not isinstance(game, str) or not game.strip() or game.casefold() in seen:
            raise ValueError("Duplicate or empty game")
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", slug) or slug in ids:
            raise ValueError("Invalid or duplicate id")
        if type(version) is not int or not 1 <= version <= 2147483647:
            raise ValueError("Invalid version")
        seen.add(game.casefold())
        ids.add(slug)
        src = pathlib.Path(item["csv"])
        if src.stat().st_size > 24_000_000:
            raise ValueError("CSV too large")
        data = src.read_bytes()
        if not 1 <= len(data) <= 24_000_000:
            raise ValueError("CSV size invalid")
        with io.StringIO(data.decode("utf-8-sig"), newline="") as f:
            rows = csv.DictReader(f)
            if rows.fieldnames != ["source", "vi", "game"]:
                raise ValueError("Expected source,vi,game")
            count = 0
            for row in rows:
                count += 1
                if count > 100000 or row["game"] != game or not (row["source"] or "").strip() or not (row["vi"] or "").strip() or None in row:
                    raise ValueError("Invalid row or game")
            if not count:
                raise ValueError("Empty CSV")
        digest = hashlib.sha256(data).hexdigest()
        name = f"{slug}-{version}-{digest[:16]}.csv"
        manifest = f"{slug}.dichgame.json"
        (out / name).write_bytes(data)
        (out / manifest).write_text(json.dumps(dict(schema=1, game=game, version=version, url=f"{base}/{name}", bytes=len(data), sha256=digest), ensure_ascii=False), encoding="utf-8")
        games.append(dict(game=game, manifest=f"{base}/{manifest}"))
    catalog = json.dumps(dict(schema=1, games=games), ensure_ascii=False)
    if len(catalog.encode()) > 512 * 1024:
        raise ValueError("Catalog too large")
    (out / "catalog.json").write_text(catalog, encoding="utf-8")

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("spec", type=pathlib.Path)
    p.add_argument("base")
    p.add_argument("out", type=pathlib.Path)
    a = p.parse_args()
    build(json.loads(a.spec.read_text(encoding="utf-8")), a.base, a.out)
