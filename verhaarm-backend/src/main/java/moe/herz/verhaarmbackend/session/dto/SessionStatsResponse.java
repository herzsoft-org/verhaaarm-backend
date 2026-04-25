package moe.herz.verhaarmbackend.session.dto;

import java.util.List;

public record SessionStatsResponse(
		List<SessionStatsRowDto> week,
		List<SessionStatsRowDto> month,
		List<SessionStatsRowDto> year
) {}