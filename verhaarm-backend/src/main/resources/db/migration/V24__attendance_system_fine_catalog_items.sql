-- Two protected/system fine catalog items used for attendance automation.
-- Fixed UUIDs so code can reference them deterministically.

INSERT INTO fine_catalog_items (id, title, default_amount_cents, active, deleted_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Verspätung', 100, TRUE, NULL),
    ('22222222-2222-2222-2222-222222222222', 'Abwesenheit', 100, TRUE, NULL)
    ON CONFLICT (id) DO NOTHING;

-- If they existed but were soft-deleted before, revive them.
UPDATE fine_catalog_items
SET deleted_at = NULL, active = TRUE
WHERE id IN (
             '11111111-1111-1111-1111-111111111111',
             '22222222-2222-2222-2222-222222222222'
    );
