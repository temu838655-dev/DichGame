"""Multi-game localization knowledge base."""
from .db import KnowledgeBase, normalize_text, sha256_text
from .translation_memory import TranslationMemory, TranslationResult
from .ai_fallback import (
    AIFallback, AIFallbackError, AIBudgetExceededError,
    AIUnavailableError, ProviderResponse,
)
from .moderation import CommunityModeration, ModerationPermissionError, ReviewOutcome
from .backup import create_backup, restore_backup, validate_backup
from .sync import export_game_pack, import_game_pack, PackIntegrityError

__all__ = [
    "KnowledgeBase", "normalize_text", "sha256_text",
    "TranslationMemory", "TranslationResult",
    "AIFallback", "AIFallbackError", "AIBudgetExceededError",
    "AIUnavailableError", "ProviderResponse",
    "CommunityModeration", "ModerationPermissionError", "ReviewOutcome",
    "create_backup", "restore_backup", "validate_backup",
    "export_game_pack", "import_game_pack", "PackIntegrityError",
]
