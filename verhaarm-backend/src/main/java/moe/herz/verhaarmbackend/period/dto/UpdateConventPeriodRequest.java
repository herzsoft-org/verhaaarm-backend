package moe.herz.verhaarmbackend.period.dto;

import java.time.LocalDate;

public record UpdateConventPeriodRequest(
		String semester,
		LocalDate startAt,
		LocalDate endAt,
		Boolean locked
) {}
