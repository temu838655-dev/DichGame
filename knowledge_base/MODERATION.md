# Community contribution and review

The package separates public contribution from verification.

## Flow

1. A contributor submits source text and a proposed Vietnamese translation.
2. The item is stored as `COMMUNITY` with its contributor ID; submission never marks it verified.
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


## Local moderation command line

For a trusted maintainer workflow, use `python -m knowledge_base.moderation_cli`
to submit proposals, inspect pending translations and open reports, record
reports, and review entries.

Set `DICHGAME_REVIEWER_IDS` in the trusted operator environment to a
comma-separated allowlist of reviewer IDs. Never commit this setting or expose
it to public clients. This CLI enforces a local allowlist only; it is not a
hosted API, login system, or replacement for server-side authentication and
rate limiting.

```bash
python -m knowledge_base.moderation_cli --db translations.db submit \
  --game-id my-game --source "Open the gate" --translation "Mở cổng" \
  --contributor-id player-123

python -m knowledge_base.moderation_cli --db translations.db queue
python -m knowledge_base.moderation_cli --db translations.db reports

python -m knowledge_base.moderation_cli --db translations.db report \
  --translation-id 12 --reporter-id player-456 \
  --reason MISTRANSLATION --details "Context says unlock the gate"

DICHGAME_REVIEWER_IDS=reviewer-1 python -m knowledge_base.moderation_cli \
  --db translations.db review --translation-id 12 --reviewer-id reviewer-1 \
  --decision APPROVE --note "Checked against in-game context"
```

Contributor IDs are stored on submitted translations. Existing databases are
upgraded additively; old rows retain a null contributor ID. IDs are labels
provided by the host application, not proof of identity.
