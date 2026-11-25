# Offline matching and OCR correction

\`TranslationMatcher\` implements a conservative lookup pipeline:

1. Apply curated whole-token OCR substitutions only when the dictionary entry
   confidence is at least 0.95 and the erroneous form maps to exactly one term.
2. Search exact normalized text, preferring matching speaker/scene/mission/context
   and then higher-quality translation statuses.
3. Search a bounded SQLite FTS5 candidate set and rank it by string similarity.
4. Reject below-threshold matches and ambiguous near-ties so the caller can use
   its AI fallback rather than returning a risky translation.

RapidFuzz is used when installed. Python's \`difflib.SequenceMatcher\` is the
zero-dependency fallback. Install the optional accelerator with
\`pip install rapidfuzz\`.

## Example

\`\`\`python
from knowledge_base.db import KnowledgeBase
from knowledge_base.matcher import TranslationMatcher

kb = KnowledgeBase("translations.db")
matcher = TranslationMatcher(kb)
result = matcher.lookup(
    "your-game-id", "Find the hiden key",
    speaker="Guard", mission_id="chapter-2",
)
if result:
    print(result.method, result.confidence, result.translation["translated_text"])
else:
    # No sufficiently reliable local match: caller may consult cache/AI fallback.
    print("MISS")
\`\`\`

Fuzzy search is deliberately bounded to at most 1,000 FTS5 candidates to avoid
unbounded memory growth. This is a candidate retrieval strategy, not a guarantee
of global nearest-neighbor recall across arbitrarily large corpora. Production
benchmarking and a dedicated n-gram index may be needed for very large datasets.
OCR substitutions are not inferred automatically: ambiguous or low-confidence
variants remain untouched.
