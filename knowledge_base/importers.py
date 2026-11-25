"""License-aware importers for common localization exchange formats.

Supported formats: CSV, JSON, PO, XLIFF 1.x/2.x, and TMX.
No remote fetching is performed. The caller supplies a local file and source
provenance; this module never assumes that a publicly reachable file is reusable.
"""
from __future__ import annotations

import csv
import hashlib
import json
import re
import sqlite3
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Iterator

from .db import KnowledgeBase, normalize_text, sha256_text

SUPPORTED_FORMATS = {"csv", "json", "po", "xliff", "xlf", "tmx"}
BLOCKED_LICENSE_IDS = {"", "unknown", "none", "n/a", "na", "unlicensed", "all-rights-reserved"}


class ImportValidationError(ValueError):
    """Raised when an import cannot be safely interpreted."""


@dataclass(frozen=True)
class TranslationRow:
    source_text: str
    translated_text: str
    context: str | None = None
    speaker: str | None = None
    scene_id: str | None = None
    mission_id: str | None = None


@dataclass
class ImportReport:
    import_id: int
    file_name: str
    file_format: str
    imported: int
    skipped: int
    rejected: int
    status: str
    errors: list[str]


def _clean(value: object) -> str:
    return str(value).strip() if value is not None else ""


def _first(mapping: dict, keys: tuple[str, ...]) -> str:
    lowered = {str(k).strip().lower(): v for k, v in mapping.items()}
    for key in keys:
        value = _clean(lowered.get(key))
        if value:
            return value
    return ""


SOURCE_KEYS = ("source_text", "source", "original", "msgid", "english", "en", "text")
TARGET_KEYS = ("translated_text", "translation", "target", "msgstr", "vietnamese", "vi")
CONTEXT_KEYS = ("context", "extracomment", "developer_comment", "description")
SPEAKER_KEYS = ("speaker", "character")
SCENE_KEYS = ("scene_id", "scene")
MISSION_KEYS = ("mission_id", "mission")


def _row(mapping: dict) -> TranslationRow | None:
    source = _first(mapping, SOURCE_KEYS)
    target = _first(mapping, TARGET_KEYS)
    if not source or not target:
        return None
    return TranslationRow(
        source, target,
        _first(mapping, CONTEXT_KEYS) or None,
        _first(mapping, SPEAKER_KEYS) or None,
        _first(mapping, SCENE_KEYS) or None,
        _first(mapping, MISSION_KEYS) or None,
    )


def parse_csv(path: Path) -> Iterator[TranslationRow]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        sample = stream.read(8192)
        stream.seek(0)
        try:
            dialect = csv.Sniffer().sniff(sample, delimiters=",;\t|")
        except csv.Error:
            dialect = csv.excel
        reader = csv.DictReader(stream, dialect=dialect)
        if not reader.fieldnames:
            raise ImportValidationError("CSV must contain a header row.")
        for item in reader:
            row = _row(item)
            if row:
                yield row


def parse_json(path: Path) -> Iterator[TranslationRow]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ImportValidationError(f"Invalid JSON: {exc}") from exc
    if isinstance(payload, dict):
        payload = payload.get("translations", payload.get("entries", payload.get("data", [])))
    if not isinstance(payload, list):
        raise ImportValidationError("JSON root must be an array or contain translations/entries/data array.")
    for item in payload:
        if isinstance(item, dict):
            row = _row(item)
            if row:
                yield row


_PO_TOKEN = re.compile(r'^(msgctxt|msgid_plural|msgid|msgstr(?:\[\d+\])?)\s+("(?:\\.|[^"\\])*")\s*$')
_PO_QUOTED = re.compile(r'^("(?:\\.|[^"\\])*")\s*$')


def _po_unquote(token: str) -> str:
    try:
        # PO string literals use C-style escapes. Decode only the quoted literal.
        return json.loads(token)
    except json.JSONDecodeError as exc:
        raise ImportValidationError(f"Invalid PO string literal: {token}") from exc


def parse_po(path: Path) -> Iterator[TranslationRow]:
    current: dict[str, str] = {}
    active: str | None = None

    def emit() -> TranslationRow | None:
        source = current.get("msgid", "")
        target = current.get("msgstr", "")
        if not source or not target:
            return None
        return TranslationRow(source, target, current.get("msgctxt") or None)

    with path.open("r", encoding="utf-8-sig") as stream:
        for raw in stream:
            line = raw.strip()
            if not line:
                result = emit()
                if result:
                    yield result
                current, active = {}, None
                continue
            if line.startswith("#"):
                continue
            match = _PO_TOKEN.match(line)
            if match:
                key, literal = match.groups()
                if key.startswith("msgstr"):
                    # Singular importer intentionally skips plural forms rather than
                    # silently selecting an arbitrary plural branch.
                    if key != "msgstr":
                        active = None
                        continue
                    key = "msgstr"
                current[key] = _po_unquote(literal)
                active = key
                continue
            continuation = _PO_QUOTED.match(line)
            if continuation and active:
                current[active] = current.get(active, "") + _po_unquote(continuation.group(1))
                continue
            raise ImportValidationError(f"Unsupported PO syntax: {line[:120]}")
    result = emit()
    if result:
        yield result


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].lower()


def _element_text(element: ET.Element | None) -> str:
    if element is None:
        return ""
    return "".join(element.itertext()).strip()


