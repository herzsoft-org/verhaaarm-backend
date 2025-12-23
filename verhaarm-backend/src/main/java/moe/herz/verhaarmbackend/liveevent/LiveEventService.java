package moe.herz.verhaarmbackend.liveevent;

import moe.herz.verhaarmbackend.common.ApiErrors;
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

	private static final int TTL_HOURS = 6;

	private final LiveEventRepository liveEvents;

	@PersistenceContext
	private EntityManager em;

	public LiveEventService(LiveEventRepository liveEvents) {
		this.liveEvents = liveEvents;
	}

	@Transactional(readOnly = true)
	public List<LiveEventDto> listActive(UserEntity actor) {
		// any authenticated user can view
		return liveEvents.findActiveVisible(OffsetDateTime.now())
				.stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public LiveEventDto getVisible(UUID id, UserEntity actor) {
		// any authenticated user can view, but expired events should not be visible
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (e.getExpiresAt().isBefore(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");
		return toDto(e);
	}

	@Transactional
	public LiveEventDto create(CreateLiveEventRequest req, UserEntity actor) {
		// any authenticated user can create
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
				expiresAt
		);

		liveEvents.save(e);

		em.flush();
		em.clear();

		var reloaded = liveEvents.findById(e.getId()).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		return toDto(reloaded);
	}

	@Transactional
	public LiveEventDto update(UUID id, UpdateLiveEventRequest req, UserEntity actor) {
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (e.getExpiresAt().isBefore(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");

		requireCanModify(e, actor);

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
		return toDto(e);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		var e = liveEvents.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Live event not found"));
		if (e.getExpiresAt().isBefore(OffsetDateTime.now())) throw ApiErrors.notFound("Live event not found");

		requireCanModify(e, actor);

		e.setDeletedAt(OffsetDateTime.now());
		liveEvents.save(e);
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
