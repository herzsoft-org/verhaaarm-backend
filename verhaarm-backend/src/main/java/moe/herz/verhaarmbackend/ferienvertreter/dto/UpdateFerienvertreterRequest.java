package moe.herz.verhaarmbackend.ferienvertreter.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateFerienvertreterRequest(
		UUID userId,
		LocalDate fromDate,
		LocalDate untilDate
) {}
