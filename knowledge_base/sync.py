"""Signed, portable per-game translation packs for offline synchronization.

Packs use HMAC-SHA256. Share the signing secret only with trusted devices.
This is a full per-game snapshot, not a network sync service or delta protocol.
"""
from __future__ import annotations

import hashlib
import hmac
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .db import KnowledgeBase, normalize_text


PACK_VERSION = 1
TABLES = ("data_sources", "translations", "glossary", "characters", "ocr_dictionary")


class PackIntegrityError(ValueError):
    pass


def _canonical(payload: dict[str, Any]) -> bytes:
    return json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
    ).encode("utf-8")


def export_game_pack(
    knowledge_base: KnowledgeBase,
    game_id: str,
    signing_key: bytes,
    output_path: str | Path,
) -> Path:
    if len(signing_key) < 32:
        raise ValueError("signing_key must contain at least 32 bytes")
    with knowledge_base.connect() as db:
        game = db.execute("SELECT * FROM games WHERE game_id=?", (game_id,)).fetchone()
        if game is None:
            raise ValueError("game does not exist")
        payload: dict[str, Any] = {
            "format": "dichgame-localization-pack",
            "version": PACK_VERSION,
            "exported_at": datetime.now(timezone.utc).isoformat(),
            "game": dict(game),
            "tables": {},
        }
        for table in TABLES:
            if table == "data_sources":
                rows = db.execute(
                    """SELECT DISTINCT s.* FROM data_sources s
                       LEFT JOIN translations t ON t.source_id=s.source_id
                       LEFT JOIN glossary g ON g.source_id=s.source_id
                       WHERE t.game_id=? OR g.game_id=?
                       ORDER BY s.source_id""",
                    (game_id, game_id),
                ).fetchall()
            else:
                rows = db.execute(
                    f"SELECT * FROM {table} WHERE game_id=? ORDER BY 1", (game_id,),
                ).fetchall()
            payload["tables"][table] = [dict(row) for row in rows]
    signature = hmac.new(signing_key, _canonical(payload), hashlib.sha256).hexdigest()
    document = {"payload": payload, "signature": signature}
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return path


