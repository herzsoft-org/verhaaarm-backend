package moe.herz.verhaarmbackend.push;

import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FcmSenderTest {

	@Test
	void androidMessageIsDataOnlySoTheClientRendersExactlyOneNotification() throws Exception {
		FcmSender sender = new FcmSender(new PushConfigProperties());

		Message message = sender.buildMessage("fcm-token", Map.of(
				"title", "Neuer Arbeitsauftrag",
				"body", "Keller fegen",
				"notificationId", "notification-id"
		));

		assertNull(field(message, "notification"));
		assertEquals(Map.of(
				"title", "Neuer Arbeitsauftrag",
				"body", "Keller fegen",
				"notificationId", "notification-id"
		), field(message, "data"));

		Object android = field(message, "androidConfig");
		assertEquals("high", field(android, "priority"));
		assertNull(field(android, "notification"));
	}

	private static Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
