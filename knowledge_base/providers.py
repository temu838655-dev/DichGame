"""Concrete stdlib HTTP adapters for Gemini and OpenAI-compatible APIs.

API keys are supplied at runtime and are never persisted by this package.
"""
from __future__ import annotations

import json
import os
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

from .ai_fallback import ProviderResponse


class ProviderHTTPError(RuntimeError):
    pass


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
        raise ProviderHTTPError(f"Provider returned HTTP {exc.code}") from None
    except URLError as exc:
        raise ProviderHTTPError("Provider network request failed") from None
    try:
        result = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProviderHTTPError("Provider returned invalid JSON") from exc
    if not isinstance(result, dict):
        raise ProviderHTTPError("Provider returned an unexpected response")
    return result


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
        prompt = (
            "Translate the game dialogue below into natural Vietnamese. "
            "Preserve names, variables, markup, placeholders, and line breaks. "
            "Return only the Vietnamese translation, without explanations.\n"
            f"Context: {context or 'Not provided'}\n"
            f"Text:\n{source_text}"
        )
        url = (
            f"{self.api_base}/models/{quote(model, safe='')}:generateContent?"
            + urlencode({"key": self.api_key})
        )
        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"maxOutputTokens": max_output_tokens, "temperature": 0.2},
        }
        result = _post_json(url, payload, {}, self.timeout_seconds)
        try:
            candidates = result["candidates"]
            parts = candidates[0]["content"]["parts"]
            translated = "".join(part.get("text", "") for part in parts)
            usage = result.get("usageMetadata", {})
            input_tokens = int(usage.get("promptTokenCount", 0))
            output_tokens = int(usage.get("candidatesTokenCount", 0))
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise ProviderHTTPError("Gemini returned no usable translation") from exc
        if not translated.strip():
            raise ProviderHTTPError("Gemini returned an empty translation")
        return ProviderResponse(translated.strip(), input_tokens, output_tokens)


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
        result = _post_json(
            self.base_url, payload,
            {"Authorization": f"Bearer {self.api_key}"},
            self.timeout_seconds,
        )
        try:
            translated = result["choices"][0]["message"]["content"]
            usage = result.get("usage", {})
            input_tokens = int(usage.get("prompt_tokens", 0))
            output_tokens = int(usage.get("completion_tokens", 0))
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise ProviderHTTPError("OpenAI-compatible provider returned no usable translation") from exc
        if not isinstance(translated, str) or not translated.strip():
            raise ProviderHTTPError("OpenAI-compatible provider returned an empty translation")
        return ProviderResponse(translated.strip(), input_tokens, output_tokens)
