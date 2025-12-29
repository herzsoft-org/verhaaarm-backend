package moe.herz.verhaarmbackend.notification.dto;

import moe.herz.verhaarmbackend.notification.NotificationType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationDto(
		UUID id,
		UUID userId,
		NotificationType type,
		String title,
		String body,
		Map<String, Object> data,
		OffsetDateTime createdAt,
		OffsetDateTime readAt
) {
}
