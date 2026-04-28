package moe.herz.verhaarmbackend.periodprotocol.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConventPeriodProtocolDto(
		UUID id,
		UUID periodId,
		UUID uploaderUserId,
		String originalFilename,
		String contentType,
		long sizeBytes,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
