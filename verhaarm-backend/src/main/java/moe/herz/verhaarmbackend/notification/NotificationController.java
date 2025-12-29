package moe.herz.verhaarmbackend.notification;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.notification.dto.NotificationDto;
import moe.herz.verhaarmbackend.notification.dto.UnreadCountDto;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

	private final NotificationService notifications;
	private final UserRepository userRepo;

	public NotificationController(NotificationService notifications, UserRepository userRepo) {
		this.notifications = notifications;
		this.userRepo = userRepo;
	}

	@GetMapping
	public List<NotificationDto> list(
			@RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
			Authentication auth
	) {
		UserEntity actor = resolveActor(auth);
		return notifications.listMine(actor, limit).stream()
				.map(this::toDto)
				.toList();
	}

	@GetMapping("/unread-count")
	public UnreadCountDto unreadCount(Authentication auth) {
		UserEntity actor = resolveActor(auth);
		return new UnreadCountDto(notifications.unreadCount(actor));
	}

	@PostMapping("/{id}/read")
	public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = resolveActor(auth);
		notifications.markRead(id, actor);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteOne(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = resolveActor(auth);
		notifications.deleteOne(id, actor);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> deleteAll(Authentication auth) {
		UserEntity actor = resolveActor(auth);
		notifications.deleteAll(actor);
		return ResponseEntity.noContent().build();
	}

	private NotificationDto toDto(NotificationEntity n) {
		return new NotificationDto(
				n.getId(),
				n.getUserId(),
				n.getType(),
				n.getTitle(),
				n.getBody(),
				n.getData(),
				n.getCreatedAt(),
				n.getReadAt()
		);
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		UserEntity actor = userRepo.findByUsername(auth.getName()).orElse(null);
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		return actor;
	}
}
