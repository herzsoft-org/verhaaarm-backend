package moe.herz.verhaarmbackend.settings.dto;

import java.time.OffsetDateTime;

public record UpdateUserSettingRequest(
		String key,
		String value,
		OffsetDateTime changedAt
) {}