package moe.herz.verhaarmbackend.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateEventRequest(
		UUID periodId,
		String title,
		OffsetDateTime startsAt,
		Boolean mandatory
) {}
