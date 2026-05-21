package moe.herz.verhaarmbackend.settings;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.settings.dto.SettingValueDto;
import moe.herz.verhaarmbackend.settings.dto.SyncUserSettingsRequest;
import moe.herz.verhaarmbackend.settings.dto.UpdateUserSettingRequest;
import moe.herz.verhaarmbackend.settings.dto.UserSettingsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class UserSettingsService {

	private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,120}$");

	private static final Map<String, String> DEFAULTS = Map.of(
			"ui.theme", "DARK",
			"users.filterPhilister", "false"
	);

	private final UserSettingRepository settings;

	public UserSettingsService(UserSettingRepository settings) {
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public UserSettingsDto get(UUID userId) {
		Map<String, SettingValueDto> result = new TreeMap<>();

		for (var entry : DEFAULTS.entrySet()) {
			result.put(entry.getKey(), new SettingValueDto(entry.getValue(), null));
		}

		for (UserSettingEntity entity : settings.findAllByUserId(userId)) {
			result.put(entity.getKey(), new SettingValueDto(entity.getValue(), entity.getUpdatedAt()));
		}

		return new UserSettingsDto(OffsetDateTime.now(), result);
	}

	@Transactional
	public UserSettingsDto sync(UUID userId, SyncUserSettingsRequest req) {
		if (req == null || req.settings() == null) {
			return get(userId);
		}

		for (UpdateUserSettingRequest item : req.settings()) {
			upsertOne(userId, item);
		}

		return get(userId);
	}

	private void upsertOne(UUID userId, UpdateUserSettingRequest item) {
		if (item == null) return;

		String key = safeTrim(item.key());
		String value = item.value();

		if (key.isBlank()) {
			throw ApiErrors.badRequest("Setting key required");
		}

		if (!KEY_PATTERN.matcher(key).matches()) {
			throw ApiErrors.badRequest("Invalid setting key");
		}

		if (value == null) {
			throw ApiErrors.badRequest("Setting value required");
		}

		if (value.length() > 4000) {
			throw ApiErrors.badRequest("Setting value too long");
		}

		validateKnownSetting(key, value);

		OffsetDateTime changedAt = item.changedAt() == null
				? OffsetDateTime.now()
				: item.changedAt();

		Optional<UserSettingEntity> existingOpt = settings.findByUserIdAndKey(userId, key);

		if (existingOpt.isPresent()) {
			UserSettingEntity existing = existingOpt.get();

			// Per-setting last-write-wins.
			// Older local values do not overwrite newer server values.
			if (existing.getUpdatedAt() != null && changedAt.isBefore(existing.getUpdatedAt())) {
				return;
			}

			existing.setValue(value);
			existing.setUpdatedAt(changedAt);
			settings.save(existing);
			return;
		}

		settings.save(new UserSettingEntity(
				UUID.randomUUID(),
				userId,
				key,
				value,
				changedAt
		));
	}

	private static void validateKnownSetting(String key, String value) {
		switch (key) {
			case "ui.theme" -> {
				if (!value.equals("DARK") && !value.equals("LIGHT")) {
					throw ApiErrors.badRequest("ui.theme must be DARK or LIGHT");
				}
			}
			case "users.filterPhilister" -> {
				if (!value.equals("true") && !value.equals("false")) {
					throw ApiErrors.badRequest("users.filterPhilister must be true or false");
				}
			}
			default -> {
				// Unknown keys are allowed intentionally.
				// This keeps the setting system forward-compatible.
			}
		}
	}

	private static String safeTrim(String value) {
		return value == null ? "" : value.trim();
	}
}