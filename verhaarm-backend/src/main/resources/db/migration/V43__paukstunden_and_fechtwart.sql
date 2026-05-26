-- Add the Fechtwart role and Paukstunden training entries.
-- Roles are stored as TEXT in user_roles, so no enum/table migration is needed for FECHTWART.

CREATE TABLE IF NOT EXISTS paukstunden (
                                           id UUID PRIMARY KEY,
                                           training_date DATE NOT NULL,
                                           hours INTEGER NOT NULL,
                                           created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                           updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                           CONSTRAINT chk_paukstunden_hours_positive CHECK (hours > 0)
);

CREATE INDEX IF NOT EXISTS idx_paukstunden_training_date
    ON paukstunden (training_date);

CREATE INDEX IF NOT EXISTS idx_paukstunden_created_by_user_id
    ON paukstunden (created_by_user_id);

DROP TRIGGER IF EXISTS trg_paukstunden_updated_at ON paukstunden;
CREATE TRIGGER trg_paukstunden_updated_at
    BEFORE UPDATE ON paukstunden
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS paukstunde_participants (
                                                       paukstunde_id UUID NOT NULL REFERENCES paukstunden(id) ON DELETE CASCADE,
                                                       user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                                       PRIMARY KEY (paukstunde_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_paukstunde_participants_user_id
    ON paukstunde_participants (user_id);
