package moe.herz.verhaarmbackend.session;

import java.util.Locale;

public enum AppType {
	WEB,
	ANDROID,
	UNKNOWN;

	public static AppType fromNullable(String raw) {
		if (raw == null || raw.isBlank()) return UNKNOWN;

		try {
			return AppType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return UNKNOWN;
		}
	}
}