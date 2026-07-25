package moe.herz.verhaarmbackend.event.dto;

import moe.herz.verhaarmbackend.event.ConventType;
import moe.herz.verhaarmbackend.event.EventKind;
import moe.herz.verhaarmbackend.event.EventOwnerType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventDto(
		UUID id,
		UUID creatorUserId,
		String title,
		String location,
		OffsetDateTime startsAt,
		boolean mandatory,
		EventKind eventKind,
		EventOwnerType ownerType,
		OffsetDateTime createdAt,
		ConventType conventType,
		// backend-computed: "Anconvent" / "1. Convent" / ... / "Abconvent"; null when conventType is null
		String conventLabel
) {}