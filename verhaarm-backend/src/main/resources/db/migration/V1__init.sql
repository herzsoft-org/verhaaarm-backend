CREATE TABLE audit_log (
                           id BIGSERIAL PRIMARY KEY,
                           created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                           actor_user_id BIGINT,
                           action TEXT NOT NULL,
                           details JSONB NOT NULL
);
