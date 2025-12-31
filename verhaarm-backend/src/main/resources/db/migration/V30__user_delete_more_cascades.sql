-- V30__user_delete_more_cascades.sql
-- Expand user hard-delete behaviour.
-- CASCADE for user-owned state
-- SET NULL for "created_by"/"uploader"/"actor" so records survive

-- ----------------------------
-- 1) SET NULL columns must be nullable
-- ----------------------------
ALTER TABLE tasks            ALTER COLUMN creator_user_id     DROP NOT NULL;
ALTER TABLE audit_log        ALTER COLUMN actor_user_id       DROP NOT NULL;
ALTER TABLE events           ALTER COLUMN creator_user_id     DROP NOT NULL;
ALTER TABLE fine_photos      ALTER COLUMN uploader_user_id    DROP NOT NULL;
ALTER TABLE fine_suggestions ALTER COLUMN creator_user_id     DROP NOT NULL;
ALTER TABLE fines            ALTER COLUMN creator_user_id     DROP NOT NULL;
ALTER TABLE live_events      ALTER COLUMN created_by_user_id  DROP NOT NULL;

-- fine_suggestions.decided_by_user_id is already nullable in your schema, so this is optional:
ALTER TABLE fine_suggestions ALTER COLUMN decided_by_user_id  DROP NOT NULL;

-- fines.suggester_user_id is already nullable in your schema, so this is optional:
ALTER TABLE fines            ALTER COLUMN suggester_user_id   DROP NOT NULL;

-- ----------------------------
-- 2) CASCADE: user-owned state
-- ----------------------------
ALTER TABLE attendance DROP CONSTRAINT IF EXISTS attendance_user_id_fkey;
ALTER TABLE attendance
    ADD CONSTRAINT attendance_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_user_id_fkey;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE fine_targets DROP CONSTRAINT IF EXISTS fine_targets_user_id_fkey;
ALTER TABLE fine_targets
    ADD CONSTRAINT fine_targets_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE fine_suggestion_targets DROP CONSTRAINT IF EXISTS fine_suggestion_targets_user_id_fkey;
ALTER TABLE fine_suggestion_targets
    ADD CONSTRAINT fine_suggestion_targets_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_user_id_fkey;
ALTER TABLE user_roles
    ADD CONSTRAINT user_roles_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Already CASCADE in your DB, but harmless to keep consistent:
ALTER TABLE task_assignees DROP CONSTRAINT IF EXISTS task_assignees_user_id_fkey;
ALTER TABLE task_assignees
    ADD CONSTRAINT task_assignees_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE push_devices DROP CONSTRAINT IF EXISTS push_devices_user_id_fkey;
ALTER TABLE push_devices
    ADD CONSTRAINT push_devices_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ----------------------------
-- 3) SET NULL: records survive user deletion
-- ----------------------------
-- Tasks must survive:
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_creator_user_id_fkey;
ALTER TABLE tasks
    ADD CONSTRAINT tasks_creator_user_id_fkey
        FOREIGN KEY (creator_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- You said audit log can be ignored; SET NULL is simplest:
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS fk_audit_log_actor_user;
ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE events DROP CONSTRAINT IF EXISTS events_creator_user_id_fkey;
ALTER TABLE events
    ADD CONSTRAINT events_creator_user_id_fkey
        FOREIGN KEY (creator_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE fine_photos DROP CONSTRAINT IF EXISTS fk_fine_photos_uploader_user_id;
ALTER TABLE fine_photos
    ADD CONSTRAINT fk_fine_photos_uploader_user_id
        FOREIGN KEY (uploader_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE fine_suggestions DROP CONSTRAINT IF EXISTS fine_suggestions_creator_user_id_fkey;
ALTER TABLE fine_suggestions
    ADD CONSTRAINT fine_suggestions_creator_user_id_fkey
        FOREIGN KEY (creator_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE fine_suggestions DROP CONSTRAINT IF EXISTS fine_suggestions_decided_by_user_id_fkey;
ALTER TABLE fine_suggestions
    ADD CONSTRAINT fine_suggestions_decided_by_user_id_fkey
        FOREIGN KEY (decided_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE fines DROP CONSTRAINT IF EXISTS fines_creator_user_id_fkey;
ALTER TABLE fines
    ADD CONSTRAINT fines_creator_user_id_fkey
        FOREIGN KEY (creator_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE fines DROP CONSTRAINT IF EXISTS fines_suggester_user_id_fkey;
ALTER TABLE fines
    ADD CONSTRAINT fines_suggester_user_id_fkey
        FOREIGN KEY (suggester_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE live_events DROP CONSTRAINT IF EXISTS live_events_created_by_user_id_fkey;
ALTER TABLE live_events
    ADD CONSTRAINT live_events_created_by_user_id_fkey
        FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
