package moe.herz.verhaarmbackend.fine.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record UpdateFineRequest(
		LocalDate fineDate,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		Set<UUID> targetUserIds
) {}