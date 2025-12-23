-- Attendance fine generation: DB-level safety net
-- Ensure a fine can only be linked to one attendance row (when fine_id is set).

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_fine_id_not_null
    ON attendance (fine_id)
    WHERE fine_id IS NOT NULL;
