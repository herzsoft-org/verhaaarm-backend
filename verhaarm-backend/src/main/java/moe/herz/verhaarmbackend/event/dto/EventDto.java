package moe.herz.verhaarmbackend.event.dto;

import moe.herz.verhaarmbackend.event.EventOwnerType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventDto(
		UUID id,
		UUID creatorUserId,
		String title,
		OffsetDateTime startsAt,
		boolean mandatory,
		EventOwnerType ownerType,
		OffsetDateTime createdAt
) {}