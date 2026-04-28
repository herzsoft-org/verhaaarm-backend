CREATE TABLE IF NOT EXISTS convent_period_protocols (
                                                        id UUID PRIMARY KEY,
                                                        period_id UUID NOT NULL UNIQUE REFERENCES convent_periods(id) ON DELETE CASCADE,
                                                        uploader_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
                                                        original_filename TEXT NOT NULL,
                                                        stored_filename TEXT NOT NULL,
                                                        content_type TEXT NOT NULL,
                                                        size_bytes BIGINT NOT NULL,
                                                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_convent_period_protocols_period_id
    ON convent_period_protocols(period_id);

CREATE INDEX IF NOT EXISTS idx_convent_period_protocols_uploader_user_id
    ON convent_period_protocols(uploader_user_id);

DROP TRIGGER IF EXISTS trg_convent_period_protocols_updated_at ON convent_period_protocols;

CREATE TRIGGER trg_convent_period_protocols_updated_at
    BEFORE UPDATE ON convent_period_protocols
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();