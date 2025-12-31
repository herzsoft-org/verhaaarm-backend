-- Periods become DATE-only (no time)
-- Active period is computed by current date; we remove the "active" column and its unique index.

-- 1) Add new DATE columns
ALTER TABLE convent_periods
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS end_date DATE;

-- 2) Backfill from existing timestamptz using Europe/Berlin local date
UPDATE convent_periods
SET
    start_date = (start_at AT TIME ZONE 'Europe/Berlin')::date,
    end_date   = (end_at   AT TIME ZONE 'Europe/Berlin')::date
WHERE start_date IS NULL OR end_date IS NULL;

-- 3) Enforce NOT NULL on new columns
ALTER TABLE convent_periods
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL;

-- 4) Drop old constraint and columns that are no longer used
ALTER TABLE convent_periods DROP CONSTRAINT IF EXISTS chk_convent_periods_start_before_end;

-- drop "exactly one active period" index, because active is no longer stored
DROP INDEX IF EXISTS uq_convent_periods_single_active;

-- drop indexes on old columns
DROP INDEX IF EXISTS idx_convent_periods_start_at;

-- drop old columns
ALTER TABLE convent_periods
    DROP COLUMN IF EXISTS start_at,
    DROP COLUMN IF EXISTS end_at,
    DROP COLUMN IF EXISTS active;

-- 5) New check constraint for DATEs
ALTER TABLE convent_periods
    ADD CONSTRAINT chk_convent_periods_start_before_end_date CHECK (start_date < end_date);

-- 6) Helpful indexes
CREATE INDEX IF NOT EXISTS idx_convent_periods_start_date ON convent_periods (start_date);
CREATE INDEX IF NOT EXISTS idx_convent_periods_end_date ON convent_periods (end_date);
