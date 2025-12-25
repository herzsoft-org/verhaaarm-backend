-- Allow multiple convent periods per semester label (WS24/25, SS25, ...)
-- This removes the uniqueness constraint on convent_periods.semester.
-- We keep the "single active period" partial unique index.

DROP INDEX IF EXISTS uq_convent_periods_semester;

-- Optional: keep a non-unique index for fast grouping/filtering by semester
CREATE INDEX IF NOT EXISTS idx_convent_periods_semester
    ON convent_periods (semester);
