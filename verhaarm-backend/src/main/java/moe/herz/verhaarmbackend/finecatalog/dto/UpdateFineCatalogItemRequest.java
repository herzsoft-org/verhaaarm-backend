package moe.herz.verhaarmbackend.finecatalog.dto;

public record UpdateFineCatalogItemRequest(
		String title,
		Integer defaultAmountCents,
		Boolean active
) {}
