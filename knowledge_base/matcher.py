"""Offline translation lookup: OCR repair, exact/context lookup and fuzzy search."""
from __future__ import annotations

import json
import re
from contextlib import closing
from dataclasses import dataclass
from difflib import SequenceMatcher
from typing import Any

from .db import KnowledgeBase, normalize_text, preserves_arguments, runtime_arguments

try:
    from rapidfuzz import fuzz
except ImportError:  # Standard-library fallback keeps offline installs dependency-free.
    fuzz = None


@dataclass
class MatchResult:
    translation: dict[str, Any]
    method: str
    confidence: float
    corrected_source: str
    corrections: list[dict[str, str]]


def _similarity(left: str, right: str) -> float:
    if fuzz is not None:
        return float(fuzz.ratio(left, right)) / 100.0
    return SequenceMatcher(None, left, right).ratio()


def _context_score(row: dict[str, Any], context: dict[str, str]) -> float:
    # Previous/next lines are part of game dialogue context too. They must be
    # considered in both exact-match disambiguation and fuzzy candidate ranking.
    keys = ("context", "speaker", "scene_id", "mission_id",
            "previous_sentence", "next_sentence")
    supplied = [(key, value) for key, value in context.items() if key in keys and value]
    if not supplied:
        return 0.0
    matched = sum(
        1 for key, value in supplied
        if normalize_text(str(row.get(key) or "")) == normalize_text(value)
    )
    return matched / len(supplied)


def _compatible(row, context: dict[str, str]) -> bool:
    return all(
        not row[key] or normalize_text(row[key]) == normalize_text(value)
        for key, value in context.items() if value
    )


class TranslationMatcher:
    def __init__(self, knowledge_base: KnowledgeBase):
        self.kb = knowledge_base

    def correct_ocr(self, game_id: str, text: str, min_confidence: float = 0.95) -> tuple[str, list[dict[str, str]]]:
        """Apply only unambiguous, explicitly curated whole-token OCR substitutions."""
        with closing(self.kb.connect()) as db:
            entries = db.execute(
                "SELECT correct_word, incorrect_variants_json, confidence_score "
                "FROM ocr_dictionary WHERE game_id=?",
                (game_id,),
            ).fetchall()

        variant_map: dict[str, set[str]] = {}
        for entry in entries:
            if float(entry["confidence_score"]) < min_confidence:
                continue
            try:
                variants = json.loads(entry["incorrect_variants_json"])
            except (TypeError, json.JSONDecodeError):
                continue
            if not isinstance(variants, list):
                continue
            correct = str(entry["correct_word"])
            for variant in variants:
                if isinstance(variant, str) and variant:
                    variant_map.setdefault(normalize_text(variant), set()).add(correct)

        corrections: list[dict[str, str]] = []
        output = text
        for variant, candidates in variant_map.items():
            if len(candidates) != 1:
                continue
            correct = next(iter(candidates))
            pattern = re.compile(r"(?<!\w)" + re.escape(variant) + r"(?!\w)", re.IGNORECASE)
            output, count = pattern.subn(correct, output)
            if count:
                corrections.append({"from": variant, "to": correct})
        return output, corrections

    def _candidate_rows(self, game_id: str, query: str, limit: int) -> list[dict[str, Any]]:
        terms = [term for term in re.findall(r"[\w]+", query, flags=re.UNICODE) if len(term) > 1]
        if not terms:
            return []
        match_query = " OR ".join('"' + term.replace('"', '""') + '"' for term in terms[:12])
        with self.kb.connect() as db:
            rows = db.execute(
                """SELECT t.* FROM translations_fts f
                   JOIN translations t ON t.translation_id=f.rowid
                   WHERE translations_fts MATCH ? AND t.game_id=?
                     AND t.translation_status != 'REJECTED'
                   ORDER BY bm25(translations_fts) LIMIT ?""",
                (match_query, game_id, max(1, min(limit, 1000))),
            ).fetchall()
            return [dict(row) for row in rows]

    def lookup(
        self,
        game_id: str,
        source_text: str,
        *,
        context: str | None = None,
        speaker: str | None = None,
        scene_id: str | None = None,
        mission_id: str | None = None,
        previous_sentence: str | None = None,
        next_sentence: str | None = None,
        fuzzy_threshold: float = 0.92,
        max_candidates: int = 500,
    ) -> MatchResult | None:
        context_data = {
            "context": context or "", "speaker": speaker or "",
            "scene_id": scene_id or "", "mission_id": mission_id or "",
            "previous_sentence": previous_sentence or "",
            "next_sentence": next_sentence or "",
        }
        corrected, corrections = self.correct_ocr(game_id, source_text)
        normalized = normalize_text(corrected)

        with self.kb.connect() as db:
            exact_rows = db.execute(
                """SELECT * FROM translations
                   WHERE game_id=? AND normalized_text=?
                     AND translation_status != 'REJECTED'""",
                (game_id, normalized),
            ).fetchall()
        arguments = runtime_arguments(corrected)
        if exact_rows:
            exact_rows = [row for row in exact_rows if _compatible(row, context_data)
                          and runtime_arguments(row["source_text"]) == arguments
                          and preserves_arguments(corrected, row["translated_text"])]
            if not exact_rows:
                return None
            rows = [dict(row) for row in exact_rows]
            rows.sort(key=lambda row: (
                _context_score(row, context_data),
                {"VERIFIED": 5, "AI_REVIEWED": 4, "COMMUNITY": 3,
                 "AI_UNVERIFIED": 2}.get(row["translation_status"], 0),
                float(row.get("confidence_score") or 0),
            ), reverse=True)
            best = rows[0]
            context_fit = _context_score(best, context_data)
            for other in rows[1:]:
                if other["translated_text"] == best["translated_text"]:
                    continue
                if _context_score(other, context_data) != context_fit:
                    continue
                if any(other[key] != best[key] for key in context_data):
                    return None
                if other["translation_status"] == best["translation_status"]:
                    return None
            return MatchResult(best, "OCR_CORRECTED_EXACT" if corrections else "EXACT",
                               1.0, corrected, corrections)

        candidates = self._candidate_rows(game_id, corrected, max_candidates)
        if not candidates:
            return None

        scored: list[tuple[float, float, dict[str, Any]]] = []
        for row in candidates:
            if (not _compatible(row, context_data) or runtime_arguments(row["source_text"]) != arguments
                    or not preserves_arguments(corrected, row["translated_text"])):
                continue
            source_norm = normalize_text(str(row["source_text"]))
            lexical = _similarity(normalized, source_norm)
            context_fit = _context_score(row, context_data)
            supplied_context_count = sum(bool(v) for v in context_data.values())
            if supplied_context_count:
                score = lexical * 0.88 + context_fit * 0.12
                if context_fit == 0:
                    score = min(score, lexical - 0.025)
            else:
                score = lexical
            status_bonus = {"VERIFIED": 0.008, "AI_REVIEWED": 0.005}.get(
                row["translation_status"], 0.0
            )
            scored.append((min(1.0, score + status_bonus), lexical, row))

        if not scored:
            return None
        scored.sort(key=lambda item: item[0], reverse=True)
        best_score, lexical, best = scored[0]
        if best_score < fuzzy_threshold:
            return None
        for other_score, _, other in scored[1:]:
            if best_score - other_score >= 0.015:
                break
            if (other["translated_text"] != best["translated_text"]
                    or other.get("speaker") != best.get("speaker")
                    or other.get("scene_id") != best.get("scene_id")):
                return None
        return MatchResult(best, "FUZZY", best_score, corrected, corrections)
