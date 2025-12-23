package moe.herz.verhaarmbackend.finephoto.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FinePhotoDto(
		UUID id,
		UUID fineId,
		String originalFilename,
		String contentType,
		long sizeBytes,
		OffsetDateTime createdAt
) {}
