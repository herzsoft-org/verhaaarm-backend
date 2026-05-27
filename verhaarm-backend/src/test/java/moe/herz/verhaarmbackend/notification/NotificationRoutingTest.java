package moe.herz.verhaarmbackend.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationRoutingTest {

	@Test
	void liveEventRoutesToHomeLiveEvents() {
		Map<String, Object> data = NotificationRouting.withRouting(
				NotificationType.LIVE_EVENT_CREATED,
				Map.of("liveEventId", "live-event-id")
		);

		assertEquals("LIVE_EVENT_CREATED", data.get("notificationType"));
		assertEquals("HOME_LIVE_EVENTS", data.get("clickTarget"));
		assertEquals("live-event-id", data.get("liveEventId"));
	}

	@Test
	void taskRoutesToArbeitsauftraege() {
		Map<String, Object> data = NotificationRouting.withRouting(
				NotificationType.TASK_ASSIGNED,
				Map.of("taskId", "task-id")
		);

		assertEquals("TASK_ASSIGNED", data.get("notificationType"));
		assertEquals("ACTIONS_ARBEITSAUFTRAEGE", data.get("clickTarget"));
		assertEquals("task-id", data.get("taskId"));
	}

	@Test
	void fineRoutesToBeihaengung() {
		Map<String, Object> data = NotificationRouting.withRouting(
				NotificationType.FINE_CREATED,
				Map.of("fineId", "fine-id")
		);

		assertEquals("FINE_CREATED", data.get("notificationType"));
		assertEquals("ACTIONS_BEIHAENGUNG", data.get("clickTarget"));
		assertEquals("fine-id", data.get("fineId"));
	}

	@Test
	void fineSuggestionRoutesToFineSuggestions() {
		Map<String, Object> data = NotificationRouting.withRouting(
				NotificationType.FINE_SUGGESTION_CREATED,
				Map.of("suggestionId", "suggestion-id")
		);

		assertEquals("FINE_SUGGESTION_CREATED", data.get("notificationType"));
		assertEquals("FINE_SUGGESTIONS", data.get("clickTarget"));
		assertEquals("suggestion-id", data.get("suggestionId"));
	}
}
