package moe.herz.verhaarmbackend.slushyrecipe.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RatingDto(
		UUID userId,
		String displayName,
		int stars,
		String comment,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
