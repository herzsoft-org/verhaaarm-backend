package moe.herz.verhaarmbackend.finesuggestion.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateFineSuggestionRequest(
		@NotNull UUID periodId,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		@NotNull Set<UUID> targetUserIds
) {}
