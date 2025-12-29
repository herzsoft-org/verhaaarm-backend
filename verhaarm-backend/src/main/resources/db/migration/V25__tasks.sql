-- Tasks / Arbeitsaufträge
-- Global solved + global delete.
-- Multi-assignee via task_assignees.

CREATE TABLE IF NOT EXISTS tasks (
                                     id UUID PRIMARY KEY,

                                     creator_user_id UUID NOT NULL REFERENCES users(id),

    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',

    solved BOOLEAN NOT NULL DEFAULT FALSE,
    solved_at TIMESTAMPTZ NULL,

    deleted_at TIMESTAMPTZ NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_tasks_deleted_at ON tasks (deleted_at);
CREATE INDEX IF NOT EXISTS idx_tasks_solved_deleted_at ON tasks (solved, deleted_at);
CREATE INDEX IF NOT EXISTS idx_tasks_creator_user_id ON tasks (creator_user_id);

-- Multi-assignee mapping
CREATE TABLE IF NOT EXISTS task_assignees (
                                              task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (task_id, user_id)
    );

CREATE INDEX IF NOT EXISTS idx_task_assignees_user_id ON task_assignees (user_id);
CREATE INDEX IF NOT EXISTS idx_task_assignees_task_id ON task_assignees (task_id);

-- Auto-update updated_at (reuses set_updated_at() from V2)
DROP TRIGGER IF EXISTS trg_tasks_updated_at ON tasks;

CREATE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
