-- Allow hard-deleting fine_catalog_items without breaking historical fines.
-- Past fines keep their snapshot fields (reason/amount_cents); only catalog_item_id becomes NULL.

DO $$
BEGIN
  -- Drop the existing FK if it exists (Postgres default name from V6).
  IF EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fines_catalog_item_id_fkey'
  ) THEN
ALTER TABLE fines DROP CONSTRAINT fines_catalog_item_id_fkey;
END IF;

  -- Recreate FK with ON DELETE SET NULL so catalog items can be hard-deleted safely.
ALTER TABLE fines
    ADD CONSTRAINT fines_catalog_item_id_fkey
        FOREIGN KEY (catalog_item_id)
            REFERENCES fine_catalog_items(id)
            ON DELETE SET NULL;
END $$;
