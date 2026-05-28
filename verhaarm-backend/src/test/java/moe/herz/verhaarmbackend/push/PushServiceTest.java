package moe.herz.verhaarmbackend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.notification.NotificationEntity;
import moe.herz.verhaarmbackend.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

	@Test
	void liveEventReactionActionMetadataIsIncludedInFcmAndWebPushPayloads() throws Exception {
		PushDeviceRepository devices = mock(PushDeviceRepository.class);
		WebPushSender webPush = mock(WebPushSender.class);
		FcmSender fcm = mock(FcmSender.class);
		PushService service = new PushService(devices, new PushConfigProperties(), webPush, fcm);

		UUID userId = UUID.randomUUID();
		PushDeviceEntity webDevice = new PushDeviceEntity(UUID.randomUUID(), userId, PushDeviceKind.WEBPUSH);
		webDevice.setEndpoint("https://push.example/sub");
		webDevice.setP256dh("p256dh");
		webDevice.setAuth("auth");
		PushDeviceEntity fcmDevice = new PushDeviceEntity(UUID.randomUUID(), userId, PushDeviceKind.FCM);
		fcmDevice.setFcmToken("fcm-token");
		when(devices.findAllForUser(userId)).thenReturn(List.of(webDevice, fcmDevice));

		NotificationEntity notification = new NotificationEntity(
				UUID.randomUUID(),
				userId,
				NotificationType.LIVE_EVENT_CREATED,
				"Das geht gerade:",
				"Titel",
				Map.of(
						"liveEventId", "live-event-id",
						"notificationType", "LIVE_EVENT_CREATED",
						"clickTarget", "HOME_LIVE_EVENTS",
						"supportsActions", "true",
						"actionSet", "LIVE_EVENT_REACTIONS",
						"reactionEndpoint", "/live-events/live-event-id/reactions/{type}",
						"reactionTypes", "PROST,ICH_KOMME"
				)
		);

		service.sendForNotification(notification);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> fcmDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(fcm).send(eq("fcm-token"), fcmDataCaptor.capture());
		Map<String, String> fcmData = fcmDataCaptor.getValue();
		assertEquals("LIVE_EVENT_REACTIONS", fcmData.get("actionSet"));
		assertEquals("/live-events/live-event-id/reactions/{type}", fcmData.get("reactionEndpoint"));
		assertEquals("PROST,ICH_KOMME", fcmData.get("reactionTypes"));
		assertEquals("HOME_LIVE_EVENTS", fcmData.get("clickTarget"));

		ArgumentCaptor<String> webPayloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(webPush).send(eq("https://push.example/sub"), eq("p256dh"), eq("auth"), webPayloadCaptor.capture());
		var data = new ObjectMapper().readTree(webPayloadCaptor.getValue()).get("data");
		assertEquals("LIVE_EVENT_REACTIONS", data.get("actionSet").asText());
		assertEquals("/live-events/live-event-id/reactions/{type}", data.get("reactionEndpoint").asText());
		assertEquals("PROST,ICH_KOMME", data.get("reactionTypes").asText());
		assertEquals("HOME_LIVE_EVENTS", data.get("clickTarget").asText());
	}
}
