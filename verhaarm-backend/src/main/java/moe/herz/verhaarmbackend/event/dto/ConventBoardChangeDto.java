package moe.herz.verhaarmbackend.event.dto;

import jakarta.validation.constraints.NotNull;
import moe.herz.verhaarmbackend.event.ConventType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One Convent's intended final state within a board batch. Both fields are required - the client
 * (which owns the drag/drop interaction) always sends the concrete intended type and date, never a
 * partial "unchanged" update; the server compares against the current value itself to detect no-ops.
 */
public record ConventBoardChangeDto(
		@NotNull UUID eventId,
		@NotNull ConventType conventType,
		@NotNull OffsetDateTime startsAt
) {}
