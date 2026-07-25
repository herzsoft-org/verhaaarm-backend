package moe.herz.verhaarmbackend.period.dto;

import moe.herz.verhaarmbackend.event.ConventType;
import moe.herz.verhaarmbackend.period.PeriodType;

import java.time.LocalDate;
import java.util.UUID;

public record ConventPeriodDto(
		UUID id,                            // == the ending convent's event id; null only when periodType == OPEN
		String semester,                     // e.g. "WS25/26"; null only for an OPEN period sitting in Semesterferien
		LocalDate startAt,                    // inclusive; null only for the very first period ever
		LocalDate endAt,                       // inclusive; null only when periodType == OPEN
		boolean active,
		boolean hasProtocolPdf,
		PeriodType periodType,
		ConventType endingConventType,          // null when periodType == OPEN
		String endingConventLabel,                // null when periodType == OPEN
		boolean consistent,
		String warning
) {}
