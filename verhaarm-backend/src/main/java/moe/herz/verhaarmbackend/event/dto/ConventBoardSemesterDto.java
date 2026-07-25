package moe.herz.verhaarmbackend.event.dto;

import java.util.List;

public record ConventBoardSemesterDto(
		String semester,                        // e.g. "WS25/26"; derived, same as ConventPeriodDto.semester
		List<ConventBoardItemDto> convents        // chronological order
) {}
