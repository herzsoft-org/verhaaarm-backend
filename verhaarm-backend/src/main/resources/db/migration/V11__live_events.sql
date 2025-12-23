-- Live "Spending Time Together" events
-- Not linked to convent periods.
-- Auto-expire after 6 hours (enforced by query + expires_at).
-- Soft delete via deleted_at.

CREATE TABLE IF NOT EXISTS live_events (
                                           id UUID PRIMARY KEY,

                                           title TEXT NOT NULL,
                                           place TEXT NOT NULL,
                                           description TEXT NOT NULL,

                                           created_by_user_id UUID NOT NULL REFERENCES users(id),

    expires_at TIMESTAMPTZ NOT NULL,

    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_live_events_created_by_user_id ON live_events (created_by_user_id);
CREATE INDEX IF NOT EXISTS idx_live_events_expires_at ON live_events (expires_at);
CREATE INDEX IF NOT EXISTS idx_live_events_deleted_at ON live_events (deleted_at);

DROP TRIGGER IF EXISTS trg_live_events_updated_at ON live_events;
CREATE TRIGGER trg_live_events_updated_at
    BEFORE UPDATE ON live_events
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
