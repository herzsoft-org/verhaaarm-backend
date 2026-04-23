public record UpdateFineSuggestionRequest(
		LocalDate fineDate,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		Set<UUID> targetUserIds
) {}