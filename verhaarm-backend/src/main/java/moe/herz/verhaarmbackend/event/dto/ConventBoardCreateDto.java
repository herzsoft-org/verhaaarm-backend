package moe.herz.verhaarmbackend.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import moe.herz.verhaarmbackend.event.ConventType;

import java.time.OffsetDateTime;

/**
 * A brand-new Convent to create as part of a board batch, alongside any existing Convente being
 * moved/retyped or deleted in the same request. Location defaults to "adH" when blank/omitted, like
 * every other Event; mandatory defaults to true when omitted, since a Convent is a formal, expected
 * meeting unless explicitly marked otherwise. Always created as EventKind.MAIN, owned by SENIOR.
 */
public record ConventBoardCreateDto(
		@NotBlank String title,
		String location,
		@NotNull OffsetDateTime startsAt,
		@NotNull ConventType conventType,
		Boolean mandatory
) {}
