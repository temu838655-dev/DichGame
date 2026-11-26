"""Stage and optionally translate GTA VI workbook rows into the private DịchGame D1 API.

The script never writes source dialogue to GitHub. It reads the workbook locally and sends
small authorized batches to the authenticated Cloudflare admin endpoint.
"""
from __future__ import annotations

import argparse
import json
import os
import re
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
    parser.add_argument("--source-name", default="", help="Name of the source workbook or localization pack")
    parser.add_argument("--license-id", required=True, help="Declared permission/license identifier, e.g. PERMISSION-GRANTED when accurate")
    parser.add_argument("--source-url", default="")
    parser.add_argument("--license-url", default="")
    parser.add_argument("--attribution", default="")
    parser.add_argument("--model", default=os.environ.get("GEMINI_MODEL", ""))
    parser.add_argument("--max-rows", type=int, default=50, help="Maximum new rows per run; use 0 for all remaining rows.")
    parser.add_argument("--max-output-tokens", type=int, default=4096)
    parser.add_argument("--translation-batch-size", type=int, default=10)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--translate", action="store_true", help="Translate blank Vietnamese cells with Gemini; requires GEMINI_API_KEY.")
    parser.add_argument("--confirm-rights", action="store_true", required=True, help="Confirm you have permission for this source and intended distribution.")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.file.is_file() or args.file.suffix.lower() != ".xlsx":
        parser.error("--file must point to an existing .xlsx workbook")
    if args.max_rows < 0 or args.max_output_tokens < 1 or args.retries < 0 or not 1 <= args.translation_batch_size <= 20:
        parser.error("Invalid batch limits; translation batch size must be 1-20")
    token = os.environ.get("DICHGAME_ADMIN_TOKEN", "")
    if not args.dry_run and len(token) < 24:
        parser.error("Set DICHGAME_ADMIN_TOKEN to the existing DịchGame Worker ADMIN_TOKEN")
    provider = None
    if args.translate and not args.dry_run:
        if not args.model:
            parser.error("Set --model or GEMINI_MODEL to a Gemini model enabled for your key")
        provider = GeminiProvider.from_env()
    stats = {"rows_scanned": 0, "rows_staged": 0, "rows_with_translation": 0, "already_present_skipped": 0, "translation_failed": 0}
    pending_upload: list[dict] = []
    new_rows = 0
    stop = False

    def upload() -> None:
        if not pending_upload or args.dry_run:
            pending_upload.clear()
            return
        request_json(args.api.rstrip("/") + "/v1/admin/gta6/batch", token, {
            "game": GAME, "rightsConfirmed": True,
            "sourceName": args.source_name.strip() or args.file.name,
            "licenseId": args.license_id.strip(),
            "sourceUrl": args.source_url.strip(),
            "licenseUrl": args.license_url.strip(),
            "attribution": args.attribution.strip(),
            "lines": list(pending_upload),
        })
        stats["rows_staged"] += len(pending_upload)
        stats["rows_with_translation"] += sum(bool(row["vi"]) for row in pending_upload)
        pending_upload.clear()

    def process_chunk(chunk: list[dict]) -> None:
        nonlocal new_rows, stop
        if not chunk or stop:
            return
        known = {}
        unique = {}
        for row in chunk:
            previous = unique.get(row["external_id"])
            if previous and previous["original"] != row["original"]:
                raise RuntimeError(f"Duplicate external ID has conflicting English text: {row['external_id']}")
            unique[row["external_id"]] = row
        chunk = list(unique.values())
        if not args.dry_run:
            response = request_json(args.api.rstrip("/") + "/v1/admin/gta6/check", token,
                                    {"ids": [row["external_id"] for row in chunk]})
            known = {item["external_id"]: item for item in response.get("lines", [])}
        to_translate = []
        for row in chunk:
            if args.max_rows and new_rows >= args.max_rows:
                stop = True
                break
            stats["rows_scanned"] += 1
            existing = known.get(row["external_id"])
            if existing and existing.get("status") in {"VERIFIED", "AI_UNVERIFIED", "REJECTED"}:
                stats["already_present_skipped"] += 1
                continue
            new_rows += 1
            if not row["vi"] and provider is not None:
                to_translate.append(row)
            else:
                pending_upload.append(row)
        for offset in range(0, len(to_translate), args.translation_batch_size):
            group = to_translate[offset:offset + args.translation_batch_size]
            translated = None
            for attempt in range(args.retries + 1):
                try:
                    translated = provider.translate_many([
                        {"id": row["external_id"], "text": row["original"],
                         "context": f"{row['sheet_name']} | {row['group_name']}"}
                        for row in group
                    ], model=args.model, max_output_tokens=args.max_output_tokens)
                    break
                except Exception as exc:
                    if attempt >= args.retries:
                        print(f"BATCH_TRANSLATION_FAILED ({len(group)} rows): {exc}")
                    else:
                        time.sleep(1.0 * (2 ** attempt))
            for row in group:
                value = translated.get(row["external_id"], "").strip() if translated else ""
                pattern = r"\{[^{}]+\}|%[A-Za-z0-9_]+%|~[^~]+~|<[^>]+>|\d+(?:[.:]\d+)?"
                source_tokens = re.findall(pattern, row["original"])
                target_tokens = re.findall(pattern, value)
                if not value or source_tokens != target_tokens:
                    stats["translation_failed"] += 1
                    if value:
                        print(f"TOKEN_MISMATCH {row['external_id']}: left pending; no translation uploaded")
                else:
                    row["vi"] = value
                pending_upload.append(row)
                batch_preview = {
                    "game": GAME, "rightsConfirmed": True,
                    "sourceName": args.source_name.strip() or args.file.name,
                    "licenseId": args.license_id.strip(),
                    "sourceUrl": args.source_url.strip(),
                    "licenseUrl": args.license_url.strip(),
                    "attribution": args.attribution.strip(),
                    "lines": pending_upload,
                }
                if len(pending_upload) >= 30 or len(json.dumps(batch_preview, ensure_ascii=False).encode("utf-8")) >= 14_000:
                    upload()
        batch_preview = {
            "game": GAME, "rightsConfirmed": True,
            "sourceName": args.source_name.strip() or args.file.name,
            "licenseId": args.license_id.strip(),
            "sourceUrl": args.source_url.strip(),
            "licenseUrl": args.license_url.strip(),
            "attribution": args.attribution.strip(),
            "lines": pending_upload,
        }
        if pending_upload and len(json.dumps(batch_preview, ensure_ascii=False).encode("utf-8")) >= 14_000:
            upload()

    try:
        chunk = []
        for row in rows_from_workbook(args.file):
            chunk.append(row)
            if len(chunk) == 100:
                process_chunk(chunk)
                chunk = []
                if stop:
                    break
                if stats["rows_scanned"] and stats["rows_scanned"] % 500 == 0:
                    print(json.dumps(stats, ensure_ascii=False))
        if chunk and not stop:
            process_chunk(chunk)
        upload()
    except KeyboardInterrupt:
        upload()
        print("Stopped by user; uploaded batches remain saved and the next run resumes by ID.")
        return 130
    except Exception as exc:
        upload()
        print(f"ERROR: {exc}")
        return 2
    stats["dry_run"] = args.dry_run
    print(json.dumps(stats, ensure_ascii=False, indent=2))
    return 0 if stats["translation_failed"] == 0 else 1

if __name__ == "__main__":
    raise SystemExit(main())
