package moe.herz.verhaarmbackend.period.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ConventPeriodDto(
		UUID id,
		String semester,
		LocalDate startAt,
		LocalDate endAt,
		boolean active,   // computed by backend now
		boolean locked
) {}
