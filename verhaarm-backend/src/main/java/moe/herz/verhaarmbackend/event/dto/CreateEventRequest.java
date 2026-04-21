package moe.herz.verhaarmbackend.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import moe.herz.verhaarmbackend.event.EventKind;

import java.time.OffsetDateTime;

public record CreateEventRequest(
		@NotBlank String title,
		@NotNull OffsetDateTime startsAt,
		Boolean mandatory,
		EventKind eventKind
) {}