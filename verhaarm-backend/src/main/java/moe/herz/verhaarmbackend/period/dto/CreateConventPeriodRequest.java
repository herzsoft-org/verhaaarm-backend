package moe.herz.verhaarmbackend.period.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateConventPeriodRequest(
		@NotBlank String semester,
		@NotNull LocalDate startAt,
		@NotNull LocalDate endAt
) {}
