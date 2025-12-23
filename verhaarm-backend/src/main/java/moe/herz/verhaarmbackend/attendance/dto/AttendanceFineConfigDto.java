package moe.herz.verhaarmbackend.attendance.dto;

import java.util.UUID;

public record AttendanceFineConfigDto(
		UUID periodId,

		UUID lateCatalogItemId,
		String lateReason,
		Integer lateAmountCents,

		UUID absentCatalogItemId,
		String absentReason,
		Integer absentAmountCents
) {}
