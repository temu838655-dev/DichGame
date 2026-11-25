# Community contribution and review

The package separates public contribution from verification.

## Flow

1. A contributor submits source text and a proposed Vietnamese translation.
2. The item is stored as `COMMUNITY`; submission never marks it verified.
3. Players may report a mistranslation or other issue. Reports enter an open queue.
4. A reviewer approved by the host application's authorization callback may
   approve, reject, or request changes.
5. Approval sets `VERIFIED` and records the reviewer and timestamp. Rejection
   removes the item from normal matching. Review history remains append-only.

## Example

```python
from knowledge_base import CommunityModeration, KnowledgeBase

kb = KnowledgeBase("translations.db")
moderation = CommunityModeration(
    kb,
    reviewer_authorizer=lambda user_id: user_id in trusted_reviewer_ids,
)
translation_id = moderation.submit(
    "my-game", "Open the gate", "Mở cổng",
    contributor_id="account-123",
    context="quest:old-fortress",
)
moderation.report(
    translation_id, "account-456", "MISTRANSLATION",
    details="The character is asking to unlock, not open.",
)
# Call only from an authenticated reviewer workflow:
moderation.review(
    translation_id, "reviewer-1", "APPROVE",
    note="Checked against in-game context.",
)
```

The IDs above are illustrative. The host application must authenticate
contributors, implement `trusted_reviewer_ids`, protect reviewer credentials,
and apply rate limits / anti-spam controls. The Python package does not provide
an account system or a hosted moderation dashboard.
