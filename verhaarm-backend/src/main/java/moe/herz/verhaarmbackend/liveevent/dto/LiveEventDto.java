package moe.herz.verhaarmbackend.liveevent.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LiveEventDto(
		UUID id,
		String title,
		String place,
		String description,
		UUID createdByUserId,
		OffsetDateTime createdAt,
		OffsetDateTime expiresAt,
		LiveEventReactionSummaryDto reactions,
		LiveEventReactionUsersDto reactionUsers
) {}
