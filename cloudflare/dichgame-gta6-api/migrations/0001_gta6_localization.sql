CREATE TABLE IF NOT EXISTS gta6_localization (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  game_key TEXT NOT NULL DEFAULT 'grand theft auto vi',
  external_id TEXT NOT NULL,
  sheet_name TEXT NOT NULL DEFAULT '',
  row_number INTEGER,
  group_name TEXT NOT NULL DEFAULT '',
  source_text TEXT NOT NULL,
  normalized_text TEXT NOT NULL,
  translated_text TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','AI_UNVERIFIED','VERIFIED','REJECTED')),
  source_sha256 TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
  UNIQUE(game_key, external_id)
);
CREATE INDEX IF NOT EXISTS idx_gta6_lookup_id ON gta6_localization(game_key, external_id, status);
CREATE INDEX IF NOT EXISTS idx_gta6_lookup_text ON gta6_localization(game_key, normalized_text, status);
CREATE INDEX IF NOT EXISTS idx_gta6_pending ON gta6_localization(game_key, status, id);
