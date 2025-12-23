package moe.herz.verhaarmbackend.finecatalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateFineCatalogItemRequest(
		@NotBlank String title,
		@Min(0) int defaultAmountCents,
		Boolean active
) {}
