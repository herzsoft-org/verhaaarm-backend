package moe.herz.verhaarmbackend.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import moe.herz.verhaarmbackend.event.ConventType;
import moe.herz.verhaarmbackend.event.EventKind;

import java.time.OffsetDateTime;

public record CreateEventRequest(
		@NotBlank String title,
		// null/blank defaults to "adH" server-side - location is required conceptually, but never
		// worth a hard validation error over.
		String location,
		@NotNull OffsetDateTime startsAt,
		Boolean mandatory,
		EventKind eventKind,
		ConventType conventType
) {}