package moe.herz.verhaarmbackend.notification;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.push.PushDeviceRepository;
import moe.herz.verhaarmbackend.push.PushService;
import moe.herz.verhaarmbackend.user.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

	private final NotificationRepository notifications;
	private final PushService push;
	private final PushDeviceRepository pushDevices;
	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	public NotificationService(NotificationRepository notifications, PushService push, PushDeviceRepository pushDevices) {
		this.notifications = notifications;
		this.push = push;
		this.pushDevices = pushDevices;
	}

	public List<NotificationEntity> listMine(UserEntity actor, int limit) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		int safeLimit = Math.max(1, Math.min(limit, 200));
		return notifications.findVisibleForUser(actor.getId(), PageRequest.of(0, safeLimit));
	}

	public long unreadCount(UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		return notifications.countUnread(actor.getId());
	}

	@Transactional
	public void markRead(UUID notificationId, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		int n = notifications.markRead(actor.getId(), notificationId);
		if (n == 0) throw ApiErrors.notFound("Notification not found");
	}

	@Transactional
	public void deleteOne(UUID notificationId, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		int n = notifications.softDeleteOneForUser(actor.getId(), notificationId);
		if (n == 0) throw ApiErrors.notFound("Notification not found");
	}

	@Transactional
	public int deleteAll(UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		return notifications.softDeleteAllForUser(actor.getId());
	}

	/**
	 * Create a persistent notification and schedule push send AFTER COMMIT.
	 */
	@Transactional
	public NotificationEntity createForUser(
			UUID userId,
			NotificationType type,
			String title,
			String body,
			Map<String, Object> data
	) {
		if (userId == null) throw ApiErrors.badRequest("userId required");
		if (type == null) throw ApiErrors.badRequest("type required");

		String t = title == null ? "" : title.trim();
		String b = body == null ? "" : body.trim();
		if (t.isBlank()) t = "Notification";
		if (b.isBlank()) b = "";

		NotificationEntity n = new NotificationEntity(
				UUID.randomUUID(),
				userId,
				type,
				t,
				b,
				NotificationRouting.withRouting(type, data)
		);

		log.info("Notification createForUser userId={} type={} title={}", userId, type, t);
		notifications.save(n);

		// Send push only after DB commit succeeded
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					log.info("Notification afterCommit send notificationId={} userId={}", n.getId(), n.getUserId());
					push.sendForNotification(n);
				}
			});
		} else {
			// no tx -> send immediately (should not normally happen)
			log.info("Notification immediate send notificationId={} userId={}", n.getId(), n.getUserId());
			push.sendForNotification(n);
		}

		return n;
	}

	@Transactional
	public int createForEnabledUsersWithPush(
			NotificationType type,
			String title,
			String body,
			Map<String, Object> data
	) {
		int created = 0;
		for (UUID userId : pushDevices.findEnabledUserIdsWithValidPushDevice()) {
			try {
				createForUser(userId, type, title, body, data);
				created++;
			} catch (Exception ex) {
				log.warn("Failed to create notification type={} userId={}: {}", type, userId, ex.toString(), ex);
			}
		}
		return created;
	}
}
