package moe.herz.verhaarmbackend.liveevent;

import moe.herz.verhaarmbackend.common.ApiErrors;

public enum LiveEventReactionType {
	PROST,
	ICH_KOMME;

	public static LiveEventReactionType fromPath(String raw) {
		if (raw == null || raw.isBlank()) throw ApiErrors.badRequest("Reaction type required");

		String normalized = raw.trim().toUpperCase();
		return switch (normalized) {
			case "PROST" -> PROST;
			case "ICH_KOMME" -> ICH_KOMME;
			default -> throw ApiErrors.badRequest("Unsupported reaction type");
		};
	}
}
