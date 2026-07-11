package moe.herz.verhaarmbackend.slushyrecipe.dto;

import java.util.List;

public record UpdateSlushyRecipeRequest(
		String title,
		String description,
		List<IngredientRequest> ingredients
) {}
