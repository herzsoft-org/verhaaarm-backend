CREATE TABLE IF NOT EXISTS fine_suggestion_photos (
                                                      id UUID PRIMARY KEY,
                                                      suggestion_id UUID NOT NULL REFERENCES fine_suggestions(id) ON DELETE CASCADE,
                                                      uploader_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
                                                      original_filename TEXT NOT NULL,
                                                      stored_filename TEXT NOT NULL UNIQUE,
                                                      content_type TEXT NOT NULL,
                                                      size_bytes BIGINT NOT NULL,
                                                      deleted_at TIMESTAMPTZ NULL,
                                                      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fine_suggestion_photos_suggestion_id
    ON fine_suggestion_photos (suggestion_id);

CREATE INDEX IF NOT EXISTS idx_fine_suggestion_photos_deleted_at
    ON fine_suggestion_photos (deleted_at);

CREATE INDEX IF NOT EXISTS idx_fine_suggestion_photos_uploader_user_id
    ON fine_suggestion_photos (uploader_user_id);