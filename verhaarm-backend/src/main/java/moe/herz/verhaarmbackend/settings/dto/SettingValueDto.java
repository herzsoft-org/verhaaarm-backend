package moe.herz.verhaarmbackend.settings.dto;

import java.time.OffsetDateTime;

public record SettingValueDto(
		String value,
		OffsetDateTime updatedAt
) {}