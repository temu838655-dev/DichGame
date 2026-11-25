"""Multi-game localization knowledge base."""
from .db import KnowledgeBase, normalize_text, sha256_text\nfrom .translation_memory import TranslationMemory, TranslationResult

__all__ = ["KnowledgeBase", "normalize_text", "sha256_text", "TranslationMemory", "TranslationResult"]
