# AI provider adapters

Concrete HTTP adapters are included for Gemini and OpenAI Chat Completions
compatible endpoints. They use Python's standard library; no vendor SDK is
required.

## Gemini

Set the key in the runtime environment, not in source code:

```bash
export GEMINI_API_KEY="your-key"
```

```python
from knowledge_base import AIFallback, GeminiProvider, KnowledgeBase, TranslationMemory

kb = KnowledgeBase("translations.db")
memory = TranslationMemory(kb)
provider = GeminiProvider.from_env(timeout_seconds=20)
translator = AIFallback(
    kb, memory, provider, model="gemini-2.5-flash",
    daily_request_limit=100, max_retries=2,
)
result = translator.translate(
    "my-game", "The gate is locked.", context="quest:old-keep",
)
print(result.translated_text)
```

## OpenAI-compatible endpoint

```bash
export OPENAI_API_KEY="your-key"
```

```python
from knowledge_base import OpenAICompatibleProvider

provider = OpenAICompatibleProvider.from_env(
    base_url="https://api.openai.com/v1/chat/completions",
    timeout_seconds=20,
)
```

For a compatible provider, set its HTTPS Chat Completions endpoint and model
name. Some providers expose different token parameter names or response
shapes; those require a small adapter specific to that service.

## Security and cost notes

- API keys are passed at runtime or read from environment variables. This
  package does not write keys to SQLite, files, logs, or source control.
- HTTPS is required for OpenAI-compatible endpoints. TLS verification uses
  Python's default certificate validation.
- Network timeouts and response-size limits are enforced by the adapter.
- Each actual provider attempt consumes one daily request-budget unit; retries
  count separately. Successful token usage is recorded when supplied by the
  provider.
- The daily request limit is not a guaranteed monetary-cost cap. Set a low
  limit and configure billing limits with the provider.
- The adapter returns raw translated text as AI output. It is not marked
  VERIFIED; human review remains a separate workflow.
