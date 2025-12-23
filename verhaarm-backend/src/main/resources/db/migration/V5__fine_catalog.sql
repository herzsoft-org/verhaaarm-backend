-- Fine catalog (ADMIN-managed)
-- Used for predefined fines + attendance fines + suggestions.

CREATE TABLE IF NOT EXISTS fine_catalog_items (
                                                  id UUID PRIMARY KEY,
                                                  title TEXT NOT NULL,
                                                  default_amount_cents INT NOT NULL,
                                                  active BOOLEAN NOT NULL DEFAULT TRUE,
                                                  deleted_at TIMESTAMPTZ NULL,
                                                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_fine_catalog_amount_nonneg CHECK (default_amount_cents >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_fine_catalog_active ON fine_catalog_items (active);
CREATE INDEX IF NOT EXISTS idx_fine_catalog_deleted_at ON fine_catalog_items (deleted_at);

-- Auto-update updated_at (reuses set_updated_at() from V2)
DROP TRIGGER IF EXISTS trg_fine_catalog_items_updated_at ON fine_catalog_items;

CREATE TRIGGER trg_fine_catalog_items_updated_at
    BEFORE UPDATE ON fine_catalog_items
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
