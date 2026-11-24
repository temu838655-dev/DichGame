"""Verify a generated pack before uploading; never modify files or call APIs."""
import argparse
import csv
import hashlib
import io
import unicodedata
import json
from pathlib import Path
from urllib.parse import urlsplit

def verify(directory, game):
    root = Path(directory)
    manifest = json.loads((root / "pack.dichgame.json").read_text(encoding="utf-8"))
    if not game.strip() or manifest.get("game") != game or type(manifest.get("schema")) is not int or manifest.get("schema") != 1:
        raise ValueError("Game or schema mismatch")
    if type(manifest.get("version")) is not int or manifest["version"] < 1:
        raise ValueError("Invalid version")
    url = urlsplit(manifest["url"])
    if url.scheme != "https" or not url.hostname or url.username or url.password or url.fragment:
        raise ValueError("Invalid pack URL")
    path = root / "pack.csv"
    if path.stat().st_size > 24 * 1024 * 1024:
        raise ValueError("Pack too large")
    data = path.read_bytes()
    if len(data) > 24 * 1024 * 1024 or type(manifest["bytes"]) is not int or len(data) != manifest["bytes"] or hashlib.sha256(data).hexdigest() != manifest["sha256"]:
        raise ValueError("Pack integrity mismatch")
    count = 0
    seen = set()
    with io.StringIO(data.decode("utf-8-sig"), newline="") as f:
        reader = csv.DictReader(f, strict=True)
        if reader.fieldnames != ["source", "vi", "game"]:
            raise ValueError("Invalid pack header")
        for row in reader:
            if None in row or any(v is None for v in row.values()):
                raise ValueError("Malformed row")
            if row["game"] != game or not row["source"].strip() or not row["vi"].strip():
                raise ValueError("Wrong game or empty translation")
            if any(any(ord(c) < 32 for c in v) for v in row.values()):
                raise ValueError("Control character or multiline field")
            key = " ".join(unicodedata.normalize("NFC", row["source"]).lower().split())
            if key in seen:
                raise ValueError("Duplicate normalized source")
            seen.add(key)
            count += 1
            if count > 100000:
                raise ValueError("Too many rows")
    if not count:
        raise ValueError("Empty pack")
    return {"game": game, "version": manifest["version"], "rows": count, "sha256": manifest["sha256"]}

if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("directory")
    p.add_argument("--game", required=True)
    a = p.parse_args()
    try:
        print(json.dumps(verify(a.directory, a.game), ensure_ascii=False))
    except (ValueError, OSError, KeyError, TypeError, csv.Error) as e:
        p.error(str(e))
