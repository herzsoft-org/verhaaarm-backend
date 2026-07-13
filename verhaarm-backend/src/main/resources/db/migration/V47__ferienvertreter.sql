-- Ferienvertreter: who covers office duties during a semester break, as a plain
-- date range (no overlap checks; multiple entries may cover the same period).

CREATE TABLE IF NOT EXISTS ferienvertreter (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    from_date DATE NOT NULL,
    until_date DATE NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ferienvertreter_from_date ON ferienvertreter (from_date);
CREATE INDEX IF NOT EXISTS idx_ferienvertreter_user_id ON ferienvertreter (user_id);

-- Auto-update updated_at (reuses set_updated_at() from V2)
DROP TRIGGER IF EXISTS trg_ferienvertreter_updated_at ON ferienvertreter;

CREATE TRIGGER trg_ferienvertreter_updated_at
    BEFORE UPDATE ON ferienvertreter
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
