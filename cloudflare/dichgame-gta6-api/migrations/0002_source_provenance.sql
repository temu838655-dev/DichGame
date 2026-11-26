ALTER TABLE gta6_localization ADD COLUMN source_name TEXT NOT NULL DEFAULT '';
ALTER TABLE gta6_localization ADD COLUMN license_id TEXT NOT NULL DEFAULT '';
ALTER TABLE gta6_localization ADD COLUMN source_url TEXT NOT NULL DEFAULT '';
ALTER TABLE gta6_localization ADD COLUMN license_url TEXT NOT NULL DEFAULT '';
ALTER TABLE gta6_localization ADD COLUMN attribution TEXT NOT NULL DEFAULT '';
ALTER TABLE gta6_localization ADD COLUMN provenance_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(provenance_status IN ('PENDING','DECLARED','VERIFIED','REJECTED'));
ALTER TABLE gta6_localization ADD COLUMN rights_confirmed_at INTEGER;
