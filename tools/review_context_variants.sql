-- Review GTA VI ambiguous lines against the currently published pack.
-- Exact original comparison is intentional; no fuzzy joins or automatic writes.
WITH variants AS (
 SELECT original, vi, COUNT(*) AS occurrences
 FROM pack_import_staging
 WHERE dataset GLOB 'gta6_user_*'
 GROUP BY original, vi
), ambiguous AS (
 SELECT original FROM variants GROUP BY original HAVING COUNT(*) > 1
)
SELECT v.original, v.vi AS candidate_vi, v.occurrences,
 p.vi AS published_vi,
 CASE WHEN p.vi = v.vi THEN 'published'
      WHEN p.vi IS NULL THEN 'not_found_exact'
      ELSE 'alternative' END AS review_status
FROM variants v JOIN ambiguous a ON a.original=v.original
LEFT JOIN packs p ON p.game_key='grand theft auto vi'
 AND p.lang='en' AND p.original=v.original
ORDER BY v.original, v.occurrences DESC, v.vi;
-- Frequency is not evidence of semantic correctness.
-- Preserve alternative variants until speaker and scene metadata are available.
