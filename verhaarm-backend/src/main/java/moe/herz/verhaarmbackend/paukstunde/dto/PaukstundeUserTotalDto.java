package moe.herz.verhaarmbackend.paukstunde.dto;

import java.util.Map;
import java.util.UUID;

public record PaukstundeUserTotalDto(
		UUID userId,
		String username,
		String displayName,
		String memberStatus,
		int totalHours,
		int entryCount,
		Map<String, Integer> hoursByDate
) {}
