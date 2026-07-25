package moe.herz.verhaarmbackend.event.dto;

import moe.herz.verhaarmbackend.event.ConventType;
import moe.herz.verhaarmbackend.event.EventKind;

import java.time.OffsetDateTime;

public record UpdateEventRequest(
		String title,
		// null = unchanged; blank is treated as a reset to the "adH" default rather than rejected.
		String location,
		OffsetDateTime startsAt,
		Boolean mandatory,
		EventKind eventKind,
		ConventType conventType,
		// null conventType above means "unchanged" (same convention as every other field here);
		// set this true to explicitly clear an existing conventType (un-mark as Convent).
		Boolean clearConventType
) {}