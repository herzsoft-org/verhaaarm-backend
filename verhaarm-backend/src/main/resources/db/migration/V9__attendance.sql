-- Attendance: store only exceptions (LATE/ABSENT).
-- Default assumption: PRESENT / excused => no row.
-- Config per period for late/absent fines (catalog-based or custom), stored to preserve history.

CREATE TABLE IF NOT EXISTS attendance_fine_config (
                                                      period_id UUID PRIMARY KEY REFERENCES convent_periods(id),

    late_catalog_item_id UUID NULL REFERENCES fine_catalog_items(id),
    late_reason TEXT NULL,
    late_amount_cents INT NULL,

    absent_catalog_item_id UUID NULL REFERENCES fine_catalog_items(id),
    absent_reason TEXT NULL,
    absent_amount_cents INT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_att_cfg_late_nonneg CHECK (late_amount_cents IS NULL OR late_amount_cents >= 0),
    CONSTRAINT chk_att_cfg_absent_nonneg CHECK (absent_amount_cents IS NULL OR absent_amount_cents >= 0)
    );

DROP TRIGGER IF EXISTS trg_attendance_fine_config_updated_at ON attendance_fine_config;
CREATE TRIGGER trg_attendance_fine_config_updated_at
    BEFORE UPDATE ON attendance_fine_config
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();


CREATE TABLE IF NOT EXISTS attendance (
                                          id UUID PRIMARY KEY,
                                          event_id UUID NOT NULL REFERENCES events(id),
    period_id UUID NOT NULL REFERENCES convent_periods(id),

    user_id UUID NOT NULL REFERENCES users(id),

    status TEXT NOT NULL, -- LATE / ABSENT
    late_minutes INT NULL, -- required for LATE

    fine_id UUID NULL REFERENCES fines(id), -- set when generated

    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_attendance_event_user UNIQUE (event_id, user_id),
    CONSTRAINT chk_attendance_late_minutes CHECK (late_minutes IS NULL OR late_minutes >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_attendance_event_id ON attendance (event_id);
CREATE INDEX IF NOT EXISTS idx_attendance_period_id ON attendance (period_id);
CREATE INDEX IF NOT EXISTS idx_attendance_user_id ON attendance (user_id);
CREATE INDEX IF NOT EXISTS idx_attendance_fine_id ON attendance (fine_id);
CREATE INDEX IF NOT EXISTS idx_attendance_deleted_at ON attendance (deleted_at);

DROP TRIGGER IF EXISTS trg_attendance_updated_at ON attendance;
CREATE TRIGGER trg_attendance_updated_at
    BEFORE UPDATE ON attendance
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
