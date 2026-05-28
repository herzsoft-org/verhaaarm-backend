package moe.herz.verhaarmbackend.liveevent.dto;

public record LiveEventReactionSummaryDto(
		long prostCount,
		long ichKommeCount,
		boolean reactedProst,
		boolean reactedIchKomme
) {}
