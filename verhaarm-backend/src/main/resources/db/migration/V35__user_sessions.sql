ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_online_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_last_online_at
    ON users (last_online_at);

CREATE TABLE IF NOT EXISTS user_sessions (
                                             id UUID PRIMARY KEY,
                                             user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

                                             app_type TEXT NOT NULL DEFAULT 'UNKNOWN',

                                             device_name TEXT,
                                             device_model TEXT,
                                             os_name TEXT,
                                             os_version TEXT,
                                             browser_name TEXT,
                                             browser_version TEXT,
                                             user_agent TEXT,

                                             created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                             last_active_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                             expires_at TIMESTAMPTZ,
                                             revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id
    ON user_sessions (user_id);

CREATE INDEX IF NOT EXISTS idx_user_sessions_last_active_at
    ON user_sessions (last_active_at);

CREATE INDEX IF NOT EXISTS idx_user_sessions_app_type
    ON user_sessions (app_type);

CREATE INDEX IF NOT EXISTS idx_user_sessions_revoked_at
    ON user_sessions (revoked_at);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS session_id UUID;

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'refresh_tokens_session_id_fkey'
        ) THEN
            ALTER TABLE refresh_tokens
                ADD CONSTRAINT refresh_tokens_session_id_fkey
                    FOREIGN KEY (session_id)
                        REFERENCES user_sessions(id)
                        ON DELETE CASCADE;
        END IF;
    END $$;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_session_id
    ON refresh_tokens (session_id);