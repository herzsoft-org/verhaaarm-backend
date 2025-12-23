package moe.herz.verhaarmbackend.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateEventRequest(
		@NotNull UUID periodId,
		@NotBlank String title,
		@NotNull OffsetDateTime startsAt,
		Boolean mandatory
) {}
