-- Convent / Billing Periods
-- Exactly one active period at a time (DB-enforced via partial unique index).

CREATE TABLE IF NOT EXISTS convent_periods (
                                               id UUID PRIMARY KEY,
                                               semester TEXT NOT NULL,
                                               start_at TIMESTAMPTZ NOT NULL,
                                               end_at TIMESTAMPTZ NOT NULL,
                                               active BOOLEAN NOT NULL DEFAULT FALSE,
                                               locked BOOLEAN NOT NULL DEFAULT FALSE,
                                               created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_convent_periods_start_before_end CHECK (start_at < end_at)
    );

-- Usually there should not be duplicate semesters
CREATE UNIQUE INDEX IF NOT EXISTS uq_convent_periods_semester ON convent_periods (semester);

-- Exactly one active=true allowed.
CREATE UNIQUE INDEX IF NOT EXISTS uq_convent_periods_single_active
    ON convent_periods ((active))
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_convent_periods_active ON convent_periods (active);
CREATE INDEX IF NOT EXISTS idx_convent_periods_locked ON convent_periods (locked);
CREATE INDEX IF NOT EXISTS idx_convent_periods_start_at ON convent_periods (start_at);

-- Auto-update updated_at on convent_periods (reuses set_updated_at() from V2)
DROP TRIGGER IF EXISTS trg_convent_periods_updated_at ON convent_periods;

CREATE TRIGGER trg_convent_periods_updated_at
    BEFORE UPDATE ON convent_periods
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
