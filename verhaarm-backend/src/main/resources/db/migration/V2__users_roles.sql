-- Users + Roles (identity foundation)
-- Amounts, fines, events etc. come later.
-- This migration is intentionally minimal but future-proof.

CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     username TEXT NOT NULL UNIQUE,
                                     display_name TEXT NOT NULL,
                                     username_normalized TEXT NOT NULL,
                                     password_hash TEXT NOT NULL,
                                     disabled BOOLEAN NOT NULL DEFAULT FALSE,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_users_username_normalized ON users (username_normalized);
CREATE INDEX IF NOT EXISTS idx_users_disabled ON users (disabled);

CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id UUID NOT NULL REFERENCES users(id),
    role TEXT NOT NULL,
    PRIMARY KEY (user_id, role)
    );

CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles (role);

-- Auto-update updated_at on users
-- (Postgres trigger)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
