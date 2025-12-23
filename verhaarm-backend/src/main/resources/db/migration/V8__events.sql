-- Scheduled events (cleanup, prep, etc.)
-- soft delete via deleted_at

CREATE TABLE IF NOT EXISTS events (
                                      id UUID PRIMARY KEY,
                                      period_id UUID NOT NULL REFERENCES convent_periods(id),
    creator_user_id UUID NOT NULL REFERENCES users(id),

    title TEXT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,

    owner_type TEXT NOT NULL, -- SENIOR or HOUSEKEEPING

    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_events_period_id ON events (period_id);
CREATE INDEX IF NOT EXISTS idx_events_creator_user_id ON events (creator_user_id);
CREATE INDEX IF NOT EXISTS idx_events_starts_at ON events (starts_at);
CREATE INDEX IF NOT EXISTS idx_events_deleted_at ON events (deleted_at);

-- Auto-update updated_at (reuses set_updated_at() from V2)
DROP TRIGGER IF EXISTS trg_events_updated_at ON events;

CREATE TRIGGER trg_events_updated_at
    BEFORE UPDATE ON events
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
