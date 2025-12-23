package moe.herz.verhaarmbackend.fine.dto;

import moe.herz.verhaarmbackend.fine.FineType;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record FineDto(
		UUID id,
		UUID periodId,
		UUID creatorUserId,
		UUID catalogItemId,
		String reason,
		int amountCents,
		FineType type,
		Set<UUID> targetUserIds,
		OffsetDateTime createdAt,

		// suggestion metadata (nullable)
		UUID suggesterUserId,
		UUID acceptedFromSuggestionId
) {}
