-- Bring fine_photos schema in line with FinePhotoEntity:
-- - uploader_user_id is required
-- - created_at should exist and default to now()
-- - keep it safe for existing installs

-- 1) uploader_user_id
ALTER TABLE fine_photos
    ADD COLUMN IF NOT EXISTS uploader_user_id UUID;

-- For existing rows (if any), set to the fine creator as a reasonable default.
UPDATE fine_photos p
SET uploader_user_id = f.creator_user_id
    FROM fines f
WHERE p.uploader_user_id IS NULL
  AND p.fine_id = f.id;

-- Enforce NOT NULL + FK
ALTER TABLE fine_photos
    ALTER COLUMN uploader_user_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_fine_photos_uploader_user_id'
    ) THEN
ALTER TABLE fine_photos
    ADD CONSTRAINT fk_fine_photos_uploader_user_id
        FOREIGN KEY (uploader_user_id) REFERENCES users(id);
END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fine_photos_uploader_user_id
    ON fine_photos (uploader_user_id);

-- 2) created_at (if your V13 already has it, this is a no-op)
ALTER TABLE fine_photos
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
