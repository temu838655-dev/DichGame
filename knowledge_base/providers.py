"""Concrete stdlib HTTP adapters for Gemini and OpenAI-compatible APIs.

API keys are supplied at runtime and are never persisted by this package.
"""
from __future__ import annotations

import json
import os
import time
from email.utils import parsedate_to_datetime
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

from .ai_fallback import ProviderHTTPError, ProviderResponse
from .db import preserves_arguments


def _retry_after(value: str) -> float:
    if value.isascii() and value.isdecimal():
        return float(value)
    try:
        return max(0.0, parsedate_to_datetime(value).timestamp() - time.time())
    except (ValueError, TypeError, OverflowError):
        return 0.0


def _quota_exhausted(payload: bytes) -> bool:
    try:
        error = json.loads(payload)["error"]
        return isinstance(error, dict) and any(
            error.get(key) in ("insufficient_quota", "billing_hard_limit_reached")
            for key in ("type", "code")
        )
    except (ValueError, TypeError, KeyError):
        return False


def _post_json(url: str, payload: dict, headers: dict[str, str], timeout: float) -> dict:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=body, headers={
        "Content-Type": "application/json",
        "Accept": "application/json",
        **headers,
    }, method="POST")
    try:
        with urlopen(request, timeout=timeout) as response:
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000:
                raise ProviderHTTPError("Provider response exceeded 2 MB")
    except HTTPError as exc:
        with exc:
            quota = exc.code == 429 and _quota_exhausted(exc.read(16_384))
            retry_after = _retry_after((exc.headers or {}).get("Retry-After", "").strip())
        message = "API đã hết hạn mức" if quota else "API trả về lỗi"
        raise ProviderHTTPError(
            f"{message} HTTP {exc.code}",
            retryable=exc.code in (408, 429, 500, 502, 503, 504) and not quota,
            retry_after=retry_after, status=exc.code,
        ) from None
    except (URLError, TimeoutError):
        raise ProviderHTTPError("Mất kết nối với API", retryable=True) from None
    try:
        result = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProviderHTTPError("Provider returned invalid JSON") from exc
    if not isinstance(result, dict):
        raise ProviderHTTPError("Provider returned an unexpected response")
    return result


def _gemini_answer(result: dict) -> str:
    try:
        candidate = result["candidates"][0]
        if candidate.get("finishReason", "STOP") != "STOP":
            raise ProviderHTTPError("Gemini chưa hoàn tất bản dịch")
        parts = candidate["content"]["parts"]
        if not isinstance(parts, list):
            raise TypeError
        text = "".join(part.get("text", "") for part in parts if not part.get("thought")).strip()
    except (AttributeError, IndexError, KeyError, TypeError):
        raise ProviderHTTPError("Cấu trúc phản hồi Gemini không hợp lệ") from None
    if not text:
        raise ProviderHTTPError("Gemini trả về bản dịch trống")
    return text


