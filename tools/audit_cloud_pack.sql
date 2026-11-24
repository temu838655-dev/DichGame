-- Read-only audit for existing dichgame-community D1 schema.
-- Never automatically delete or replace ambiguous translations.
SELECT game_key, lang, COUNT(*) AS total,
       SUM(CASE WHEN trim(original)='' OR trim(vi)='' THEN 1 ELSE 0 END) AS empty_rows,
       SUM(CASE WHEN original=vi THEN 1 ELSE 0 END) AS unchanged_rows
FROM packs GROUP BY game_key, lang;

-- Restrict to the GTA VI dataset family; do not mix unrelated games.
SELECT original, COUNT(*) AS occurrences, COUNT(DISTINCT vi) AS variants
FROM pack_import_staging
WHERE dataset GLOB 'gta6_user_*'
GROUP BY original
HAVING COUNT(DISTINCT vi)>1
ORDER BY variants DESC, original;

-- Show candidate variants with provenance for manual review.
WITH conflicts AS (
  SELECT original FROM pack_import_staging
  WHERE dataset GLOB 'gta6_user_*'
  GROUP BY original HAVING COUNT(DISTINCT vi)>1
)
SELECT s.original, s.vi, s.dataset, s.source_id
FROM pack_import_staging s JOIN conflicts c ON s.original=c.original
WHERE s.dataset GLOB 'gta6_user_*'
ORDER BY s.original, s.vi, s.dataset, s.source_id;
