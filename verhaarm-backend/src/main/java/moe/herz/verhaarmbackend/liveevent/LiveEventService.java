package moe.herz.verhaarmbackend.liveevent;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.liveevent.dto.CreateLiveEventRequest;
import moe.herz.verhaarmbackend.liveevent.dto.LiveEventDto;
import moe.herz.verhaarmbackend.liveevent.dto.UpdateLiveEventRequest;
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
	private final AuditLogService audit;

	@PersistenceContext
	private EntityManager em;

	public LiveEventService(
			LiveEventRepository liveEvents,
			EventRepository events,
			AuditLogService audit
	) {
		this.liveEvents = liveEvents;
		this.events = events;
		this.audit = audit;
	}

	@Transactional
	public List<LiveEventDto> listActive(UserEntity actor) {
		materializeRecentlyStartedEvents(actor);

		return liveEvents.findActiveVisible(OffsetDateTime.now())
				.stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public LiveEventDto getVisible(UUID id, UserEntity actor) {
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (!e.getExpiresAt().isAfter(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");
		return toDto(e);
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

		return toDto(reloaded);
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

		return toDto(e);
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

	private LiveEventDto toDto(LiveEventEntity e) {
		return new LiveEventDto(
				e.getId(),
				e.getTitle(),
				e.getPlace(),
				e.getDescription(),
				e.getCreatedByUserId(),
				e.getCreatedAt(),
				e.getExpiresAt()
		);
	}
}