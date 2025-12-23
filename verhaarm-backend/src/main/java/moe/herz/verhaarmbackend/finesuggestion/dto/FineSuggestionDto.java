package moe.herz.verhaarmbackend.finesuggestion.dto;

import moe.herz.verhaarmbackend.fine.FineType;
import moe.herz.verhaarmbackend.finesuggestion.FineSuggestionStatus;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record FineSuggestionDto(
		UUID id,
		UUID periodId,
		UUID creatorUserId,
		UUID catalogItemId,
		String reason,
		int amountCents,
		FineType type,
		FineSuggestionStatus status,
		UUID decidedByUserId,
		OffsetDateTime decidedAt,
		UUID acceptedFineId,
		Set<UUID> targetUserIds,
		OffsetDateTime createdAt
) {}
