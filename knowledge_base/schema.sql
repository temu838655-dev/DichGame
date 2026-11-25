PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS games (
    game_id TEXT PRIMARY KEY,
    game_name TEXT NOT NULL,
    alternative_names_json TEXT NOT NULL DEFAULT '[]',
    developer TEXT,
    publisher TEXT,
    platform TEXT NOT NULL,
    region TEXT,
    game_version TEXT,
    source_language TEXT NOT NULL DEFAULT 'en',
    target_language TEXT NOT NULL DEFAULT 'vi',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE IF NOT EXISTS data_sources (
    source_id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_name TEXT NOT NULL,
    source_url TEXT,
    author_name TEXT,
    license_id TEXT NOT NULL,
    license_url TEXT,
    platform TEXT,
    game_version TEXT,
    imported_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    verification_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED','NEEDS_REVIEW')),
    attribution TEXT,
    content_sha256 TEXT,
    notes TEXT
);

CREATE TABLE IF NOT EXISTS translations (
    translation_id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    source_text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    text_sha256 TEXT NOT NULL,
    context TEXT,
    speaker TEXT,
    scene_id TEXT,
    mission_id TEXT,
    previous_sentence TEXT,
    next_sentence TEXT,
    source_id INTEGER REFERENCES data_sources(source_id) ON DELETE SET NULL,
    contributor_id TEXT,
    translation_source TEXT NOT NULL DEFAULT 'COMMUNITY',
    translation_status TEXT NOT NULL DEFAULT 'COMMUNITY'
        CHECK (translation_status IN ('VERIFIED','COMMUNITY','AI_REVIEWED','AI_UNVERIFIED','REJECTED')),
    confidence_score REAL CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1)),
    verified_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    CHECK (length(trim(source_text)) > 0),
    CHECK (length(trim(translated_text)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_translations_game_hash
    ON translations(game_id, text_sha256);
CREATE INDEX IF NOT EXISTS idx_translations_game_normalized
    ON translations(game_id, normalized_text);
CREATE INDEX IF NOT EXISTS idx_translations_status
    ON translations(game_id, translation_status);
CREATE INDEX IF NOT EXISTS idx_translations_context
    ON translations(game_id, speaker, scene_id, mission_id);

CREATE TABLE IF NOT EXISTS glossary (
    glossary_id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    original_term TEXT NOT NULL,
    normalized_term TEXT NOT NULL,
    translated_term TEXT NOT NULL,
    category TEXT,
    description TEXT,
    context TEXT,
    priority INTEGER NOT NULL DEFAULT 0,
    source_id INTEGER REFERENCES data_sources(source_id) ON DELETE SET NULL,
    UNIQUE(game_id, normalized_term)
);
CREATE INDEX IF NOT EXISTS idx_glossary_lookup ON glossary(game_id, normalized_term);

CREATE TABLE IF NOT EXISTS characters (
    character_id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    character_key TEXT NOT NULL,
    original_name TEXT NOT NULL,
    localized_name TEXT,
    gender_reference TEXT,
    preferred_pronouns TEXT,
    relationship TEXT,
    speaking_style TEXT,
    notes TEXT,
    UNIQUE(game_id, character_key)
);

CREATE TABLE IF NOT EXISTS ocr_dictionary (
    ocr_id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    correct_word TEXT NOT NULL,
    incorrect_variants_json TEXT NOT NULL DEFAULT '[]',
    correction_rule TEXT,
    confidence_score REAL NOT NULL DEFAULT 0.8
        CHECK (confidence_score >= 0 AND confidence_score <= 1),
    UNIQUE(game_id, correct_word)
);

CREATE TABLE IF NOT EXISTS translation_cache (
    game_id TEXT NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    text_hash TEXT NOT NULL,
    context_hash TEXT NOT NULL DEFAULT '',
    source_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    model TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    last_used TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    use_count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(game_id, text_hash, context_hash)
);

CREATE TABLE IF NOT EXISTS translation_feedback (
    feedback_id INTEGER PRIMARY KEY AUTOINCREMENT,
    translation_id INTEGER NOT NULL REFERENCES translations(translation_id) ON DELETE CASCADE,
    user_correction TEXT,
    rating INTEGER CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    report_count INTEGER NOT NULL DEFAULT 0,
    verified_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (verified_status IN ('PENDING','ACCEPTED','REJECTED')),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX IF NOT EXISTS idx_feedback_translation ON translation_feedback(translation_id);

CREATE TABLE IF NOT EXISTS import_jobs (
    import_id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER REFERENCES data_sources(source_id) ON DELETE SET NULL,
    game_id TEXT REFERENCES games(game_id) ON DELETE CASCADE,
    file_name TEXT NOT NULL,
    file_format TEXT NOT NULL,
    file_sha256 TEXT NOT NULL,
    imported_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED')),
    error_message TEXT,
    started_at TEXT,
    completed_at TEXT
);

CREATE VIRTUAL TABLE IF NOT EXISTS translations_fts USING fts5(
    source_text,
    translated_text,
    context,
    speaker,
    content='translations',
    content_rowid='translation_id',
    tokenize='unicode61 remove_diacritics 0'
);

CREATE TRIGGER IF NOT EXISTS translations_fts_insert AFTER INSERT ON translations BEGIN
    INSERT INTO translations_fts(rowid, source_text, translated_text, context, speaker)
    VALUES (new.translation_id, new.source_text, new.translated_text, new.context, new.speaker);
END;

CREATE TRIGGER IF NOT EXISTS translations_fts_delete AFTER DELETE ON translations BEGIN
    INSERT INTO translations_fts(translations_fts, rowid, source_text, translated_text, context, speaker)
    VALUES ('delete', old.translation_id, old.source_text, old.translated_text, old.context, old.speaker);
END;

CREATE TRIGGER IF NOT EXISTS translations_fts_update AFTER UPDATE ON translations BEGIN
    INSERT INTO translations_fts(translations_fts, rowid, source_text, translated_text, context, speaker)
    VALUES ('delete', old.translation_id, old.source_text, old.translated_text, old.context, old.speaker);
    INSERT INTO translations_fts(rowid, source_text, translated_text, context, speaker)
    VALUES (new.translation_id, new.source_text, new.translated_text, new.context, new.speaker);
END;

CREATE TABLE IF NOT EXISTS translation_request_locks (
    game_id TEXT NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    text_hash TEXT NOT NULL,
    context_hash TEXT NOT NULL DEFAULT '',
    owner_token TEXT NOT NULL,
    lease_until TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    PRIMARY KEY(game_id, text_hash, context_hash)
);

CREATE INDEX IF NOT EXISTS idx_translation_cache_last_used
    ON translation_cache(last_used);

CREATE TABLE IF NOT EXISTS ai_usage_daily (
    usage_date TEXT PRIMARY KEY,
    request_count INTEGER NOT NULL DEFAULT 0,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS translation_reviews (
    review_id INTEGER PRIMARY KEY AUTOINCREMENT,
    translation_id INTEGER NOT NULL REFERENCES translations(translation_id) ON DELETE CASCADE,
    reviewer_id TEXT NOT NULL,
    decision TEXT NOT NULL CHECK (decision IN ('APPROVE','REJECT','REQUEST_CHANGES')),
    note TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX IF NOT EXISTS idx_translation_reviews_item
    ON translation_reviews(translation_id, created_at);

CREATE TABLE IF NOT EXISTS translation_reports (
    report_id INTEGER PRIMARY KEY AUTOINCREMENT,
    translation_id INTEGER NOT NULL REFERENCES translations(translation_id) ON DELETE CASCADE,
    reporter_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    details TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','RESOLVED','DISMISSED')),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    resolved_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_translation_reports_status
    ON translation_reports(status, translation_id);

INSERT OR IGNORE INTO schema_migrations(version, description)
VALUES
    (1, 'Initial multi-game localization knowledge-base schema'),
    (2, 'Persistent leases for duplicate translation request suppression'),
    (3, 'Daily AI request and token usage accounting'),
    (4, 'Community translation reviews and reports'),
    (5, 'Track community contributor identifiers');
