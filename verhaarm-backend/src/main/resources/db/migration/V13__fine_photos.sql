-- Fine photos (0..N per fine), stored on disk, soft delete in DB.

CREATE TABLE IF NOT EXISTS fine_photos (
                                           id UUID PRIMARY KEY,
                                           fine_id UUID NOT NULL REFERENCES fines(id) ON DELETE CASCADE,

    -- original client filename (for Content-Disposition)
    original_filename TEXT NOT NULL,

    -- stored filename on disk (UUID-based, no path traversal risk)
    stored_filename TEXT NOT NULL UNIQUE,

    content_type TEXT NULL,
    size_bytes BIGINT NOT NULL,

    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_fine_photos_fine_id ON fine_photos (fine_id);
CREATE INDEX IF NOT EXISTS idx_fine_photos_deleted_at ON fine_photos (deleted_at);