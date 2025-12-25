-- ===== src/main/resources/db/migration/V19__attendance_drop_period_id.sql =====
-- Attendance rows are exceptions per event: remove period_id.

DROP INDEX IF EXISTS idx_attendance_period_id;

ALTER TABLE attendance
DROP COLUMN IF EXISTS period_id;
