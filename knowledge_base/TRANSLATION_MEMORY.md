# Translation memory and request coordination

Use the cache-first layer after local knowledge-base matching and before asking
an AI provider to translate a line. The provider is injected by the app, so the
knowledge base stays independent of Gemini, OpenAI-compatible services, or any
other vendor.

## Example

```python
from knowledge_base.db import KnowledgeBase
from knowledge_base.translation_memory import TranslationMemory

kb = KnowledgeBase("translations.db")
memory = TranslationMemory(kb, max_cache_age_seconds=30 * 24 * 60 * 60)

def my_provider(source_text: str, context: str) -> str:
    # Call your configured AI provider here, with its own network timeout.
    return "Bản dịch tiếng Việt"

result = memory.get_or_translate(
    "my-game-id",
    "The gate is locked.",
    my_provider,
    model="configured-model-name",
    context="quest:the-old-keep",
)
print(result.translated_text, result.cache_hit)
```

## Guarantees and limits

- Cache keys are scoped by game, normalized source text, and normalized
  context. Similar words in a shop and a combat scene do not share a result.
- Cache hits skip the provider call. Optional max_cache_age_seconds ignores
  stale entries; the default keeps entries until replaced or cleared.
- A persistent SQLite lease coordinates workers sharing the same database.
  Concurrent requests for the same key wait for the first worker and reuse its
  result instead of issuing duplicate provider calls.
- Provider errors and empty outputs are not cached; the lease is released in a
  finally block.
- lease_seconds must exceed the provider's maximum request duration. Leases
  expire to recover from crashed workers; a provider call that outlives its
  lease can still be duplicated. Set the provider timeout accordingly.
- This layer does not call an AI service, choose a model, enforce a monetary
  budget, retry provider failures, or verify translation quality. Those belong
  to the provider adapter and later orchestration work.
- Cache entries are AI/provider output, not verified community translations.
  They are kept separate from the reviewed translations table.
