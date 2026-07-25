-- Events get a required location, defaulting to "adH" for existing rows.
-- Application code always supplies a value going forward (defaulting blank/omitted to "adH"
-- itself), so no DB-level default is needed beyond this one-time backfill.

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS location TEXT;

UPDATE events
SET location = 'adH'
WHERE location IS NULL;

ALTER TABLE events
    ALTER COLUMN location SET NOT NULL;
