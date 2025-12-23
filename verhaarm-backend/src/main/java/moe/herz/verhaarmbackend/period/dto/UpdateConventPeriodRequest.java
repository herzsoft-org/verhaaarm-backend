package moe.herz.verhaarmbackend.period.dto;

import java.time.OffsetDateTime;

public record UpdateConventPeriodRequest(
		String semester,
		OffsetDateTime startAt,
		OffsetDateTime endAt,
		Boolean active,   // optional: allow toggling via PATCH (still validated)
		Boolean locked    // optional: allow toggling via PATCH (still validated)
) {}
