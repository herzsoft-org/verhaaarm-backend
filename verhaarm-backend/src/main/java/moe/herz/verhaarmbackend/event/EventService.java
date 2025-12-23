package moe.herz.verhaarmbackend.event;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.dto.CreateEventRequest;
import moe.herz.verhaarmbackend.event.dto.EventDto;
import moe.herz.verhaarmbackend.event.dto.UpdateEventRequest;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
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
	private final ConventPeriodRepository periods;

	@PersistenceContext
	private EntityManager em;

	public EventService(EventRepository events, ConventPeriodRepository periods) {
		this.events = events;
		this.periods = periods;
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

		periods.findById(req.periodId()).orElseThrow(() -> ApiErrors.badRequest("Period not found"));

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
				req.periodId(),
				actor.getId(),
				title,
				startsAt,
				mandatory,
				ownerType
		);

		events.save(e);

		// Ensure DB-generated columns (created_at) are available
		em.flush();
		em.clear();

		var reloaded = events.findVisibleById(e.getId())
				.orElseThrow(() -> ApiErrors.notFound("Event not found"));
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

		if (req.periodId() != null && !req.periodId().equals(e.getPeriodId())) {
			periods.findById(req.periodId()).orElseThrow(() -> ApiErrors.badRequest("Period not found"));
			e.setPeriodId(req.periodId());
		}

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
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private EventDto toDto(EventEntity e) {
		return new EventDto(
				e.getId(),
				e.getPeriodId(),
				e.getCreatorUserId(),
				e.getTitle(),
				e.getStartsAt(),
				e.isMandatory(),
				e.getOwnerType(),
				e.getCreatedAt()
		);
	}
}
