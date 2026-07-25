package moe.herz.verhaarmbackend.event.dto;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

/**
 * One atomic Convente-board batch: any mix of retyping/redating existing Convente, creating brand new
 * ones, and deleting existing ones. The server builds the complete resulting timeline and validates it
 * once, then applies every operation together or none at all. At least one list must be non-empty -
 * the service rejects an entirely empty batch.
 */
public record UpdateConventBoardRequest(
		@Valid List<ConventBoardChangeDto> changes,
		@Valid List<ConventBoardCreateDto> creates,
		List<UUID> deleteEventIds
) {
	public UpdateConventBoardRequest {
		changes = changes == null ? List.of() : changes;
		creates = creates == null ? List.of() : creates;
		deleteEventIds = deleteEventIds == null ? List.of() : deleteEventIds;
	}

	/** Convenience for the pre-existing changes-only shape (also used throughout the test suite). */
	public UpdateConventBoardRequest(List<ConventBoardChangeDto> changes) {
		this(changes, List.of(), List.of());
	}
}
