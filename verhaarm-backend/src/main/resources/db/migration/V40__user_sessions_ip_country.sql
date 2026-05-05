ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS ip_address TEXT,
    ADD COLUMN IF NOT EXISTS country_code TEXT;

CREATE INDEX IF NOT EXISTS idx_user_sessions_ip_address
    ON user_sessions (ip_address);

CREATE INDEX IF NOT EXISTS idx_user_sessions_country_code
    ON user_sessions (country_code);