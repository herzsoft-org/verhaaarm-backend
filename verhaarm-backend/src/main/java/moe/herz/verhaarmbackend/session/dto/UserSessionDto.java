package moe.herz.verhaarmbackend.session.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserSessionDto(
		UUID id,
		UUID userId,
		String appType,
		String deviceName,
		String deviceModel,
		String osName,
		String osVersion,
		String browserName,
		String browserVersion,
		String userAgent,
		OffsetDateTime createdAt,
		OffsetDateTime lastActiveAt,
		OffsetDateTime expiresAt,
		OffsetDateTime revokedAt,
		boolean current
) {}