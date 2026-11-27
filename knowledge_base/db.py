"""SQLite storage layer for the multi-game localization knowledge base.

Uses only Python's standard library. FTS5 is required; no network or AI calls
are made by this module.
"""
from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import unicodedata
from contextlib import closing, contextmanager
from pathlib import Path
from typing import Any, Iterator

SCHEMA_PATH = Path(__file__).with_name("schema.sql")


def normalize_text(value: str) -> str:
    """Normalize OCR/game text without deleting meaningful words or numbers."""
    value = unicodedata.normalize("NFKC", value or "")
    value = value.replace("\u00a0", " ")
    value = re.sub(r"\s+", " ", value).strip()
    value = value.casefold()
    # Normalize spacing around punctuation, but preserve punctuation itself.
    value = re.sub(r"\s+([,.;:!?])", r"\1", value)
    return value


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


# Compare case-sensitive runtime arguments before case folding or fuzzy scoring.
_ARGUMENT = re.compile(r"\{[^{}\n]+\}|%(?:\d+\$)?[-+#0 ]*\d*(?:\.\d+)?[diuoxXfFeEgGcs]|<[^<>\n]+>|\d+")


def runtime_arguments(text: str) -> list[str]:
    return _ARGUMENT.findall(text)


def preserves_arguments(source: str, translation: str) -> bool:
    expected = runtime_arguments(source)
    actual = runtime_arguments(translation)
    if expected == actual:
        return True
    if sorted(expected) != sorted(actual):
        return False
    # Named/indexed arguments can move. Markup and unnumbered printf arguments
    # must retain their relative order within their respective groups.
    for prefix in ("<", "%"):
        def ordered(tokens):
            return [t for t in tokens if t.startswith(prefix) and
                    (prefix != "%" or "$" not in t)]
        if ordered(expected) != ordered(actual):
            return False
    return True


def _memory_hash(text: str) -> str:
    normalized = normalize_text(text)
    args = runtime_arguments(text)
    if not args:
        return sha256_text(normalized)
    payload = json.dumps([normalized, args], ensure_ascii=False, separators=(",", ":"))
    return sha256_text("runtime-arguments-v1\0" + payload)


def memory_key(text: str, context: str) -> tuple[str, str]:
    return _memory_hash(text), _memory_hash(context) if context else ""