class GeminiProvider:
    """Google Gemini generateContent REST adapter."""

    def __init__(
        self,
        api_key: str,
        *,
        timeout_seconds: float = 20,
        api_base: str = "https://generativelanguage.googleapis.com/v1beta",
    ) -> None:
        if not api_key.strip() or timeout_seconds <= 0:
            raise ValueError("A non-empty API key and positive timeout are required")
        self.api_key = api_key
        self.timeout_seconds = timeout_seconds
        self.api_base = api_base.rstrip("/")

    @classmethod
    def from_env(cls, variable: str = "GEMINI_API_KEY", **kwargs):
        key = os.environ.get(variable, "")
        if not key:
            raise ValueError(f"Environment variable {variable} is not set")
        return cls(key, **kwargs)

    def translate(
        self, source_text: str, context: str, *, model: str,
        max_output_tokens: int,
    ) -> ProviderResponse:
        if not model.strip() or max_output_tokens < 1 or not source_text.strip():
            raise ValueError("Cần model, nội dung và giới hạn token hợp lệ")
        prompt = (
            "Translate the game dialogue below into natural Vietnamese. "
            "Preserve names, variables, markup, placeholders, and line breaks. "
            "Return only the Vietnamese translation, without explanations.\n"
            f"Context: {context or 'Not provided'}\n"
            f"Text:\n{source_text}"
        )
        url = f"{self.api_base}/models/{quote(model, safe='')}:generateContent"
        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"maxOutputTokens": max_output_tokens, "temperature": 0.2},
        }
        result = _post_json(url, payload, {"x-goog-api-key": self.api_key}, self.timeout_seconds)
        try:
            translated = _gemini_answer(result)
            usage = result.get("usageMetadata", {})
            input_tokens = int(usage.get("promptTokenCount", 0))
            output_tokens = int(usage.get("candidatesTokenCount", 0))
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise ProviderHTTPError("Gemini returned no usable translation") from exc
        if not translated.strip():
            raise ProviderHTTPError("Gemini returned an empty translation")
        return ProviderResponse(translated.strip(), input_tokens, output_tokens)


    def translate_many(
        self, rows: list[dict[str, str]], *, model: str, max_output_tokens: int,
    ) -> dict[str, str]:
        """Translate a small group in one request and require an exact JSON ID set."""
        if not model.strip() or any(not isinstance(row.get("text"), str) or not row["text"].strip() for row in rows) or not rows or len(rows) > 20 or max_output_tokens < 1:
            raise ValueError("translate_many accepts 1-20 rows and a positive output limit")
        source_ids = [str(row.get("id", "")).strip() for row in rows]
        if any(not value for value in source_ids) or len(set(source_ids)) != len(source_ids):
            raise ValueError("Every batch row must have a unique non-empty ID")
        source = [{
            "id": source_ids[index],
            "context": str(row.get("context", "")),
            "text": str(row.get("text", "")),
        } for index, row in enumerate(rows)]
        prompt = (
            "You are a professional Vietnamese game-localization translator. Translate every item into natural Vietnamese. "
            "Preserve character names, tone, profanity level, numbers, placeholders, markup tags, timing tokens and line breaks. "
            "Do not omit, merge, reorder or add items. Return only a valid JSON array of objects with exactly the keys id and translation. "
            "Copy each id exactly.\nINPUT:\n" + json.dumps(source, ensure_ascii=False)
        )
        url = f"{self.api_base}/models/{quote(model, safe='')}:generateContent"
        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"maxOutputTokens": max_output_tokens, "temperature": 0.2, "responseMimeType": "application/json"},
        }
        result = _post_json(url, payload, {"x-goog-api-key": self.api_key}, self.timeout_seconds)
        try:
            raw = _gemini_answer(result)
            if raw.startswith("```"):
                raw = raw.split("\n", 1)[1].rsplit("```", 1)[0].strip()
            decoded = json.loads(raw)
        except (KeyError, IndexError, TypeError, json.JSONDecodeError):
            raise ProviderHTTPError("Gemini returned invalid batch JSON") from None
        if not isinstance(decoded, list):
            raise ProviderHTTPError("Gemini batch response must be a JSON array")
        translations: dict[str, str] = {}
        for item in decoded:
            if not isinstance(item, dict) or set(item) != {"id", "translation"}:
                raise ProviderHTTPError("Gemini batch item has an unexpected shape")
            key = str(item["id"]).strip()
            value = item["translation"]
            if key not in source_ids or key in translations or not isinstance(value, str) or not value.strip():
                raise ProviderHTTPError("Gemini batch returned missing, duplicate or empty translations")
            translations[key] = value.strip()
        if set(translations) != set(source_ids):
            raise ProviderHTTPError("Gemini batch did not return every source ID")
        for item in source:
            if not preserves_arguments(item["text"], translations[item["id"]]):
                raise ProviderHTTPError("Bản dịch theo lô làm thay đổi biến, thẻ định dạng hoặc số liệu")
        return translations

class OpenAICompatibleProvider:
    """Adapter for OpenAI Chat Completions-compatible HTTP endpoints."""

    def __init__(
        self,
        api_key: str,
        *,
        base_url: str = "https://api.openai.com/v1/chat/completions",
        timeout_seconds: float = 20,
    ) -> None:
        if not api_key.strip() or timeout_seconds <= 0:
            raise ValueError("A non-empty API key and positive timeout are required")
        if not base_url.startswith("https://"):
            raise ValueError("base_url must use HTTPS")
        self.api_key = api_key
        self.base_url = base_url
        self.timeout_seconds = timeout_seconds

    @classmethod
    def from_env(
        cls,
        variable: str = "OPENAI_API_KEY",
        *,
        base_url: str = "https://api.openai.com/v1/chat/completions",
        **kwargs,
    ):
        key = os.environ.get(variable, "")
        if not key:
            raise ValueError(f"Environment variable {variable} is not set")
        return cls(key, base_url=base_url, **kwargs)

    def translate(
        self, source_text: str, context: str, *, model: str,
        max_output_tokens: int,
    ) -> ProviderResponse:
        if not model.strip() or max_output_tokens < 1 or not source_text.strip():
            raise ValueError("Cần model, nội dung và giới hạn token hợp lệ")
        payload = {
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "Translate game text into natural Vietnamese. Preserve names, "
                        "variables, markup, placeholders, and line breaks. Return "
                        "only the Vietnamese translation, without explanations."
                    ),
                },
                {
                    "role": "user",
                    "content": f"Context: {context or 'Not provided'}\nText:\n{source_text}",
                },
            ],
            "max_tokens": max_output_tokens,
            "temperature": 0.2,
        }
        if model == "gpt-5-mini" or model.startswith("gpt-5-mini-"):
            payload["max_completion_tokens"] = payload.pop("max_tokens")
            del payload["temperature"]
        result = _post_json(
            self.base_url, payload,
            {"Authorization": f"Bearer {self.api_key}"},
            self.timeout_seconds,
        )
        try:
            if result["choices"][0].get("finish_reason", "stop") != "stop":
                raise ProviderHTTPError("OpenAI chưa hoàn tất bản dịch")
            translated = result["choices"][0]["message"]["content"]
            usage = result.get("usage", {})
            input_tokens = int(usage.get("prompt_tokens", 0))
            output_tokens = int(usage.get("completion_tokens", 0))
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise ProviderHTTPError("OpenAI-compatible provider returned no usable translation") from exc
        if not isinstance(translated, str) or not translated.strip():
            raise ProviderHTTPError("OpenAI-compatible provider returned an empty translation")
        return ProviderResponse(translated.strip(), input_tokens, output_tokens)
