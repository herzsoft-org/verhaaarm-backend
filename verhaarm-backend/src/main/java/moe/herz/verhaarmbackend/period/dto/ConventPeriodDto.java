package moe.herz.verhaarmbackend.period.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConventPeriodDto(
		UUID id,
		String semester,
		OffsetDateTime startAt,
		OffsetDateTime endAt,
		boolean active,
		boolean locked
) {}
