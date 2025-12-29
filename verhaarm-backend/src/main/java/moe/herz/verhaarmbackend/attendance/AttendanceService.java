package moe.herz.verhaarmbackend.attendance;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.fine.FineEntity;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.fine.FineType;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogItemEntity;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AttendanceService {

	private final AttendanceRepository attendance;
	private final EventRepository events;
	private final FineRepository fines;
	private final FineCatalogRepository catalog;

	@PersistenceContext
	private EntityManager em;

	private static final UUID SYS_LATE_ID = FineCatalogRepository.SYS_LATE_ID;
	private static final UUID SYS_ABSENT_ID = FineCatalogRepository.SYS_ABSENT_ID;

	public AttendanceService(
			AttendanceRepository attendance,
			AttendanceFineConfigRepository configs, // kept for wiring compatibility; not used anymore here
			EventRepository events,
			FineRepository fines,
			FineCatalogRepository catalog
	) {
		this.attendance = attendance;
		this.events = events;
		this.fines = fines;
		this.catalog = catalog;
	}

	// --------------------
	// Attendance exceptions CRUD (per event)
	// --------------------

	@Transactional(readOnly = true)
	public java.util.List<moe.herz.verhaarmbackend.attendance.dto.AttendanceDto> listForEvent(UUID eventId, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));
		return attendance.findVisibleByEventId(eventId).stream().map(this::toDto).toList();
	}

	@Transactional
	public moe.herz.verhaarmbackend.attendance.dto.AttendanceDto upsert(UUID eventId, moe.herz.verhaarmbackend.attendance.dto.UpsertAttendanceRequest req, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		EventEntity event = events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		UUID userId = req.userId();
		if (userId == null) throw ApiErrors.badRequest("userId required");

		AttendanceStatus status = req.status();
		if (status == null) throw ApiErrors.badRequest("status required");

		Integer lateMinutes = req.lateMinutes();

		if (status == AttendanceStatus.LATE) {
			if (lateMinutes == null) throw ApiErrors.badRequest("lateMinutes required for LATE");
			if (lateMinutes < 0) throw ApiErrors.badRequest("lateMinutes must be >= 0");
		} else {
			lateMinutes = null;
		}

		AttendanceEntity row = attendance.findVisibleByEventAndUser(eventId, userId).orElse(null);

		if (row == null) {
			AttendanceEntity existing = attendance.findAnyByEventAndUser(eventId, userId).orElse(null);

			if (existing != null) {
				existing.setDeletedAt(null);
				existing.setStatus(status);
				existing.setLateMinutes(lateMinutes);
				attendance.save(existing);
				row = existing;
			} else {
				row = new AttendanceEntity(UUID.randomUUID(), eventId, userId, status, lateMinutes);
				attendance.save(row);
			}
		} else {
			AttendanceStatus before = row.getStatus();
			UUID beforeFineId = row.getFineId();

			row.setStatus(status);
			row.setLateMinutes(lateMinutes);

			// If status changed (LATE <-> ABSENT) and a fine exists, delete it so a new snapshot is created.
			if (beforeFineId != null && before != status) {
				row.setFineId(null);
				attendance.save(row);
				hardDeleteFineById(beforeFineId);
			} else {
				attendance.save(row);
			}
		}

		// Ensure the fine exists (idempotent) for the current status.
		ensureAttendanceFine(event, row, actor);

		em.flush();
		em.clear();

		AttendanceEntity reloaded = attendance.findById(row.getId())
				.orElseThrow(() -> ApiErrors.notFound("Attendance not found"));

		return toDto(reloaded);
	}

	@Transactional
	public void deleteException(UUID eventId, UUID userId, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		AttendanceEntity row = attendance.findVisibleByEventAndUser(eventId, userId)
				.orElseThrow(() -> ApiErrors.notFound("Attendance exception not found"));

		UUID fineId = row.getFineId();
		if (fineId != null) {
			// Unlink first to satisfy FK, then delete fine.
			row.setFineId(null);
			attendance.save(row);
			hardDeleteFineById(fineId);
		}

		row.setDeletedAt(OffsetDateTime.now());
		attendance.save(row);
	}

	// --------------------
	// Internals: automatic attendance fines
	// --------------------

	private void ensureAttendanceFine(EventEntity event, AttendanceEntity a, UserEntity actor) {
		if (a.isDeleted()) return;

		UUID catalogId = (a.getStatus() == AttendanceStatus.LATE) ? SYS_LATE_ID : SYS_ABSENT_ID;

		FineCatalogItemEntity item = catalog.findActiveVisibleById(catalogId)
				.orElseThrow(() -> ApiErrors.badRequest("Attendance fine catalog item missing or inactive"));

		LocalDate fineDate = event.getStartsAt().toLocalDate();

		// Snapshot semantics: create once. Do not update existing fine when catalog changes later.
		if (a.getFineId() != null) {
			return;
		}

		UUID fineId = UUID.randomUUID();
		FineEntity f = new FineEntity(
				fineId,
				fineDate,
				actor.getId(),
				item.getId(),
				item.getTitle(),               // snapshot title
				item.getDefaultAmountCents(),  // snapshot amount
				FineType.CATALOG
		);
		f.addTarget(a.getUserId());
		fines.save(f);

		a.setFineId(fineId);
		attendance.save(a);
	}

	private void hardDeleteFineById(UUID fineId) {
		// FineService.delete() enforces role rules and also deletes photo dirs; here we want internal deletion.
		// These attendance fines should not have photos.
		fines.findVisibleById(fineId).ifPresent(fines::delete);
	}

	private void requireSeniorOrHousekeepingOrAdmin(UserEntity actor) {
		if (!(hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING))) {
			throw ApiErrors.forbidden("Forbidden");
		}
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private moe.herz.verhaarmbackend.attendance.dto.AttendanceDto toDto(AttendanceEntity a) {
		return new moe.herz.verhaarmbackend.attendance.dto.AttendanceDto(
				a.getId(),
				a.getEventId(),
				a.getUserId(),
				a.getStatus(),
				a.getLateMinutes(),
				a.getFineId(),
				a.getCreatedAt()
		);
	}
}
