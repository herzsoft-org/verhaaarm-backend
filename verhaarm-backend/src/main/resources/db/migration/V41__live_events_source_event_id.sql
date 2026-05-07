ALTER TABLE live_events
    ADD COLUMN IF NOT EXISTS source_event_id UUID NULL REFERENCES events(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_live_events_source_event_id
    ON live_events (source_event_id)
    WHERE source_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_live_events_source_event_id
    ON live_events (source_event_id);