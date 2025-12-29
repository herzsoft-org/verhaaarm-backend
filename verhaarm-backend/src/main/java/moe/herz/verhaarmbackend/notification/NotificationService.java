package moe.herz.verhaarmbackend.notification;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.push.PushService;
import moe.herz.verhaarmbackend.user.UserEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

	private final NotificationRepository notifications;
	private final PushService push;

	public NotificationService(NotificationRepository notifications, PushService push) {
		this.notifications = notifications;
		this.push = push;
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

	public void markRead(UUID notificationId, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		int n = notifications.markRead(actor.getId(), notificationId);
		if (n == 0) throw ApiErrors.notFound("Notification not found");
	}

	public void deleteOne(UUID notificationId, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		int n = notifications.softDeleteOneForUser(actor.getId(), notificationId);
		if (n == 0) throw ApiErrors.notFound("Notification not found");
	}

	public int deleteAll(UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		return notifications.softDeleteAllForUser(actor.getId());
	}

	/**
	 * Create a persistent notification and schedule push send AFTER COMMIT.
	 */
	public NotificationEntity createForUser(UUID userId, NotificationType type, String title, String body, Map<String, Object> data) {
		if (userId == null) throw ApiErrors.badRequest("userId required");
		if (type == null) throw ApiErrors.badRequest("type required");

		String t = title == null ? "" : title.trim();
		String b = body == null ? "" : body.trim();
		if (t.isBlank()) t = "Notification";
		if (b.isBlank()) b = "";

		NotificationEntity n = new NotificationEntity(UUID.randomUUID(), userId, type, t, b, data);
		notifications.save(n);

		// Send push only after DB commit succeeded
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					push.sendForNotification(n);
				}
			});
		} else {
			// no tx -> send immediately
			push.sendForNotification(n);
		}

		return n;
	}
}
