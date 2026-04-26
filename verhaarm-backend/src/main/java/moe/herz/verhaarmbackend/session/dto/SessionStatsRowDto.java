package moe.herz.verhaarmbackend.session.dto;

public record SessionStatsRowDto(
		String appType,
		String detail,
		long count
) {}