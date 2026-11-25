"""Provider-neutral AI fallback with persistent daily request budgets.

No provider SDK or network dependency is required. Applications inject an
adapter that implements the small AIProvider protocol below.
"""
from __future__ import annotations

import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Protocol

from .db import KnowledgeBase
from .translation_memory import TranslationMemory, TranslationResult


@dataclass(frozen=True)
class ProviderResponse:
    translated_text: str
    input_tokens: int = 0
    output_tokens: int = 0


class AIProvider(Protocol):
    def translate(
        self,
        source_text: str,
        context: str,
        *,
        model: str,
        max_output_tokens: int,
    ) -> ProviderResponse:
        """Call a configured provider with a hard output-token limit."""
        ...


class AIFallbackError(RuntimeError):
    """Base class for controlled AI fallback failures."""


class AIUnavailableError(AIFallbackError):
    """Raised when offline mode prevents a cache-miss provider request."""


class AIBudgetExceededError(AIFallbackError):
    """Raised when the persistent daily request budget is exhausted."""


class AIFallback:
    def __init__(
        self,
        knowledge_base: KnowledgeBase,
        memory: TranslationMemory,
        provider: AIProvider,
        *,
        model: str,
        daily_request_limit: int = 100,
        max_retries: int = 2,
        retry_backoff_seconds: float = 0.25,
        max_input_chars: int = 8000,
        max_output_tokens: int = 512,
        offline_mode: bool = False,
    ) -> None:
        if not model.strip():
            raise ValueError("model must not be empty")
        if daily_request_limit < 1 or max_retries < 0:
            raise ValueError("daily_request_limit must be positive and max_retries non-negative")
        if retry_backoff_seconds < 0 or max_input_chars < 1 or max_output_tokens < 1:
            raise ValueError("Backoff must be non-negative and limits must be positive")
        self.kb = knowledge_base
        self.memory = memory
        self.provider = provider
        self.model = model
        self.daily_request_limit = daily_request_limit
        self.max_retries = max_retries
        self.retry_backoff_seconds = retry_backoff_seconds
        self.max_input_chars = max_input_chars
        self.max_output_tokens = max_output_tokens
        self.offline_mode = offline_mode

    def translate(
        self, game_id: str, source_text: str, *, context: str = "",
    ) -> TranslationResult:
        if len(source_text) > self.max_input_chars:
            raise ValueError("source_text exceeds max_input_chars")

        def call_provider(text: str, context_text: str) -> str:
            if self.offline_mode:
                raise AIUnavailableError("Offline mode: no cached translation is available")
            last_error: Exception | None = None
            for attempt in range(self.max_retries + 1):
                usage_date = self._reserve_request()
                try:
                    response = self.provider.translate(
                        text, context_text, model=self.model,
                        max_output_tokens=self.max_output_tokens,
                    )
                    if not isinstance(response, ProviderResponse):
                        raise TypeError("Provider must return ProviderResponse")
                    if not response.translated_text.strip():
                        raise ValueError("Provider returned empty translation")
                    if response.input_tokens < 0 or response.output_tokens < 0:
                        raise ValueError("Provider token counts cannot be negative")
                    self._record_tokens(response.input_tokens, response.output_tokens, usage_date)
                    return response.translated_text.strip()
                except AIBudgetExceededError:
                    raise
                except Exception as exc:
                    last_error = exc
                    if attempt >= self.max_retries:
                        raise
                    if self.retry_backoff_seconds:
                        time.sleep(self.retry_backoff_seconds * (2 ** attempt))
            assert last_error is not None
            raise last_error

        return self.memory.get_or_translate(
            game_id, source_text, call_provider, model=self.model, context=context,
        )

    def _reserve_request(self) -> str:
        usage_date = datetime.now(timezone.utc).date().isoformat()
        with self.kb.transaction() as db:
            cursor = db.execute(
                """INSERT INTO ai_usage_daily(usage_date, request_count)
                   VALUES (?, 1)
                   ON CONFLICT(usage_date) DO UPDATE SET
                       request_count=ai_usage_daily.request_count + 1
                   WHERE ai_usage_daily.request_count < ?""",
                (usage_date, self.daily_request_limit),
            )
            if cursor.rowcount != 1:
                raise AIBudgetExceededError("Daily AI request budget has been reached")
        return usage_date

    def _record_tokens(self, input_tokens: int, output_tokens: int, usage_date: str) -> None:
        with self.kb.transaction() as db:
            db.execute(
                """UPDATE ai_usage_daily
                   SET input_tokens=input_tokens + ?, output_tokens=output_tokens + ?
                   WHERE usage_date=?""",
                (input_tokens, output_tokens, usage_date),
            )
