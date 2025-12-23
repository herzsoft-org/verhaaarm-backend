-- Enforce uniqueness of username_normalized (race-safe, spec requirement)

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_normalized
    ON users (username_normalized);
