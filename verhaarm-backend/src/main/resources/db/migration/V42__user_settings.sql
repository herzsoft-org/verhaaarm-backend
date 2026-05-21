CREATE TABLE IF NOT EXISTS user_settings (
                                             id UUID PRIMARY KEY,
                                             user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                             setting_key TEXT NOT NULL,
                                             setting_value TEXT NOT NULL,
                                             updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                             CONSTRAINT uq_user_settings_user_key UNIQUE (user_id, setting_key),
                                             CONSTRAINT chk_user_settings_key_length CHECK (length(setting_key) BETWEEN 1 AND 120),
                                             CONSTRAINT chk_user_settings_value_length CHECK (length(setting_value) <= 4000)
);

CREATE INDEX IF NOT EXISTS idx_user_settings_user_id
    ON user_settings (user_id);

CREATE INDEX IF NOT EXISTS idx_user_settings_updated_at
    ON user_settings (updated_at);