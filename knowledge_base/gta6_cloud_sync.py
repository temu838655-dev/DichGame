"""Stage and optionally translate GTA VI workbook rows into the private DịchGame D1 API.

The script never writes source dialogue to GitHub. It reads the workbook locally and sends
small authorized batches to the authenticated Cloudflare admin endpoint.
"""
from __future__ import annotations

import argparse
import json
import os
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from openpyxl import load_workbook

from .providers import GeminiProvider

DEFAULT_API = "https://dichgame-community.temu838655.workers.dev"
GAME = "Grand Theft Auto VI"


def request_json(url: str, token: str, body: dict | None = None) -> dict:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=data, method="GET" if body is None else "POST", headers={
        "Accept": "application/json", "Authorization": f"Bearer {token}",
        **({"Content-Type": "application/json"} if data is not None else {}),
        "User-Agent": "DichGame-GTA6-Importer/1.0",
    })
    try:
        with urlopen(request, timeout=30) as response:
            raw = response.read(256_001)
            if len(raw) > 256_000:
                raise RuntimeError("Cloud API response exceeded 256 KB")
    except HTTPError as exc:
        detail = exc.read(2048).decode("utf-8", "replace")
        raise RuntimeError(f"Cloud API HTTP {exc.code}: {detail}") from None
    except URLError:
        raise RuntimeError("Cannot connect to the DịchGame Cloud API") from None
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise RuntimeError("Cloud API returned invalid JSON") from None
    if not isinstance(value, dict):
        raise RuntimeError("Cloud API returned an unexpected response")
    return value


def clean(value) -> str:
    return str(value).strip() if value is not None else ""


def rows_from_workbook(path: Path):
    workbook = load_workbook(path, read_only=True, data_only=True)
    try:
        for sheet in workbook.worksheets:
            iterator = sheet.iter_rows(values_only=True)
            headers = next(iterator, None)
            if not headers:
                continue
            names = [clean(value).casefold() for value in headers]
            aliases = {
                "id": ("id", "external_id", "string_id"),
                "english": ("english", "tiếng anh", "source_text", "source"),
                "vietnamese": ("tiếng việt", "vietnamese", "translated_text", "translation", "vi"),
                "group": ("nhóm", "nhom", "group", "category"),
            }
            positions = {key: next((names.index(alias) for alias in choices if alias in names), -1)
                         for key, choices in aliases.items()}
            for row_number, values in enumerate(iterator, start=2):
                def get(key):
                    index = positions[key]
                    return clean(values[index]) if 0 <= index < len(values) else ""
                external_id, source = get("id"), get("english")
                if external_id and source:
                    yield {
                        "external_id": external_id, "original": source, "vi": get("vietnamese"),
                        "group_name": get("group"), "sheet_name": sheet.title, "row_number": row_number,
                    }
    finally:
        workbook.close()


def translate_one(provider, row: dict, model: str, max_tokens: int, retries: int) -> str:
    context = f"Grand Theft Auto VI | {row['sheet_name']} | {row['group_name']}"
    for attempt in range(retries + 1):
        try:
            response = provider.translate(row["original"], context, model=model, max_output_tokens=max_tokens)
            result = response.translated_text.strip()
            if not result:
                raise RuntimeError("Gemini returned an empty translation")
            return result
        except Exception:
            if attempt >= retries:
                raise
            time.sleep(1.0 * (2 ** attempt))
    raise RuntimeError("Translation failed")


def main() -> int:
    parser = argparse.ArgumentParser(description="Stage or translate GTA VI Excel rows into the private DịchGame Cloud database.")
    parser.add_argument("--file", required=True, type=Path)
    parser.add_argument("--api", default=os.environ.get("DICHGAME_COMMUNITY_API", DEFAULT_API))
    parser.add_argument("--model", default=os.environ.get("GEMINI_MODEL", ""))
    parser.add_argument("--max-rows", type=int, default=50, help="Maximum new rows to stage/translate in this run; use 0 for all remaining rows.")
    parser.add_argument("--max-output-tokens", type=int, default=512)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--translate", action="store_true", help="Translate blank Vietnamese cells with Gemini; requires GEMINI_API_KEY.")
    parser.add_argument("--confirm-rights", action="store_true", required=True, help="Confirm you have permission for this source and intended distribution.")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.file.is_file() or args.file.suffix.lower() != ".xlsx":
        parser.error("--file must point to an existing .xlsx workbook")
    if args.max_rows < 0 or args.max_output_tokens < 1 or args.retries < 0:
        parser.error("Invalid batch limits")
    token = os.environ.get("DICHGAME_ADMIN_TOKEN", "")
    if not args.dry_run and len(token) < 24:
        parser.error("Set DICHGAME_ADMIN_TOKEN to the existing DịchGame Worker ADMIN_TOKEN")
    provider = None
    if args.translate:
        if not args.model:
            parser.error("Set --model or GEMINI_MODEL to a Gemini model enabled for your key")
        provider = GeminiProvider.from_env()
    checked = staged = translated = skipped = failed = 0
    pending_upload = []

    def upload():
        nonlocal staged, translated, pending_upload
        if not pending_upload or args.dry_run:
            pending_upload = []
            return
        request_json(args.api.rstrip("/") + "/v1/admin/gta6/batch", token, {
            "game": GAME, "rightsConfirmed": True, "lines": pending_upload,
        })
        staged += len(pending_upload)
        translated += sum(bool(row["vi"]) for row in pending_upload)
        pending_upload = []

    try:
        for row in rows_from_workbook(args.file):
            checked += 1
            if args.max_rows and (staged + len(pending_upload) + skipped) >= args.max_rows:
                break
            if not args.dry_run:
                known = request_json(args.api.rstrip("/") + "/v1/admin/gta6/check", token, {"ids": [row["external_id"]]})
                existing = next((item for item in known.get("lines", []) if item.get("external_id") == row["external_id"]), None)
                if existing and existing.get("status") in {"VERIFIED", "AI_UNVERIFIED"}:
                    skipped += 1
                    continue
            if not row["vi"] and provider is not None:
                try:
                    row["vi"] = translate_one(provider, row, args.model, args.max_output_tokens, args.retries)
                except Exception as exc:
                    failed += 1
                    print(f"TRANSLATION_FAILED {row['external_id']}: {exc}")
                    continue
            pending_upload.append(row)
            if len(pending_upload) >= 5:
                upload()
            if checked % 25 == 0:
                print(f"Read {checked}; staged {staged + len(pending_upload)}; translated {translated}; skipped {skipped}; failed {failed}")
        upload()
    except KeyboardInterrupt:
        upload()
        print("Stopped by user; uploaded batches remain saved and the next run resumes by ID.")
        return 130
    except Exception as exc:
        upload()
        print(f"ERROR: {exc}")
        return 2
    print(json.dumps({
        "rows_scanned": checked, "rows_staged": staged, "rows_with_translation": translated,
        "already_present_skipped": skipped, "translation_failed": failed, "dry_run": args.dry_run,
    }, ensure_ascii=False, indent=2))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
