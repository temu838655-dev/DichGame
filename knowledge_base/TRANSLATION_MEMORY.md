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

## Biến trong câu thoại và dữ liệu phiên bản cũ

Trong backend Python, khóa tra cứu và khóa chống gọi trùng giữ nguyên chữ hoa,
chữ thường của các biến như `{Player}`, tham số printf và thẻ định dạng.
Quy tắc này áp dụng cho cả câu gốc lẫn ngữ cảnh. Với dữ liệu cũ, backend đối
chiếu biến trong câu gốc và câu dịch trước khi dùng lại. Nếu ngữ cảnh cũ chỉ
còn giá trị băm và không thể xác minh biến, backend tra theo khóa mới.

Phép thử SQLite trên cùng máy dùng 20 lượt xen kẽ `Hello {Player}` và
`Hello {player}`, với hàm dịch thử giữ nguyên biến. Trước khi sửa, một lần gọi
hàm dịch tạo 10 kết quả sai biến. Sau khi sửa, hai lần gọi tạo đủ hai bản dịch,
18 lượt còn lại dùng bộ nhớ và cả 20 kết quả đều đúng biến. Đây là số đo độ
chính xác và số lượt gọi trong backend. Độ trễ dịch trực tuyến cần đo riêng.

Backend kiểm tra biến trước khi ghi kết quả AI và khi đọc bộ nhớ cũ. Trong
phép thử với SQLite mới, 20 câu dạng `Give {Player} N coins` nhận cùng một
phản hồi thử `Tặng xu` thiếu cả biến và số lượng. Trước khi sửa, bộ nhớ lưu
20 bản dịch lỗi. Sau khi sửa, số bản dịch lỗi được lưu là 0. Kiểm thử cũng
gửi lại câu có phản hồi hợp lệ để kiểm tra phục hồi sau lỗi.
