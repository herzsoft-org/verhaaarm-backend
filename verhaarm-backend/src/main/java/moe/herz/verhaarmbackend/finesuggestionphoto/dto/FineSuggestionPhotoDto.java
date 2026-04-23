package moe.herz.verhaarmbackend.finesuggestionphoto.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FineSuggestionPhotoDto(
		UUID id,
		UUID suggestionId,
		String originalFilename,
		String contentType,
		long sizeBytes,
		OffsetDateTime createdAt
) {}