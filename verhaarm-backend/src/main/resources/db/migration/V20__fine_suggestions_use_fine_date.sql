-- Switch fine_suggestions from period_id to fine_date (date-only)
-- Backfill fine_date from created_at (UTC date) for existing rows

ALTER TABLE fine_suggestions
    ADD COLUMN IF NOT EXISTS fine_date DATE;

UPDATE fine_suggestions
SET fine_date = (created_at AT TIME ZONE 'UTC')::date
WHERE fine_date IS NULL;

ALTER TABLE fine_suggestions
    ALTER COLUMN fine_date SET NOT NULL;

-- Cleanup old period coupling
DROP INDEX IF EXISTS idx_fine_suggestions_period_id;

ALTER TABLE fine_suggestions
DROP COLUMN IF EXISTS period_id;

CREATE INDEX IF NOT EXISTS idx_fine_suggestions_fine_date
    ON fine_suggestions (fine_date);
