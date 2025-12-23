-- Fine suggestions
-- Members can suggest fines; SENIOR/HOUSEKEEPING can accept or reject.
-- Suggestions are not official fines until accepted.
-- Soft delete via deleted_at.

CREATE TABLE IF NOT EXISTS fine_suggestions (
                                                id UUID PRIMARY KEY,

                                                period_id UUID NOT NULL REFERENCES convent_periods(id),
    creator_user_id UUID NOT NULL REFERENCES users(id), -- the suggester

-- optional: if suggestion based on catalog item at the time
    catalog_item_id UUID NULL REFERENCES fine_catalog_items(id),

    -- reason always stored to preserve text at time of suggestion
    reason TEXT NOT NULL,

    amount_cents INT NOT NULL,
    type TEXT NOT NULL, -- CATALOG or CUSTOM

    status TEXT NOT NULL, -- PENDING / ACCEPTED / REJECTED

    decided_by_user_id UUID NULL REFERENCES users(id),
    decided_at TIMESTAMPTZ NULL,

    -- if accepted, link to the created official fine
    accepted_fine_id UUID NULL REFERENCES fines(id),

    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_fine_suggestions_amount_nonneg CHECK (amount_cents >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_fine_suggestions_period_id ON fine_suggestions (period_id);
CREATE INDEX IF NOT EXISTS idx_fine_suggestions_creator_user_id ON fine_suggestions (creator_user_id);
CREATE INDEX IF NOT EXISTS idx_fine_suggestions_status ON fine_suggestions (status);
CREATE INDEX IF NOT EXISTS idx_fine_suggestions_deleted_at ON fine_suggestions (deleted_at);

-- Multi-target for suggestions
CREATE TABLE IF NOT EXISTS fine_suggestion_targets (
                                                       suggestion_id UUID NOT NULL REFERENCES fine_suggestions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (suggestion_id, user_id)
    );

CREATE INDEX IF NOT EXISTS idx_fine_suggestion_targets_user_id ON fine_suggestion_targets (user_id);

-- Link official fines back to the suggestion they came from (optional but useful)
ALTER TABLE fines
    ADD COLUMN IF NOT EXISTS accepted_from_suggestion_id UUID NULL REFERENCES fine_suggestions(id);

CREATE INDEX IF NOT EXISTS idx_fines_accepted_from_suggestion_id ON fines (accepted_from_suggestion_id);

-- Auto-update updated_at (reuses set_updated_at() from V2)
DROP TRIGGER IF EXISTS trg_fine_suggestions_updated_at ON fine_suggestions;

CREATE TRIGGER trg_fine_suggestions_updated_at
    BEFORE UPDATE ON fine_suggestions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
