-- ===== src/main/resources/db/migration/V18__events_drop_period_id.sql =====
-- Events become date-based buckets: remove FK coupling to periods.

DROP INDEX IF EXISTS idx_events_period_id;

ALTER TABLE events
DROP COLUMN IF EXISTS period_id;
