package moe.herz.verhaarmbackend.finesuggestion.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CreateFineSuggestionRequest(
		@NotNull LocalDate fineDate,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		@NotNull Set<UUID> targetUserIds
) {}
