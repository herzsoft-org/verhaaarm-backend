-- Notifications (persistent, per-user)
CREATE TABLE IF NOT EXISTS notifications (
                                             id UUID PRIMARY KEY,
                                             user_id UUID NOT NULL REFERENCES users(id),

    type TEXT NOT NULL,                 -- e.g. FINE_CREATED, TASK_ASSIGNED
    title TEXT NOT NULL,
    body  TEXT NOT NULL,

    data JSONB NOT NULL DEFAULT '{}'::jsonb,

    read_at TIMESTAMPTZ NULL,
    deleted_at TIMESTAMPTZ NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_notifications_user_id_created_at
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id_read_at
    ON notifications (user_id, read_at);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id_deleted_at
    ON notifications (user_id, deleted_at);

DROP TRIGGER IF EXISTS trg_notifications_updated_at ON notifications;

CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();


-- Push devices/tokens (both WebPush and FCM)
CREATE TABLE IF NOT EXISTS push_devices (
                                            id UUID PRIMARY KEY,
                                            user_id UUID NOT NULL REFERENCES users(id),

    kind TEXT NOT NULL, -- WEBPUSH or FCM

-- WEBPUSH fields
    endpoint TEXT NULL,
    p256dh   TEXT NULL,
    auth     TEXT NULL,
    subscription_json JSONB NULL,

    -- FCM fields
    fcm_token TEXT NULL,

    user_agent TEXT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_push_devices_user_id_kind
    ON push_devices (user_id, kind);

-- De-duplicate registrations
CREATE UNIQUE INDEX IF NOT EXISTS uq_push_devices_webpush_endpoint
    ON push_devices (kind, endpoint)
    WHERE kind = 'WEBPUSH' AND endpoint IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_push_devices_fcm_token
    ON push_devices (kind, fcm_token)
    WHERE kind = 'FCM' AND fcm_token IS NOT NULL;
