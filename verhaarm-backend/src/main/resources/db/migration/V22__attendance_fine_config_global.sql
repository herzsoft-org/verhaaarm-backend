-- Make attendance fine config global (no period tie).
-- Old model: attendance_fine_config(period_id PK -> convent_periods)
-- New model: attendance_fine_config(config_id PK) with exactly one row (config_id=1)

ALTER TABLE attendance_fine_config
    ADD COLUMN IF NOT EXISTS config_id SMALLINT;

-- Backfill: pick the most recently updated row as the global config
-- (if table is empty, this does nothing; your app will then require setting config once).
UPDATE attendance_fine_config
SET config_id = 1
WHERE config_id IS NULL;

-- Drop old FK + PK on period_id
ALTER TABLE attendance_fine_config
DROP CONSTRAINT IF EXISTS attendance_fine_config_period_id_fkey;

ALTER TABLE attendance_fine_config
DROP CONSTRAINT IF EXISTS attendance_fine_config_pkey;

-- Ensure exactly one row key
ALTER TABLE attendance_fine_config
    ALTER COLUMN config_id SET NOT NULL;

ALTER TABLE attendance_fine_config
    ADD CONSTRAINT attendance_fine_config_pkey PRIMARY KEY (config_id);

-- Remove the period_id column entirely
ALTER TABLE attendance_fine_config
DROP COLUMN IF EXISTS period_id;
