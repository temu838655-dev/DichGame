"""Persistent translation memory and duplicate-request suppression.

The provider is injected by the application; this module makes no network calls.
"""
from __future__ import annotations

import time
import uuid
from dataclasses import dataclass
from typing import Callable

from .db import KnowledgeBase, memory_key, normalize_text


@dataclass(frozen=True)
class TranslationResult:
    translated_text: str
    cache_hit: bool
    model: str


Provider = Callable[[str, str], str]


class TranslationMemory:
    """Cache-first translation orchestration with cross-process request leases.

    A provider receives (source_text, context) and returns translated text.
    It should enforce its own network timeout. lease_seconds should be longer
    than that provider timeout to avoid a second worker taking the lease while
    the first provider call is still running.
    """

    def __init__(
        self,
        knowledge_base: KnowledgeBase,
        *,
        lease_seconds: int = 180,
        wait_timeout_seconds: float = 240,
        poll_interval_seconds: float = 0.15,
        max_cache_age_seconds: int | None = None,
    ) -> None:
        if lease_seconds < 1 or wait_timeout_seconds <= 0 or poll_interval_seconds <= 0:
            raise ValueError("Lease, wait timeout and poll interval must be positive")
        self.kb = knowledge_base
        self.lease_seconds = lease_seconds
        self.wait_timeout_seconds = wait_timeout_seconds
        self.poll_interval_seconds = poll_interval_seconds
        self.max_cache_age_seconds = max_cache_age_seconds

    def get_or_translate(
        self,
        game_id: str,
        source_text: str,
        provider: Provider,
        *,
        model: str,
        context: str = "",
    ) -> TranslationResult:
        if not normalize_text(source_text):
            raise ValueError("source_text must not be empty")
        if not model.strip():
            raise ValueError("model must not be empty")
        cached = self.kb.cache_get_with_model(
            game_id, source_text, context,
            max_age_seconds=self.max_cache_age_seconds,
        )
        if cached is not None:
            return TranslationResult(cached[0], True, cached[1])

        text_hash, context_hash = memory_key(source_text, context)
        owner = uuid.uuid4().hex
        deadline = time.monotonic() + self.wait_timeout_seconds

        while True:
            if self._claim(game_id, text_hash, context_hash, owner):
                break
            cached = self.kb.cache_get_with_model(
                game_id, source_text, context,
                max_age_seconds=self.max_cache_age_seconds,
            )
            if cached is not None:
                return TranslationResult(cached[0], True, cached[1])
            if time.monotonic() >= deadline:
                raise TimeoutError("Timed out waiting for an in-flight translation")
            time.sleep(self.poll_interval_seconds)

        try:
            cached = self.kb.cache_get_with_model(
                game_id, source_text, context,
                max_age_seconds=self.max_cache_age_seconds,
            )
            if cached is not None:
                return TranslationResult(cached[0], True, cached[1])

            translated = provider(source_text, context)
            if not isinstance(translated, str) or not translated.strip():
                raise ValueError("Translation provider returned empty or non-text output")
            translated = translated.strip()
            self.kb.cache_put(game_id, source_text, translated, model, context)
            return TranslationResult(translated, False, model)
        finally:
            self._release(game_id, text_hash, context_hash, owner)

    def _claim(self, game_id: str, text_hash: str, context_hash: str, owner: str) -> bool:
        with self.kb.transaction() as db:
            cursor = db.execute(
                """INSERT INTO translation_request_locks(
                       game_id, text_hash, context_hash, owner_token, lease_until
                   ) VALUES (?, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ','now', ?))
                   ON CONFLICT(game_id, text_hash, context_hash) DO UPDATE SET
                       owner_token=excluded.owner_token,
                       lease_until=excluded.lease_until,
                       created_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')
                   WHERE translation_request_locks.lease_until
                       <= strftime('%Y-%m-%dT%H:%M:%fZ','now')""",
                (game_id, text_hash, context_hash, owner, f"+{self.lease_seconds} seconds"),
            )
            return cursor.rowcount == 1

    def _release(self, game_id: str, text_hash: str, context_hash: str, owner: str) -> None:
        with self.kb.transaction() as db:
            db.execute(
                """DELETE FROM translation_request_locks
                   WHERE game_id=? AND text_hash=? AND context_hash=? AND owner_token=?""",
                (game_id, text_hash, context_hash, owner),
            )
