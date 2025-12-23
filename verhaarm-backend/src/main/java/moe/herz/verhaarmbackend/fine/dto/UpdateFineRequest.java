package moe.herz.verhaarmbackend.fine.dto;

import java.util.Set;
import java.util.UUID;

public record UpdateFineRequest(
		UUID periodId,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		Set<UUID> targetUserIds
) {}
