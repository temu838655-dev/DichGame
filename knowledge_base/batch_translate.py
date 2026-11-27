"""Resumable, ID-preserving batch translation for staged game strings."""
from __future__ import annotations

import time
from dataclasses import dataclass

from .ai_fallback import AIProvider, ProviderHTTPError, retry_delay
from .db import KnowledgeBase


@dataclass(frozen=True)
class BatchReport:
    requested: int
    translated: int
    reused_existing: int
    failed: int
    errors: tuple[str, ...]
    deferred: int = 0


class BatchTranslator:
    def __init__(self, kb: KnowledgeBase, provider: AIProvider, *, model: str,
                 max_output_tokens: int = 512, retries: int = 2, pause_seconds: float = 0.1):
        if not model.strip() or max_output_tokens < 1 or retries < 0 or pause_seconds < 0:
            raise ValueError("Invalid batch translation configuration")
        self.kb = kb
        self.provider = provider
        self.model = model
        self.max_output_tokens = max_output_tokens
        self.retries = retries
        self.pause_seconds = pause_seconds

    def run(self, game_id: str, *, limit: int = 50) -> BatchReport:
        if limit < 1 or limit > 1000:
            raise ValueError("limit must be between 1 and 1000")
        rows = self.kb.pending_source_strings(game_id, limit)
        translated = reused = failed = deferred = 0
        provider_paused = False
        errors: list[str] = []
        for row in rows:
            external_id = row["external_id"]
            try:
                existing = self.kb.lookup_external_id(game_id, external_id)
                if existing:
                    if existing["source_text"] != row["source_text"]:
                        raise ValueError("Existing translation ID has different English source text")
                    self.kb.set_source_string_translation(game_id, external_id, existing["translated_text"])
                    reused += 1
                    continue
                if provider_paused:
                    deferred += 1
                    continue
                response = None
                for attempt in range(self.retries + 1):
                    try:
                        response = self.provider.translate(
                            row["source_text"], row.get("context") or "",
                            model=self.model, max_output_tokens=self.max_output_tokens,
                        )
                        break
                    except Exception as exc:
                        delay = retry_delay(exc, attempt, 0.5)
                        if attempt >= self.retries or delay is None:
                            raise
                        if delay:
                            time.sleep(delay)
                result = getattr(response, "translated_text", "") if response else ""
                result = result.strip() if isinstance(result, str) else ""
                if not result:
                    raise ValueError("Provider returned an empty translation")
                self.kb.add_translation(
                    game_id, row["source_text"], result, context=row.get("context"),
                    source_id=row.get("source_id"), external_id=external_id,
                    translation_source="AI_BATCH", translation_status="AI_UNVERIFIED",
                )
                self.kb.set_source_string_translation(game_id, external_id, result)
                translated += 1
                if self.pause_seconds:
                    time.sleep(self.pause_seconds)
            except Exception as exc:
                if isinstance(exc, ProviderHTTPError) and exc.status in (401, 403, 429):
                    provider_paused = True
                failed += 1
                if len(errors) < 100:
                    errors.append(f"{external_id}: {exc}")
        return BatchReport(len(rows), translated, reused, failed, tuple(errors), deferred)
