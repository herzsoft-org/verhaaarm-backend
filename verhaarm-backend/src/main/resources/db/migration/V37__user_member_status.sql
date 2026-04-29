ALTER TABLE users
    ADD COLUMN IF NOT EXISTS member_status TEXT NOT NULL DEFAULT 'BURSCH';

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_member_status;

ALTER TABLE users
    ADD CONSTRAINT chk_users_member_status
        CHECK (member_status IN ('FUX', 'BURSCH', 'INAKTIVER', 'PHILISTER'));

CREATE INDEX IF NOT EXISTS idx_users_member_status ON users (member_status);