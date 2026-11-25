# Provider-neutral AI fallback

The fallback layer is an adapter contract, not a bundled AI vendor. It can
connect to Gemini, an OpenAI-compatible endpoint, or a local model when an
application supplies an adapter implementing AIProvider.

## Example

```python
from knowledge_base.ai_fallback import AIFallback, ProviderResponse
from knowledge_base.db import KnowledgeBase
from knowledge_base.translation_memory import TranslationMemory

class MyAdapter:
    def translate(self, source_text, context, *, model, max_output_tokens):
        # Call the selected provider here. Enforce a network timeout and the
        # requested output-token limit in the concrete adapter.
        return ProviderResponse("Bản dịch", input_tokens=20, output_tokens=8)

kb = KnowledgeBase("translations.db")
memory = TranslationMemory(kb)
translator = AIFallback(
    kb, memory, MyAdapter(), model="my-configured-model",
    daily_request_limit=100, max_retries=2,
)
result = translator.translate("my-game", "Quest complete", context="quest")
print(result.translated_text)
```

## Controls

- Cache is checked before any provider call. Offline mode still serves cached
  results and raises AIUnavailableError for cache misses.
- A SQLite-backed UTC daily request counter is reserved atomically before each
  provider attempt. Retries count as separate requests, so concurrent calls
  cannot bypass the configured request limit.
- Exponential backoff is bounded by max_retries. Provider exceptions and empty
  output are not cached by the translation-memory layer.
- Input character and requested output-token limits are passed/enforced at the
  adapter boundary. The concrete adapter must enforce network timeouts and
  provider-specific token limits; this generic package cannot force a remote
  vendor to honor them.
- Successful provider token usage is recorded per UTC day. Failed attempts
  still count as requests but have no token totals when the provider did not
  return usage metadata.

## Not included

No vendor SDK, API key storage, actual network adapter, monetary-cost estimate,
automatic model selection, or guaranteed hard dollar cap is bundled. The
request-count budget is persistent and hard; a dollar cap requires trustworthy
provider pricing and usage metadata in a concrete adapter.
