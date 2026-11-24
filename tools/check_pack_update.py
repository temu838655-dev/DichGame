"""Offline preflight for replacing a published pack manifest."""
import argparse
import csv
import unicodedata
import json
from pathlib import Path
from verify_pack import verify

def entries(root):
    with (Path(root) / "pack.csv").open(encoding="utf-8-sig", newline="") as f:
        result = {}
        for row in csv.DictReader(f):
            key = " ".join(unicodedata.normalize("NFC", row["source"]).lower().split())
            if key in result and result[key] != row["vi"]:
                raise ValueError("Conflicting normalized source")
            result[key] = row["vi"]
        return result

def check(current, candidate, game, allow_removals=False):
    old = verify(current, game)
    new = verify(candidate, game)
    if new["version"] <= old["version"]:
        raise ValueError("New pack version must increase")
    old_url = json.loads((Path(current) / "pack.dichgame.json").read_text(encoding="utf-8"))["url"]
    new_url = json.loads((Path(candidate) / "pack.dichgame.json").read_text(encoding="utf-8"))["url"]
    if old_url == new_url:
        raise ValueError("Use a separate immutable URL for each version")
    before, after = entries(current), entries(candidate)
    removed = before.keys() - after.keys()
    if removed and not allow_removals:
        raise ValueError(f"Update would remove {len(removed)} existing source entries")
    return {"previous": old, "candidate": new, "removed": len(removed),
            "added": len(after.keys() - before.keys()),
            "changed": sum(before[k] != after[k] for k in before.keys() & after.keys())}

if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("current")
    p.add_argument("candidate")
    p.add_argument("--game", required=True)
    p.add_argument("--allow-removals", action="store_true")
    a = p.parse_args()
    try:
        print(json.dumps(check(a.current, a.candidate, a.game, a.allow_removals), ensure_ascii=False))
    except (ValueError, OSError, KeyError, TypeError) as e:
        p.error(str(e))
