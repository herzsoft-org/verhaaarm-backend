ALTER TABLE events
    ADD COLUMN event_kind varchar(32);

UPDATE events
SET event_kind = 'MAIN'
WHERE event_kind IS NULL;

ALTER TABLE events
    ALTER COLUMN event_kind SET NOT NULL;