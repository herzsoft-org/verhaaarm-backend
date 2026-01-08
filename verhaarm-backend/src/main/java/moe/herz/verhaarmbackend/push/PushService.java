package moe.herz.verhaarmbackend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.notification.NotificationEntity;
import moe.herz.verhaarmbackend.push.dto.RegisterFcmRequest;
import moe.herz.verhaarmbackend.push.dto.RegisterWebPushRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PushService {

	private final PushDeviceRepository devices;
	private final PushConfigProperties cfg;
	private final WebPushSender webPush;
	private final FcmSender fcm;

	private final ObjectMapper om = new ObjectMapper();
	private static final Logger log = LoggerFactory.getLogger(PushService.class);

	public PushService(PushDeviceRepository devices, PushConfigProperties cfg, WebPushSender webPush, FcmSender fcm) {
		this.devices = devices;
		this.cfg = cfg;
		this.webPush = webPush;
		this.fcm = fcm;
	}

	@Transactional
	public void registerWebPush(RegisterWebPushRequest req, UserEntity actor, String userAgent) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		if (req == null) throw ApiErrors.badRequest("body required");

		String endpoint = req.endpoint().trim();
		if (endpoint.isBlank()) throw ApiErrors.badRequest("endpoint required");

		String p256dh = req.keys().getOrDefault("p256dh", "").trim();
		String auth = req.keys().getOrDefault("auth", "").trim();
		if (p256dh.isBlank() || auth.isBlank()) throw ApiErrors.badRequest("keys.p256dh and keys.auth required");

		PushDeviceEntity d = devices.findWebPushByEndpoint(endpoint).orElse(null);
		if (d == null) {
			d = new PushDeviceEntity(UUID.randomUUID(), actor.getId(), PushDeviceKind.WEBPUSH);
		} else {
			// If endpoint is reused, bind it to the current user (last registration wins)
			d = new PushDeviceEntity(d.getId(), actor.getId(), PushDeviceKind.WEBPUSH);
		}

		d.setEndpoint(endpoint);
		d.setP256dh(p256dh);
		d.setAuth(auth);
		d.setSubscriptionJson(req.raw());
		d.setUserAgent(userAgent);
		d.setLastSeenAt(OffsetDateTime.now());

		devices.save(d);
	}

	@Transactional
	public void registerFcm(RegisterFcmRequest req, UserEntity actor, String userAgent) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		if (req == null || req.token() == null) throw ApiErrors.badRequest("token required");

		String token = req.token().trim();
		if (token.isBlank()) throw ApiErrors.badRequest("token required");

		PushDeviceEntity d = devices.findFcmByToken(token).orElse(null);
		if (d == null) {
			d = new PushDeviceEntity(UUID.randomUUID(), actor.getId(), PushDeviceKind.FCM);
		} else {
			// bind to current user
			d = new PushDeviceEntity(d.getId(), actor.getId(), PushDeviceKind.FCM);
		}

		d.setFcmToken(token);
		d.setUserAgent(userAgent);
		d.setLastSeenAt(OffsetDateTime.now());

		devices.save(d);
	}

	/**
	 * Send push for a stored notification.
	 *
	 * Fixes:
	 * - WEBPUSH: keep sending one JSON payload (fine for browsers)
	 * - FCM (Android): send DATA-ONLY with TOP-LEVEL KEYS (type, fineId/taskId, title/body, etc.)
	 *   so Flutter can route on RemoteMessage.data directly.
	 */
	public void sendForNotification(NotificationEntity n) {
		if (!cfg.isEnabled()) {
			log.debug("Push disabled; skip notificationId={} userId={}", n.getId(), n.getUserId());
			return;
		}

		List<PushDeviceEntity> ds = devices.findAllForUser(n.getUserId());
		if (ds.isEmpty()) {
			log.debug("No push devices; skip notificationId={} userId={}", n.getId(), n.getUserId());
			return;
		}

		// Build WEBPUSH JSON payload (unchanged behavior)
		String webPushPayload;
		try {
			webPushPayload = om.writeValueAsString(Map.of(
					"notificationId", n.getId().toString(),
					"type", n.getType().name(),
					"title", n.getTitle(),
					"body", n.getBody(),
					"data", n.getData()
			));
		} catch (Exception e) {
			log.warn("WebPush payload build failed notificationId={} userId={}", n.getId(), n.getUserId(), e);
			return;
		}

		// Build FCM flat data map (critical fix)
		Map<String, String> fcmData;
		try {
			var m = new HashMap<String, String>();

			// Base fields (Flutter reads these)
			m.put("notificationId", n.getId().toString());
			m.put("type", n.getType().name());
			m.put("title", n.getTitle() == null ? "" : n.getTitle());
			m.put("body", n.getBody() == null ? "" : n.getBody());

			// Flatten NotificationEntity.data into top-level keys (fineId/taskId should live here)
			if (n.getData() != null) {
				for (var e : n.getData().entrySet()) {
					if (e.getKey() == null || e.getKey().isBlank()) continue;
					Object v = e.getValue();
					if (v == null) continue;
					m.put(e.getKey(), String.valueOf(v));
				}
			}

			fcmData = Map.copyOf(m);
		} catch (Exception e) {
			log.warn("FCM payload build failed notificationId={} userId={}", n.getId(), n.getUserId(), e);
			return;
		}

		log.info("Push attempt notificationId={} userId={} devices={}", n.getId(), n.getUserId(), ds.size());

		for (PushDeviceEntity d : ds) {
			try {
				if (d.getKind() == PushDeviceKind.WEBPUSH) {
					if (d.getEndpoint() == null || d.getP256dh() == null || d.getAuth() == null) continue;
					webPush.send(d.getEndpoint(), d.getP256dh(), d.getAuth(), webPushPayload);

				} else if (d.getKind() == PushDeviceKind.FCM) {
					if (d.getFcmToken() == null) continue;

					// IMPORTANT: FcmSender must send DATA-ONLY and put each key via putData(k,v)
					// (i.e. do NOT set Notification payload in FCM)
					fcm.send(d.getFcmToken(), fcmData);
				}
			} catch (Exception ex) {
				log.warn("Push send failed kind={} deviceId={} userId={} notificationId={}: {}",
						d.getKind(), d.getId(), d.getUserId(), n.getId(), ex.toString(), ex);
			}
		}
	}
}
