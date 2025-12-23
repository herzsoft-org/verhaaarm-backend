-- Fix wrong type for actor_user_id (was BIGINT, must be UUID)
-- We drop and recreate the column because existing BIGINT values cannot be converted safely.
ALTER TABLE audit_log
DROP COLUMN IF EXISTS actor_user_id;

ALTER TABLE audit_log
    ADD COLUMN actor_user_id UUID;

ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users(id);

CREATE INDEX IF NOT EXISTS ix_audit_log_created_at ON audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_log_actor_user_id ON audit_log (actor_user_id);
CREATE INDEX IF NOT EXISTS ix_audit_log_action ON audit_log (action);
