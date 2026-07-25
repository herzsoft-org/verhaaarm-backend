-- A scheduled Event can optionally be a Convent (Anconvent/Convent/Abconvent, rule 3).
-- Semester and Conventsperioden are derived from these at read time (see ConventDerivation) -
-- nothing else needs to be stored.

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS convent_type VARCHAR(16);

ALTER TABLE events
    ADD CONSTRAINT chk_events_convent_type
        CHECK (convent_type IS NULL OR convent_type IN ('ANCONVENT', 'REGULAR', 'ABCONVENT'));

CREATE INDEX IF NOT EXISTS idx_events_convent_type
    ON events (convent_type)
    WHERE convent_type IS NOT NULL;
