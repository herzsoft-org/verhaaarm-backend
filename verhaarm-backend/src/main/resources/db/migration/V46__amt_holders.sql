-- Ämter (offices) manual holder assignments.
-- amt_type is a fixed application-level enum (AmtType), not a separate lookup table.
-- Multiple holders per amt_type are allowed (legitimate handover overlap / data corrections).

CREATE TABLE IF NOT EXISTS amt_holders (
    id UUID PRIMARY KEY,

    amt_type VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_amt_holders_type_user UNIQUE (amt_type, user_id)
);

CREATE INDEX IF NOT EXISTS idx_amt_holders_amt_type ON amt_holders (amt_type);
CREATE INDEX IF NOT EXISTS idx_amt_holders_user_id ON amt_holders (user_id);
