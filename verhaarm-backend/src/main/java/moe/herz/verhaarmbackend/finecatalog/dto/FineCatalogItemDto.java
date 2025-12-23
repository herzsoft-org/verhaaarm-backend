package moe.herz.verhaarmbackend.finecatalog.dto;

import java.util.UUID;

public record FineCatalogItemDto(
		UUID id,
		String title,
		int defaultAmountCents,
		boolean active
) {}