def parse_xliff(path: Path) -> Iterator[TranslationRow]:
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        raise ImportValidationError(f"Invalid XLIFF XML: {exc}") from exc
    for unit in root.iter():
        name = _local_name(unit.tag)
        if name not in {"trans-unit", "unit"}:
            continue
        source_el = target_el = None
        for child in unit.iter():
            child_name = _local_name(child.tag)
            if child_name == "source" and source_el is None:
                source_el = child
            elif child_name == "target" and target_el is None:
                target_el = child
        source, target = _element_text(source_el), _element_text(target_el)
        if source and target:
            context = unit.attrib.get("resname") or unit.attrib.get("id")
            yield TranslationRow(source, target, context=context)


def parse_tmx(path: Path) -> Iterator[TranslationRow]:
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        raise ImportValidationError(f"Invalid TMX XML: {exc}") from exc
    for tu in (node for node in root.iter() if _local_name(node.tag) == "tu"):
        variants: dict[str, str] = {}
        for tuv in (node for node in tu if _local_name(node.tag) == "tuv"):
            lang = (tuv.attrib.get("{http://www.w3.org/XML/1998/namespace}lang")
                    or tuv.attrib.get("lang") or "").lower().replace("_", "-")
            seg = next((child for child in tuv if _local_name(child.tag) == "seg"), None)
            text = _element_text(seg)
            if lang and text:
                variants[lang] = text
        source = next((v for k, v in variants.items() if k.startswith(("en", "eng"))), "")
        target = next((v for k, v in variants.items() if k.startswith(("vi", "vie"))), "")
        if source and target:
            yield TranslationRow(source, target)


PARSERS = {
    "csv": parse_csv, "json": parse_json, "po": parse_po,
    "xliff": parse_xliff, "xlf": parse_xliff, "tmx": parse_tmx,
}


class TranslationImporter:
    def __init__(self, knowledge_base: KnowledgeBase):
        self.kb = knowledge_base

    def import_file(
        self,
        game_id: str,
        file_path: str | Path,
        *,
        source_name: str,
        license_id: str,
        source_url: str | None = None,
        author_name: str | None = None,
        license_url: str | None = None,
        attribution: str | None = None,
        confirm_rights: bool = False,
    ) -> ImportReport:
        path = Path(file_path)
        if not path.is_file():
            raise ImportValidationError(f"Input file does not exist: {path}")
        extension = path.suffix.lower().lstrip(".")
        if extension not in SUPPORTED_FORMATS:
            raise ImportValidationError(f"Unsupported format: .{extension or '(none)'}")
        if not source_name.strip():
            raise ImportValidationError("Source name is required.")
        if license_id.strip().lower() in BLOCKED_LICENSE_IDS:
            raise ImportValidationError("A specific reuse license or permission identifier is required; unknown rights are not imported.")
        if not confirm_rights:
            raise ImportValidationError("Confirm that you have the right to import and reuse this content.")
        with self.kb.connect() as db:
            game = db.execute("SELECT game_id FROM games WHERE game_id=?", (game_id,)).fetchone()
        if game is None:
            raise ImportValidationError(f"Unknown game_id: {game_id}")

        file_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        source_id = self.kb.add_source(
            source_name=source_name, license_id=license_id.strip(),
            source_url=source_url, author_name=author_name,
            license_url=license_url, attribution=attribution,
            verification_status="PENDING",
        )
        with self.kb.transaction() as db:
            cursor = db.execute(
                """INSERT INTO import_jobs(source_id, game_id, file_name, file_format,
                   file_sha256, status, started_at)
                   VALUES (?, ?, ?, ?, ?, 'RUNNING', strftime('%Y-%m-%dT%H:%M:%fZ','now'))""",
                (source_id, game_id, path.name, extension, file_hash),
            )
            import_id = int(cursor.lastrowid)

        imported = skipped = rejected = 0
        errors: list[str] = []
        try:
            parser = PARSERS[extension]
            for line_no, row in enumerate(parser(path), start=1):
                try:
                    normalized = normalize_text(row.source_text)
                    digest = sha256_text(normalized)
                    with self.kb.transaction() as db:
                        duplicate = db.execute(
                            """SELECT 1 FROM translations
                               WHERE game_id=? AND text_sha256=? AND translated_text=?
                                 AND translation_status != 'REJECTED' LIMIT 1""",
                            (game_id, digest, row.translated_text),
                        ).fetchone()
                        if duplicate:
                            skipped += 1
                            continue
                        db.execute(
                            """INSERT INTO translations(
                                game_id, source_text, normalized_text, translated_text,
                                text_sha256, context, speaker, scene_id, mission_id,
                                source_id, translation_source, translation_status
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'IMPORT', 'COMMUNITY')""",
                            (game_id, row.source_text, normalized, row.translated_text,
                             digest, row.context, row.speaker, row.scene_id,
                             row.mission_id, source_id),
                        )
                    imported += 1
                except (sqlite3.Error, ValueError) as exc:
                    rejected += 1
                    errors.append(f"row {line_no}: {exc}")
            status = "COMPLETED" if not errors else ("COMPLETED" if imported or skipped else "FAILED")
        except Exception as exc:
            status = "FAILED"
            errors.append(str(exc))
        with self.kb.transaction() as db:
            db.execute(
                """UPDATE import_jobs SET imported_count=?, skipped_count=?,
                   rejected_count=?, status=?, error_message=?,
                   completed_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')
                   WHERE import_id=?""",
                (imported, skipped, rejected, status, "\n".join(errors[:100]) or None, import_id),
            )
        return ImportReport(import_id, path.name, extension, imported, skipped,
                            rejected, status, errors)
