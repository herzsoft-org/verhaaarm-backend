package moe.herz.verhaarmbackend.settings.dto;

import java.util.List;

public record SyncUserSettingsRequest(
		List<UpdateUserSettingRequest> settings
) {}