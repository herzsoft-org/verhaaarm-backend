package moe.herz.verhaarmbackend.slushyrecipe.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SlushyRecipeDto(
		UUID id,
		String title,
		String description,
		List<IngredientDto> ingredients,
		UUID createdByUserId,
		String createdByDisplayName,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		RatingSummaryDto ratingSummary,
		List<RatingDto> ratings
) {}
