package moe.herz.verhaarmbackend.event.dto;

import java.util.List;

/** The full Convente management board: every Convent-flagged event, grouped into semester blocks. */
public record ConventBoardDto(
		List<ConventBoardSemesterDto> semesters   // chronological order (by each block's first Convent)
) {}
