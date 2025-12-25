package moe.herz.verhaarmbackend.event.dto;

import java.time.OffsetDateTime;

public record UpdateEventRequest(
		String title,
		OffsetDateTime startsAt,
		Boolean mandatory
) {}