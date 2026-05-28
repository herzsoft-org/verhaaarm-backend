package moe.herz.verhaarmbackend.liveevent.dto;

import java.util.UUID;

public record LiveEventReactionUserDto(
		UUID id,
		String displayName
) {}
