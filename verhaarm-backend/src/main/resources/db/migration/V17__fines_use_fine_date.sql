-- ===== src/main/resources/db/migration/V17__fines_use_fine_date.sql =====
-- Switch fines from period_id FK to fine_date (date-only).
-- Backfill fine_date from created_at (UTC date) for existing rows.

ALTER TABLE fines
    ADD COLUMN IF NOT EXISTS fine_date DATE;

UPDATE fines
SET fine_date = (created_at AT TIME ZONE 'UTC')::date
WHERE fine_date IS NULL;

ALTER TABLE fines
    ALTER COLUMN fine_date SET NOT NULL;

DROP INDEX IF EXISTS idx_fines_period_id;

ALTER TABLE fines
DROP COLUMN IF EXISTS period_id;
