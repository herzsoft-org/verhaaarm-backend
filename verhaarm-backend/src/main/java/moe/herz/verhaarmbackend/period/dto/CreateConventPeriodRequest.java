package moe.herz.verhaarmbackend.period.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateConventPeriodRequest(
		@NotBlank String semester,
		@NotNull OffsetDateTime startAt,
		@NotNull OffsetDateTime endAt
) {}
