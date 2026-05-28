package moe.herz.verhaarmbackend.liveevent.dto;

import java.util.List;

public record LiveEventReactionUsersDto(
		List<LiveEventReactionUserDto> prost,
		List<LiveEventReactionUserDto> ichKomme
) {}
