package moe.herz.verhaarmbackend.paukstunde.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PaukstundeDto(
		UUID id,
		LocalDate date,
		int hours,
		List<PaukstundeParticipantDto> participants,
		UUID createdByUserId,
		String createdByDisplayName,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
