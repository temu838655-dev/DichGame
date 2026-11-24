CREATE TABLE IF NOT EXISTS catalog (
  game_key TEXT PRIMARY KEY, title TEXT NOT NULL, genre TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE TABLE IF NOT EXISTS translations (
  game_key TEXT NOT NULL, lang TEXT NOT NULL CHECK(lang IN ('en','zh')),
  speaker_key TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('dialogue','menu')),
  source_key TEXT NOT NULL, original TEXT NOT NULL, vi TEXT NOT NULL,
  updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY(game_key,lang,speaker_key,kind,source_key)
);
CREATE TABLE IF NOT EXISTS submissions (
  id INTEGER PRIMARY KEY AUTOINCREMENT, game_key TEXT NOT NULL, lang TEXT NOT NULL CHECK(lang IN ('en','zh')),
  speaker_key TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('dialogue','menu')),
  source_key TEXT NOT NULL, original TEXT NOT NULL, vi TEXT NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (unixepoch()),
  UNIQUE(game_key,lang,speaker_key,kind,source_key,vi)
);
CREATE INDEX IF NOT EXISTS submissions_created ON submissions(created_at,id);
CREATE TABLE IF NOT EXISTS packs (
  game_key TEXT NOT NULL, lang TEXT NOT NULL CHECK(lang IN ('en','zh')), source_key TEXT NOT NULL,
  original TEXT NOT NULL, vi TEXT NOT NULL, updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY(game_key,lang,source_key)
);
CREATE TABLE IF NOT EXISTS daily_usage (
  day TEXT NOT NULL, operation TEXT NOT NULL, count INTEGER NOT NULL,
  PRIMARY KEY(day,operation)
);
