package moe.herz.verhaarmbackend.ferienvertreter.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateFerienvertreterRequest(
		@NotNull UUID userId,
		@NotNull LocalDate fromDate,
		@NotNull LocalDate untilDate
) {}
