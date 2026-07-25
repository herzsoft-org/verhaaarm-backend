package moe.herz.verhaarmbackend.event.dto;

import moe.herz.verhaarmbackend.event.ConventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConventBoardItemDto(
		UUID eventId,
		String title,
		String location,
		OffsetDateTime startsAt,
		ConventType conventType,
		String label,          // "Anconvent" / "1. Convent" / ... / "Abconvent"
		boolean consistent,
		String warning,          // non-null only when consistent == false
		boolean hasProtocolPdf    // moving/retyping this item requires removing the Protokoll first
) {}
