-- Fines (core)
-- amount stored as integer cents (EUR)
-- soft delete via deleted_at
-- multi-target via fine_targets

CREATE TABLE IF NOT EXISTS fines (
                                     id UUID PRIMARY KEY,
                                     period_id UUID NOT NULL REFERENCES convent_periods(id),
    creator_user_id UUID NOT NULL REFERENCES users(id),

    -- optional: if created from catalog
    catalog_item_id UUID NULL REFERENCES fine_catalog_items(id),

    -- reason always stored on fine (even catalog-based) to preserve text at time of creation
    reason TEXT NOT NULL,

    amount_cents INT NOT NULL,
    type TEXT NOT NULL, -- CATALOG or CUSTOM

-- later: suggestions can populate this
    suggester_user_id UUID NULL REFERENCES users(id),

    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_fines_amount_nonneg CHECK (amount_cents >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_fines_period_id ON fines (period_id);
CREATE INDEX IF NOT EXISTS idx_fines_creator_user_id ON fines (creator_user_id);
CREATE INDEX IF NOT EXISTS idx_fines_deleted_at ON fines (deleted_at);

-- Exactly which users a fine targets
CREATE TABLE IF NOT EXISTS fine_targets (
                                            fine_id UUID NOT NULL REFERENCES fines(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (fine_id, user_id)
    );

CREATE INDEX IF NOT EXISTS idx_fine_targets_user_id ON fine_targets (user_id);

-- Auto-update updated_at (reuses set_updated_at() from V2)
DROP TRIGGER IF EXISTS trg_fines_updated_at ON fines;

CREATE TRIGGER trg_fines_updated_at
    BEFORE UPDATE ON fines
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
