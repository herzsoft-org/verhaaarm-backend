CREATE TABLE IF NOT EXISTS live_event_reactions (
    id UUID PRIMARY KEY,
    live_event_id UUID NOT NULL REFERENCES live_events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT live_event_reactions_type_check
        CHECK (type IN ('PROST', 'ICH_KOMME')),
    CONSTRAINT uq_live_event_reactions_event_user_type
        UNIQUE (live_event_id, user_id, type)
);

CREATE INDEX IF NOT EXISTS idx_live_event_reactions_live_event_id_type
    ON live_event_reactions (live_event_id, type);

CREATE INDEX IF NOT EXISTS idx_live_event_reactions_user_id
    ON live_event_reactions (user_id);
