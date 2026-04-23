package moe.herz.verhaarmbackend.finesuggestion.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record UpdateFineSuggestionRequest(
		LocalDate fineDate,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		Set<UUID> targetUserIds
) {}