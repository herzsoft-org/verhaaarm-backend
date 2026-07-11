package moe.herz.verhaarmbackend.slushyrecipe.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateSlushyRecipeRequest(
		@NotBlank String title,
		String description,
		List<IngredientRequest> ingredients
) {}
