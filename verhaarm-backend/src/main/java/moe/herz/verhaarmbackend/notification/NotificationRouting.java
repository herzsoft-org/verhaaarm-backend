package moe.herz.verhaarmbackend.notification;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NotificationRouting {
	public static final String NOTIFICATION_TYPE_KEY = "notificationType";
	public static final String CLICK_TARGET_KEY = "clickTarget";

	private NotificationRouting() {}

	public static Map<String, Object> withRouting(NotificationType type, Map<String, Object> data) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (data != null) out.putAll(data);
		out.put(NOTIFICATION_TYPE_KEY, type.name());

		NotificationClickTarget clickTarget = clickTargetFor(type);
		if (clickTarget != null) {
			out.put(CLICK_TARGET_KEY, clickTarget.name());
		}

		return out;
	}

	public static NotificationClickTarget clickTargetFor(NotificationType type) {
		if (type == null) return null;
		return switch (type) {
			case LIVE_EVENT_CREATED -> NotificationClickTarget.HOME_LIVE_EVENTS;
			case TASK_ASSIGNED -> NotificationClickTarget.ACTIONS_ARBEITSAUFTRAEGE;
			case FINE_CREATED -> NotificationClickTarget.ACTIONS_BEIHAENGUNG;
			case FINE_SUGGESTION_CREATED -> NotificationClickTarget.FINE_SUGGESTIONS;
		};
	}
}
