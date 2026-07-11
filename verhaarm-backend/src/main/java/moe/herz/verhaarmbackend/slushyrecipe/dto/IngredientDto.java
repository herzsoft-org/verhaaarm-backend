package moe.herz.verhaarmbackend.slushyrecipe.dto;

import java.util.UUID;

public record IngredientDto(UUID id, String name, String amount) {}
