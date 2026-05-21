package moe.herz.verhaarmbackend.settings.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record UserSettingsDto(
		OffsetDateTime serverTime,
		Map<String, SettingValueDto> settings
) {}