def import_game_pack(
    knowledge_base: KnowledgeBase,
    pack_path: str | Path,
    signing_key: bytes,
    *,
    allow_verified: bool = False,
) -> dict[str, int]:
    if len(signing_key) < 32:
        raise ValueError("signing_key must contain at least 32 bytes")
    try:
        document = json.loads(Path(pack_path).read_text(encoding="utf-8"))
        payload = document["payload"]
        signature = document["signature"]
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise PackIntegrityError("Invalid or unreadable localization pack") from exc
    expected = hmac.new(signing_key, _canonical(payload), hashlib.sha256).hexdigest()
    if not isinstance(signature, str) or not hmac.compare_digest(signature, expected):
        raise PackIntegrityError("Pack signature is invalid")
    if payload.get("format") != "dichgame-localization-pack" or payload.get("version") != PACK_VERSION:
        raise PackIntegrityError("Unsupported localization pack format/version")
    game = payload.get("game")
    tables = payload.get("tables")
    if not isinstance(game, dict) or not isinstance(tables, dict):
        raise PackIntegrityError("Pack is missing game or table data")
    game_id = game.get("game_id")
    if not isinstance(game_id, str) or not game_id.strip():
        raise PackIntegrityError("Pack has invalid game id")
    for table in TABLES:
        if not isinstance(tables.get(table), list):
            raise PackIntegrityError(f"Pack table {table} is missing or invalid")

    knowledge_base.add_game(
        game_id, str(game["game_name"]), str(game["platform"]),
        source_language=str(game.get("source_language") or "en"),
        target_language=str(game.get("target_language") or "vi"),
        alternative_names=json.loads(game.get("alternative_names_json") or "[]"),
        developer=game.get("developer"), publisher=game.get("publisher"),
        region=game.get("region"), game_version=game.get("game_version"),
    )
    counts = {table: 0 for table in TABLES}
    source_map: dict[int, int] = {}
    for source in tables["data_sources"]:
        old_id = source.get("source_id")
        new_id = knowledge_base.add_source(
            source_name=str(source["source_name"]),
            license_id=str(source["license_id"]),
            source_url=source.get("source_url"), author_name=source.get("author_name"),
            license_url=source.get("license_url"), platform=source.get("platform"),
            game_version=source.get("game_version"),
            verification_status="PENDING",
            attribution=source.get("attribution"),
        )
        if old_id is not None:
            source_map[int(old_id)] = new_id
        counts["data_sources"] += 1

    with knowledge_base.transaction() as db:
        for item in tables["translations"]:
            source_text = item.get("source_text")
            translated_text = item.get("translated_text")
            if not isinstance(source_text, str) or not normalize_text(source_text):
                continue
            if not isinstance(translated_text, str) or not translated_text.strip():
                continue
            exists = db.execute(
                """SELECT 1 FROM translations
                   WHERE game_id=? AND normalized_text=? AND translated_text=?""",
                (game_id, normalize_text(source_text), translated_text),
            ).fetchone()
            if exists:
                continue
            status = item.get("translation_status", "COMMUNITY")
            if status == "VERIFIED" and not allow_verified:
                status = "COMMUNITY"
            if status not in {"VERIFIED", "COMMUNITY", "AI_REVIEWED", "AI_UNVERIFIED", "REJECTED"}:
                status = "COMMUNITY"
            db.execute(
                """INSERT INTO translations(
                    game_id, source_text, normalized_text, translated_text, text_sha256,
                    context, speaker, scene_id, mission_id, previous_sentence,
                    next_sentence, source_id, translation_source, translation_status,
                    confidence_score, verified_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    game_id, source_text, normalize_text(source_text), translated_text,
                    hashlib.sha256(normalize_text(source_text).encode("utf-8")).hexdigest(),
                    item.get("context"), item.get("speaker"), item.get("scene_id"),
                    item.get("mission_id"), item.get("previous_sentence"),
                    item.get("next_sentence"), source_map.get(item.get("source_id")),
                    "SYNC", status,
                    item.get("confidence_score"),
                    item.get("verified_at") if status == "VERIFIED" and allow_verified else None,
                ),
            )
            counts["translations"] += 1

        for table in ("glossary", "characters", "ocr_dictionary"):
            for item in tables[table]:
                columns = {
                    "glossary": ("original_term", "normalized_term", "translated_term", "category", "description", "context", "priority", "source_id"),
                    "characters": ("character_key", "original_name", "localized_name", "gender_reference", "preferred_pronouns", "relationship", "speaking_style", "notes"),
                    "ocr_dictionary": ("correct_word", "incorrect_variants_json", "correction_rule", "confidence_score"),
                }[table]
                unique_col = {
                    "glossary": "normalized_term",
                    "characters": "character_key",
                    "ocr_dictionary": "correct_word",
                }[table]
                values = [item.get(column) for column in columns]
                if table == "glossary" and values[-1] is not None:
                    values[-1] = source_map.get(values[-1])
                existing = db.execute(
                    f"SELECT 1 FROM {table} WHERE game_id=? AND {unique_col}=?",
                    (game_id, item.get(unique_col)),
                ).fetchone()
                if existing:
                    continue
                column_sql = ", ".join(["game_id", *columns])
                placeholders = ", ".join("?" for _ in range(len(columns) + 1))
                db.execute(
                    f"INSERT INTO {table}({column_sql}) VALUES ({placeholders})",
                    [game_id, *values],
                )
                counts[table] += 1
    return counts

def export_android_csv(
    knowledge_base: KnowledgeBase,
    game_id: str,
    output_path: str | Path,
) -> Path:
    """Export only human-verified translations in the app's scoped CSV format.

    The Android app can import this file through its existing Import Pack flow.
    Community and AI-unverified rows are intentionally excluded.
    """
    import csv

    with knowledge_base.connect() as db:
        game = db.execute(
            "SELECT game_name FROM games WHERE game_id=?", (game_id,),
        ).fetchone()
        if game is None:
            raise ValueError("game does not exist")
        rows = db.execute(
            """SELECT t.source_text, t.translated_text
               FROM translations t
               LEFT JOIN data_sources s ON s.source_id=t.source_id
               WHERE t.game_id=? AND t.translation_status='VERIFIED'
                 AND (t.source_id IS NULL OR s.verification_status='VERIFIED')
               ORDER BY t.translation_id""",
            (game_id,),
        ).fetchall()
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\\r\\n")
        writer.writerow(["source", "vi", "game"])
        for row in rows:
            source = str(row["source_text"]).replace("\\r", " ").replace("\\n", " ").replace("\\t", " ")
            translated = str(row["translated_text"]).replace("\\r", " ").replace("\\n", " ").replace("\\t", " ")
            writer.writerow([source, translated, str(game["game_name"])])
    return path
