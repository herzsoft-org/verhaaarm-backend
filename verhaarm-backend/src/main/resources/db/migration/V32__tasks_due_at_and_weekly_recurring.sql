-- Tasks get a due_at (timestamptz)
-- Add weekly recurring tasks:
--   recurring_enabled: boolean
--   recurring_days: TEXT like "MON,WED,FRI"
--   recurring_due_time: TIME (local Berlin time interpreted by backend)
--   recurring_open_for: DATE (the occurrence-date that is currently "active")

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS due_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS recurring_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS recurring_days TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS recurring_due_time TIME NULL,
    ADD COLUMN IF NOT EXISTS recurring_open_for DATE NULL;

-- Backfill due_at for existing tasks:
-- "tomorrow 20:15 Europe/Berlin"
UPDATE tasks
SET due_at = (
    (date_trunc('day', now() AT TIME ZONE 'Europe/Berlin')
        + interval '1 day'
        + interval '20 hours 15 minutes'
        ) AT TIME ZONE 'Europe/Berlin'
    )
WHERE due_at IS NULL;

ALTER TABLE tasks
    ALTER COLUMN due_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tasks_due_at ON tasks (due_at);
CREATE INDEX IF NOT EXISTS idx_tasks_recurring_enabled ON tasks (recurring_enabled);
CREATE INDEX IF NOT EXISTS idx_tasks_recurring_open_for ON tasks (recurring_open_for);
