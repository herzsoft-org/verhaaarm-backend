-- Ensure user-owned state is removed automatically when a user is hard-deleted.
-- This migration does not depend on existing constraint names.

DO $$
    DECLARE
        fk_name text;
    BEGIN
        -- --------------------------------------------------------------------------
        -- task_assignees(user_id) -> users(id) ON DELETE CASCADE
        -- --------------------------------------------------------------------------
        SELECT c.conname INTO fk_name
        FROM pg_constraint c
                 JOIN pg_class t ON t.oid = c.conrelid
                 JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND t.relname = 'task_assignees'
          AND c.confrelid = 'users'::regclass
          AND pg_get_constraintdef(c.oid) LIKE '%FOREIGN KEY (user_id)%REFERENCES users(id)%';

        IF fk_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE task_assignees DROP CONSTRAINT %I', fk_name);
        END IF;

        ALTER TABLE task_assignees
            ADD CONSTRAINT task_assignees_user_id_fkey
                FOREIGN KEY (user_id) REFERENCES users(id)
                    ON DELETE CASCADE;

        -- --------------------------------------------------------------------------
        -- refresh_tokens(user_id) -> users(id) ON DELETE CASCADE
        -- --------------------------------------------------------------------------
        SELECT c.conname INTO fk_name
        FROM pg_constraint c
                 JOIN pg_class t ON t.oid = c.conrelid
                 JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND t.relname = 'refresh_tokens'
          AND c.confrelid = 'users'::regclass
          AND pg_get_constraintdef(c.oid) LIKE '%FOREIGN KEY (user_id)%REFERENCES users(id)%';

        IF fk_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE refresh_tokens DROP CONSTRAINT %I', fk_name);
        END IF;

        ALTER TABLE refresh_tokens
            ADD CONSTRAINT refresh_tokens_user_id_fkey
                FOREIGN KEY (user_id) REFERENCES users(id)
                    ON DELETE CASCADE;

        -- --------------------------------------------------------------------------
        -- push_devices(user_id) -> users(id) ON DELETE CASCADE
        -- --------------------------------------------------------------------------
        SELECT c.conname INTO fk_name
        FROM pg_constraint c
                 JOIN pg_class t ON t.oid = c.conrelid
                 JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND t.relname = 'push_devices'
          AND c.confrelid = 'users'::regclass
          AND pg_get_constraintdef(c.oid) LIKE '%FOREIGN KEY (user_id)%REFERENCES users(id)%';

        IF fk_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE push_devices DROP CONSTRAINT %I', fk_name);
        END IF;

        ALTER TABLE push_devices
            ADD CONSTRAINT push_devices_user_id_fkey
                FOREIGN KEY (user_id) REFERENCES users(id)
                    ON DELETE CASCADE;

    END $$;
