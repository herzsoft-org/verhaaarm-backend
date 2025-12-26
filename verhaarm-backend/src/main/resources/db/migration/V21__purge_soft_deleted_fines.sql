-- Purge soft-deleted fines now that fines are hard-deleted by the API.
-- Note: SQL cannot remove files on disk. Clean orphaned dirs separately.

-- fine_targets first (ElementCollection table)
DELETE FROM fine_targets
WHERE fine_id IN (SELECT id FROM fines WHERE deleted_at IS NOT NULL);

-- fine_photos (not strictly necessary because fine_photos has ON DELETE CASCADE from fines,
-- but harmless and ensures cleanup even if constraints differ)
DELETE FROM fine_photos
WHERE fine_id IN (SELECT id FROM fines WHERE deleted_at IS NOT NULL);

-- fines
DELETE FROM fines
WHERE deleted_at IS NOT NULL;
