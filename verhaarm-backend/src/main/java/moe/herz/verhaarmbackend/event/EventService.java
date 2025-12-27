package moe.herz.verhaarmbackend.event;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.dto.CreateEventRequest;
import moe.herz.verhaarmbackend.event.dto.EventDto;
import moe.herz.verhaarmbackend.event.dto.UpdateEventRequest;
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
public class EventService {

	private final EventRepository events;
	private final AuditLogService audit;

	@PersistenceContext
	private EntityManager em;

	public EventService(EventRepository events, AuditLogService audit) {
		this.events = events;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<EventDto> listVisible(UserEntity actor) {
		return events.findAllVisible().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public EventDto getVisible(UUID id, UserEntity actor) {
		var e = events.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Event not found"));
		return toDto(e);
	}

	@Transactional
	public EventDto create(CreateEventRequest req, UserEntity actor) {
		if (!(hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING))) {
			throw ApiErrors.forbidden("Forbidden");
		}

		String title = req.title() == null ? "" : req.title().trim();
		if (title.isBlank()) throw ApiErrors.badRequest("Title required");

		OffsetDateTime startsAt = req.startsAt();
		if (startsAt == null) throw ApiErrors.badRequest("startsAt required");
		if (startsAt.isBefore(OffsetDateTime.now())) throw ApiErrors.badRequest("Cannot schedule events in the past");

		boolean mandatory = req.mandatory() != null && req.mandatory();

		EventOwnerType ownerType = (hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR))
				? EventOwnerType.SENIOR
				: EventOwnerType.HOUSEKEEPING;

		var e = new EventEntity(
				UUID.randomUUID(),
				actor.getId(),
				title,
				startsAt,
				mandatory,
				ownerType
		);

		events.save(e);

		em.flush();
		em.clear();

		var reloaded = events.findVisibleById(e.getId())
				.orElseThrow(() -> ApiErrors.notFound("Event not found"));

		var d = audit.obj();
		audit.put(d, "eventId", reloaded.getId());
		audit.put(d, "creatorUserId", reloaded.getCreatorUserId());
		audit.put(d, "title", reloaded.getTitle());
		audit.put(d, "startsAt", reloaded.getStartsAt() == null ? null : reloaded.getStartsAt().toString());
		audit.put(d, "mandatory", reloaded.isMandatory());
		audit.put(d, "ownerType", reloaded.getOwnerType() == null ? null : reloaded.getOwnerType().name());
		audit.log(actor, "event.create", d);

		return toDto(reloaded);
	}

	@Transactional
	public EventDto update(UUID id, UpdateEventRequest req, UserEntity actor) {
		var e = events.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		if (!(isAdmin || isSenior || isHousekeeping)) throw ApiErrors.forbidden("Forbidden");

		if (!isAdmin && !isSenior) {
			if (e.getOwnerType() != EventOwnerType.HOUSEKEEPING) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only edit HOUSEKEEPING events");
			}
			if (!e.getCreatorUserId().equals(actor.getId())) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only edit own events");
			}
		}

		String beforeTitle = e.getTitle();
		OffsetDateTime beforeStartsAt = e.getStartsAt();
		boolean beforeMandatory = e.isMandatory();

		if (req.title() != null) {
			String title = req.title().trim();
			if (title.isBlank()) throw ApiErrors.badRequest("Title required");
			e.setTitle(title);
		}

		if (req.startsAt() != null) {
			if (req.startsAt().isBefore(OffsetDateTime.now())) throw ApiErrors.badRequest("Cannot schedule events in the past");
			e.setStartsAt(req.startsAt());
		}

		if (req.mandatory() != null) {
			e.setMandatory(req.mandatory());
		}

		events.save(e);

		var d = audit.obj();
		audit.put(d, "eventId", e.getId());

		var before = audit.obj();
		audit.put(before, "title", beforeTitle);
		audit.put(before, "startsAt", beforeStartsAt == null ? null : beforeStartsAt.toString());
		audit.put(before, "mandatory", beforeMandatory);

		var after = audit.obj();
		audit.put(after, "title", e.getTitle());
		audit.put(after, "startsAt", e.getStartsAt() == null ? null : e.getStartsAt().toString());
		audit.put(after, "mandatory", e.isMandatory());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "event.update", d);

		return toDto(e);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		var e = events.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		if (!(isAdmin || isSenior || isHousekeeping)) throw ApiErrors.forbidden("Forbidden");

		if (!isAdmin && !isSenior) {
			if (e.getOwnerType() != EventOwnerType.HOUSEKEEPING) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only delete HOUSEKEEPING events");
			}
			if (!e.getCreatorUserId().equals(actor.getId())) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only delete own events");
			}
		}

		e.setDeletedAt(OffsetDateTime.now());
		events.save(e);

		// Also soft-delete attendance exceptions for this event (attendance is event-bound).
		em.createNativeQuery("""
			update attendance
			set deleted_at = now()
			where event_id = :eventId
			  and deleted_at is null
		""").setParameter("eventId", id).executeUpdate();

		var d = audit.obj();
		audit.put(d, "eventId", e.getId());
		audit.put(d, "deletedAt", e.getDeletedAt() == null ? null : e.getDeletedAt().toString());
		audit.log(actor, "event.delete", d);
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private EventDto toDto(EventEntity e) {
		return new EventDto(
				e.getId(),
				e.getCreatorUserId(),
				e.getTitle(),
				e.getStartsAt(),
				e.isMandatory(),
				e.getOwnerType(),
				e.getCreatedAt()
		);
	}
}
