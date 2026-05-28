package moe.herz.verhaarmbackend.liveevent;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.liveevent.dto.CreateLiveEventRequest;
import moe.herz.verhaarmbackend.liveevent.dto.LiveEventDto;
import moe.herz.verhaarmbackend.liveevent.dto.LiveEventReactionSummaryDto;
import moe.herz.verhaarmbackend.liveevent.dto.LiveEventReactionUserDto;
import moe.herz.verhaarmbackend.liveevent.dto.LiveEventReactionUsersDto;
import moe.herz.verhaarmbackend.liveevent.dto.UpdateLiveEventRequest;
import moe.herz.verhaarmbackend.notification.NotificationService;
import moe.herz.verhaarmbackend.notification.NotificationType;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LiveEventService {

	private static final int TTL_HOURS = 2;
	private static final String DEFAULT_EVENT_PLACE = "adH wenn nicht anders kommuniziert";
	private static final String DEFAULT_EVENT_DESCRIPTION = "-";
	private static final String MANDATORY_EVENT_DESCRIPTION = "Pflichtveranstaltung";

	private final LiveEventRepository liveEvents;
	private final EventRepository events;
	private final LiveEventReactionRepository reactions;
	private final AuditLogService audit;
	private final NotificationService notifications;

	@PersistenceContext
	private EntityManager em;

	public LiveEventService(
			LiveEventRepository liveEvents,
			EventRepository events,
			LiveEventReactionRepository reactions,
			AuditLogService audit,
			NotificationService notifications
	) {
		this.liveEvents = liveEvents;
		this.events = events;
		this.reactions = reactions;
		this.audit = audit;
		this.notifications = notifications;
	}

	@Transactional
	public List<LiveEventDto> listActive(UserEntity actor) {
		materializeRecentlyStartedEvents(actor);

		return liveEvents.findActiveVisible(OffsetDateTime.now())
				.stream()
				.map(e -> toDto(e, actor, false))
				.toList();
	}

	@Transactional(readOnly = true)
	public LiveEventDto getVisible(UUID id, UserEntity actor) {
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (!e.getExpiresAt().isAfter(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");
		return toDto(e, actor, true);
	}

	@Transactional
	public LiveEventDto create(CreateLiveEventRequest req, UserEntity actor) {
		String title = req.title() == null ? "" : req.title().trim();
		String place = req.place() == null ? "" : req.place().trim();
		String description = req.description() == null ? "" : req.description().trim();

		if (title.isBlank()) throw ApiErrors.badRequest("Title required");
		if (place.isBlank()) throw ApiErrors.badRequest("Place required");
		if (description.isBlank()) throw ApiErrors.badRequest("Description required");

		OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(TTL_HOURS);

		var e = new LiveEventEntity(
				UUID.randomUUID(),
				title,
				place,
				description,
				actor.getId(),
				null,
				expiresAt
		);

		liveEvents.save(e);

		em.flush();
		em.clear();

		var reloaded = liveEvents.findById(e.getId()).orElseThrow(() -> ApiErrors.notFound("Live event not found"));

		var d = audit.obj();
		audit.put(d, "liveEventId", reloaded.getId());
		audit.put(d, "sourceEventId", reloaded.getSourceEventId());
		audit.put(d, "title", reloaded.getTitle());
		audit.put(d, "place", reloaded.getPlace());
		audit.put(d, "description", reloaded.getDescription());
		audit.put(d, "expiresAt", reloaded.getExpiresAt() == null ? null : reloaded.getExpiresAt().toString());
		audit.log(actor, "liveEvent.create", d);
		notifyLiveEventCreated(reloaded);

		return toDto(reloaded, actor, true);
	}

	@Transactional
	public LiveEventDto update(UUID id, UpdateLiveEventRequest req, UserEntity actor) {
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (!e.getExpiresAt().isAfter(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");

		requireCanModify(e, actor);

		String beforeTitle = e.getTitle();
		String beforePlace = e.getPlace();
		String beforeDesc = e.getDescription();

		if (req.title() != null) {
			String title = req.title().trim();
			if (title.isBlank()) throw ApiErrors.badRequest("Title required");
			e.setTitle(title);
		}
		if (req.place() != null) {
			String place = req.place().trim();
			if (place.isBlank()) throw ApiErrors.badRequest("Place required");
			e.setPlace(place);
		}
		if (req.description() != null) {
			String desc = req.description().trim();
			if (desc.isBlank()) throw ApiErrors.badRequest("Description required");
			e.setDescription(desc);
		}

		liveEvents.save(e);

		var d = audit.obj();
		audit.put(d, "liveEventId", e.getId());
		audit.put(d, "sourceEventId", e.getSourceEventId());

		var before = audit.obj();
		audit.put(before, "title", beforeTitle);
		audit.put(before, "place", beforePlace);
		audit.put(before, "description", beforeDesc);

		var after = audit.obj();
		audit.put(after, "title", e.getTitle());
		audit.put(after, "place", e.getPlace());
		audit.put(after, "description", e.getDescription());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "liveEvent.update", d);

		return toDto(e, actor, true);
	}

	@Transactional
	public LiveEventReactionSummaryDto toggleReaction(UUID id, LiveEventReactionType type, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (!e.getExpiresAt().isAfter(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");

		var existing = reactions.findByLiveEventIdAndUserIdAndType(e.getId(), actor.getId(), type);
		if (existing.isPresent()) {
			reactions.delete(existing.get());
		} else {
			reactions.save(new LiveEventReactionEntity(UUID.randomUUID(), e.getId(), actor.getId(), type));
		}

		return reactionSummary(e.getId(), actor);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (!e.getExpiresAt().isAfter(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");

		requireCanModify(e, actor);

		e.setDeletedAt(OffsetDateTime.now());
		liveEvents.save(e);

		var d = audit.obj();
		audit.put(d, "liveEventId", e.getId());
		audit.put(d, "sourceEventId", e.getSourceEventId());
		audit.put(d, "deletedAt", e.getDeletedAt() == null ? null : e.getDeletedAt().toString());
		audit.log(actor, "liveEvent.delete", d);
	}

	private void materializeRecentlyStartedEvents(UserEntity actor) {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime cutoff = now.minusHours(TTL_HOURS);

		List<EventEntity> recentlyStartedEvents = events.findRecentlyStartedVisible(cutoff, now);

		for (EventEntity event : recentlyStartedEvents) {
			if (liveEvents.existsBySourceEventId(event.getId())) {
				continue;
			}

			var liveEvent = new LiveEventEntity(
					UUID.randomUUID(),
					event.getTitle(),
					DEFAULT_EVENT_PLACE,
					event.isMandatory() ? MANDATORY_EVENT_DESCRIPTION : DEFAULT_EVENT_DESCRIPTION,
					event.getCreatorUserId(),
					event.getId(),
					event.getStartsAt().plusHours(TTL_HOURS)
			);

			liveEvents.save(liveEvent);

			var d = audit.obj();
			audit.put(d, "liveEventId", liveEvent.getId());
			audit.put(d, "sourceEventId", liveEvent.getSourceEventId());
			audit.put(d, "eventId", event.getId());
			audit.put(d, "title", liveEvent.getTitle());
			audit.put(d, "place", liveEvent.getPlace());
			audit.put(d, "description", liveEvent.getDescription());
			audit.put(d, "expiresAt", liveEvent.getExpiresAt() == null ? null : liveEvent.getExpiresAt().toString());
			audit.log(actor, "liveEvent.materializeFromEvent", d);
			notifyLiveEventCreated(liveEvent);
		}
	}

	private void notifyLiveEventCreated(LiveEventEntity e) {
		try {
			notifications.createForEnabledUsersWithPush(
					NotificationType.LIVE_EVENT_CREATED,
					"Das geht gerade:",
					(e.getTitle() == null || e.getTitle().isBlank()) ? "Ein neues Live-Event wurde erstellt." : e.getTitle(),
					java.util.Map.of(
							"liveEventId", e.getId().toString(),
							"supportsActions", "true",
							"actionSet", "LIVE_EVENT_REACTIONS",
							"reactionEndpoint", "/live-events/" + e.getId() + "/reactions/{type}",
							"reactionTypes", "PROST,ICH_KOMME"
					)
			);
		} catch (Exception ex) {
			// Notification delivery must not block live event creation.
			org.slf4j.LoggerFactory.getLogger(LiveEventService.class)
					.warn("Live event notification failed liveEventId={}: {}", e.getId(), ex.toString(), ex);
		}
	}

	private void requireCanModify(LiveEventEntity e, UserEntity actor) {
		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		boolean isCreator = e.getCreatedByUserId().equals(actor.getId());

		if (!(isCreator || isAdmin || isSenior || isHousekeeping)) {
			throw ApiErrors.forbidden("Forbidden");
		}
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private LiveEventDto toDto(LiveEventEntity e, UserEntity actor, boolean includeReactionUsers) {
		return new LiveEventDto(
				e.getId(),
				e.getTitle(),
				e.getPlace(),
				e.getDescription(),
				e.getCreatedByUserId(),
				e.getCreatedAt(),
				e.getExpiresAt(),
				reactionSummary(e.getId(), actor),
				includeReactionUsers ? reactionUsers(e.getId()) : null
		);
	}

	private LiveEventReactionSummaryDto reactionSummary(UUID liveEventId, UserEntity actor) {
		UUID actorId = actor == null ? null : actor.getId();
		return new LiveEventReactionSummaryDto(
				reactions.countByLiveEventIdAndType(liveEventId, LiveEventReactionType.PROST),
				reactions.countByLiveEventIdAndType(liveEventId, LiveEventReactionType.ICH_KOMME),
				actorId != null && reactions.existsByLiveEventIdAndUserIdAndType(liveEventId, actorId, LiveEventReactionType.PROST),
				actorId != null && reactions.existsByLiveEventIdAndUserIdAndType(liveEventId, actorId, LiveEventReactionType.ICH_KOMME)
		);
	}

	private LiveEventReactionUsersDto reactionUsers(UUID liveEventId) {
		return new LiveEventReactionUsersDto(
				reactionUsers(liveEventId, LiveEventReactionType.PROST),
				reactionUsers(liveEventId, LiveEventReactionType.ICH_KOMME)
		);
	}

	private List<LiveEventReactionUserDto> reactionUsers(UUID liveEventId, LiveEventReactionType type) {
		return reactions.findUsersByLiveEventIdAndType(liveEventId, type)
				.stream()
				.map(u -> new LiveEventReactionUserDto(u.getId(), u.getDisplayName()))
				.toList();
	}
}
