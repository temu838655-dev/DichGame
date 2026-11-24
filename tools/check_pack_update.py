"""Offline preflight for replacing a published pack manifest."""
import argparse
import json
from pathlib import Path
from verify_pack import verify

def check(current, candidate, game):
    old = verify(current, game)
    new = verify(candidate, game)
    if new["version"] <= old["version"]:
        raise ValueError("New pack version must increase")
    old_url = json.loads((Path(current) / "pack.dichgame.json").read_text(encoding="utf-8"))["url"]
    new_url = json.loads((Path(candidate) / "pack.dichgame.json").read_text(encoding="utf-8"))["url"]
    if old_url == new_url:
        raise ValueError("Use a separate immutable URL for each version")
    return {"previous": old, "candidate": new, "removed_row_count_warning": new["rows"] < old["rows"]}

if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("current")
    p.add_argument("candidate")
    p.add_argument("--game", required=True)
    a = p.parse_args()
    try:
        print(json.dumps(check(a.current, a.candidate, a.game), ensure_ascii=False))
    except (ValueError, OSError, KeyError, TypeError) as e:
        p.error(str(e))
