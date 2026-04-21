package moe.herz.verhaarmbackend.event.dto;

import moe.herz.verhaarmbackend.event.EventKind;

import java.time.OffsetDateTime;

public record UpdateEventRequest(
		String title,
		OffsetDateTime startsAt,
		Boolean mandatory,
		EventKind eventKind
) {}