class KnowledgeBase:
    def __init__(self, database_path: str | Path):
        self.database_path = str(database_path)
        self._initialize()

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.database_path, timeout=15)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA busy_timeout = 15000")
        return connection

    def _initialize(self) -> None:
        schema = SCHEMA_PATH.read_text(encoding="utf-8")
        with closing(self.connect()) as connection:
            connection.execute("PRAGMA journal_mode = WAL")
            connection.executescript(schema)
            # Additive upgrade for databases created before contributor attribution.
            columns = {row[1] for row in connection.execute("PRAGMA table_info(translations)")}
            if "contributor_id" not in columns:
                connection.execute("ALTER TABLE translations ADD COLUMN contributor_id TEXT")
            if "external_id" not in columns:
                connection.execute("ALTER TABLE translations ADD COLUMN external_id TEXT")
            connection.execute(
                "CREATE INDEX IF NOT EXISTS idx_translations_external_id "
                "ON translations(game_id, external_id) WHERE external_id IS NOT NULL"
            )
            connection.execute(
                "INSERT OR IGNORE INTO schema_migrations(version, description) VALUES (?, ?)",
                (6, "Preserve source localization string identifiers"),
            )
            connection.commit()
            # Fail early and clearly if SQLite was built without FTS5.
            connection.execute("SELECT count(*) FROM translations_fts").fetchone()

    @contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        connection = self.connect()
        try:
            connection.execute("BEGIN IMMEDIATE")
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def add_game(
        self,
        game_id: str,
        game_name: str,
        platform: str,
        source_language: str = "en",
        target_language: str = "vi",
        alternative_names: list[str] | None = None,
        developer: str | None = None,
        publisher: str | None = None,
        region: str | None = None,
        game_version: str | None = None,
    ) -> None:
        with self.transaction() as db:
            db.execute(
                """INSERT INTO games(
                    game_id, game_name, alternative_names_json, developer, publisher,
                    platform, region, game_version, source_language, target_language
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(game_id) DO UPDATE SET
                    game_name=excluded.game_name,
                    alternative_names_json=excluded.alternative_names_json,
                    developer=excluded.developer,
                    publisher=excluded.publisher,
                    platform=excluded.platform,
                    region=excluded.region,
                    game_version=excluded.game_version,
                    source_language=excluded.source_language,
                    target_language=excluded.target_language,
                    updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')""",
                (
                    game_id, game_name, json.dumps(alternative_names or [], ensure_ascii=False),
                    developer, publisher, platform, region, game_version,
                    source_language, target_language,
                ),
            )

    def add_source(
        self,
        source_name: str,
        license_id: str,
        source_url: str | None = None,
        author_name: str | None = None,
        license_url: str | None = None,
        platform: str | None = None,
        game_version: str | None = None,
        verification_status: str = "PENDING",
        attribution: str | None = None,
    ) -> int:
        with self.transaction() as db:
            cursor = db.execute(
                """INSERT INTO data_sources(
                    source_name, source_url, author_name, license_id, license_url,
                    platform, game_version, verification_status, attribution
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    source_name, source_url, author_name, license_id, license_url,
                    platform, game_version, verification_status, attribution,
                ),
            )
            return int(cursor.lastrowid)

    def add_translation(
        self,
        game_id: str,
        source_text: str,
        translated_text: str,
        *,
        context: str | None = None,
        speaker: str | None = None,
        scene_id: str | None = None,
        mission_id: str | None = None,
        previous_sentence: str | None = None,
        next_sentence: str | None = None,
        source_id: int | None = None,
        contributor_id: str | None = None,
        translation_source: str = "COMMUNITY",
        translation_status: str = "COMMUNITY",
        confidence_score: float | None = None,
        external_id: str | None = None,
    ) -> int:
        normalized = normalize_text(source_text)
        with self.transaction() as db:
            cursor = db.execute(
                """INSERT INTO translations(
                    game_id, source_text, normalized_text, translated_text, text_sha256,
                    context, speaker, scene_id, mission_id, previous_sentence,
                    next_sentence, source_id, contributor_id, translation_source, translation_status,
                    confidence_score, external_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    game_id, source_text, normalized, translated_text,
                    sha256_text(normalized), context, speaker, scene_id, mission_id,
                    previous_sentence, next_sentence, source_id, contributor_id, translation_source,
                    translation_status, confidence_score, external_id,
                ),
            )
            return int(cursor.lastrowid)

    def pending_source_strings(self, game_id: str, limit: int = 100) -> list[dict[str, Any]]:
        if limit < 1:
            raise ValueError('limit must be positive')
        with closing(self.connect()) as db:
            rows = db.execute(
                "SELECT * FROM source_strings WHERE game_id=? AND status='PENDING' ORDER BY source_string_id LIMIT ?",
                (game_id, min(limit, 1000)),
            ).fetchall()
            return [dict(row) for row in rows]

    def set_source_string_translation(self, game_id: str, external_id: str, translated_text: str, status: str = 'TRANSLATED') -> None:
        if status not in {'TRANSLATED', 'VERIFIED', 'REJECTED'}:
            raise ValueError('invalid source string status')
        with self.transaction() as db:
            db.execute(
                "UPDATE source_strings SET translated_text=?, status=?, updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE game_id=? AND external_id=?",
                (translated_text, status, game_id, external_id),
            )
    def lookup_external_id(self, game_id: str, external_id: str) -> dict[str, Any] | None:
        """Resolve a game string by its original engine/localization ID."""
        key = (external_id or "").strip()
        if not key:
            return None
        with closing(self.connect()) as db:
            row = db.execute(
                """SELECT * FROM translations
                   WHERE game_id=? AND external_id=? AND translation_status != 'REJECTED'
                   ORDER BY CASE translation_status
                       WHEN 'VERIFIED' THEN 0 WHEN 'AI_REVIEWED' THEN 1
                       WHEN 'COMMUNITY' THEN 2 ELSE 3 END, confidence_score DESC LIMIT 1""",
                (game_id, key),
            ).fetchone()
            return dict(row) if row else None
    def lookup_exact(self, game_id: str, source_text: str) -> dict[str, Any] | None:
        normalized = normalize_text(source_text)
        with closing(self.connect()) as db:
            row = db.execute(
                """SELECT * FROM translations
                   WHERE game_id = ? AND normalized_text = ?
                     AND translation_status != 'REJECTED'
                   ORDER BY CASE translation_status
                       WHEN 'VERIFIED' THEN 0
                       WHEN 'AI_REVIEWED' THEN 1
                       WHEN 'COMMUNITY' THEN 2
                       ELSE 3 END,
                       confidence_score DESC
                   LIMIT 1""",
                (game_id, normalized),
            ).fetchone()
            return dict(row) if row else None

    def search_fts(self, game_id: str, query: str, limit: int = 10) -> list[dict[str, Any]]:
        """Search indexed text. This is discovery, not a fuzzy-match confidence decision."""
        terms = [term for term in re.findall(r"[\w]+", query, flags=re.UNICODE) if term]
        if not terms:
            return []
        match_query = " AND ".join('"' + term.replace('"', '""') + '"' for term in terms)
        with self.connect() as db:
            rows = db.execute(
                """SELECT t.* FROM translations_fts f
                   JOIN translations t ON t.translation_id = f.rowid
                   WHERE translations_fts MATCH ? AND t.game_id = ?
                     AND t.translation_status != 'REJECTED'
                   ORDER BY bm25(translations_fts)
                   LIMIT ?""",
                (match_query, game_id, max(1, min(limit, 100))),
            ).fetchall()
            return [dict(row) for row in rows]

    def cache_get(
        self, game_id: str, text: str, context: str = "",
        max_age_seconds: int | None = None,
    ) -> str | None:
        entry = self.cache_get_with_model(game_id, text, context, max_age_seconds)
        return entry[0] if entry else None

    def cache_get_with_model(
        self, game_id: str, text: str, context: str = "",
        max_age_seconds: int | None = None,
    ) -> tuple[str, str] | None:
        text_hash, context_hash = memory_key(text, context)
        with self.transaction() as db:
            cutoff = None
            if max_age_seconds is not None:
                from datetime import datetime, timedelta, timezone
                cutoff = (datetime.now(timezone.utc) - timedelta(seconds=max(0, max_age_seconds))).isoformat(timespec="milliseconds").replace("+00:00", "Z")
            query = """SELECT source_text, translated_text, model FROM translation_cache
                       WHERE game_id=? AND text_hash=? AND context_hash=?
                         AND (? IS NULL OR created_at >= ?)"""
            row = db.execute(query, (game_id, text_hash, context_hash, cutoff, cutoff)).fetchone()
            if row is None and runtime_arguments(text) and not runtime_arguments(context):
                # Old keys folded case. The stored source can prove a safe hit.
                # Legacy rows lack raw context, so never infer its arguments.
                text_hash = sha256_text(normalize_text(text))
                row = db.execute(query, (game_id, text_hash, context_hash, cutoff, cutoff)).fetchone()
            if row is None or runtime_arguments(row["source_text"]) != runtime_arguments(text):
                return None
            if not preserves_arguments(text, row["translated_text"]):
                return None
            db.execute(
                """UPDATE translation_cache
                   SET last_used=strftime('%Y-%m-%dT%H:%M:%fZ','now'),
                       use_count=use_count+1
                   WHERE game_id=? AND text_hash=? AND context_hash=?""",
                (game_id, text_hash, context_hash),
            )
            return str(row["translated_text"]), str(row["model"])

    def cache_put(
        self, game_id: str, text: str, translated_text: str,
        model: str, context: str = "",
    ) -> None:
        if not preserves_arguments(text, translated_text):
            raise ValueError("Bản dịch làm thay đổi biến, thẻ định dạng hoặc số liệu")
        text_hash, context_hash = memory_key(text, context)
        with self.transaction() as db:
            db.execute(
                """INSERT INTO translation_cache(
                    game_id, text_hash, context_hash, source_text, translated_text, model
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(game_id, text_hash, context_hash) DO UPDATE SET
                    translated_text=excluded.translated_text,
                    model=excluded.model,
                    created_at=excluded.created_at,
                    last_used=strftime('%Y-%m-%dT%H:%M:%fZ','now')""",
                (game_id, text_hash, context_hash, text, translated_text, model),
            )
