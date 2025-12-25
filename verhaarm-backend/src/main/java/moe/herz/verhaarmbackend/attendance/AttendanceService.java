package moe.herz.verhaarmbackend.attendance;

import moe.herz.verhaarmbackend.attendance.dto.*;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.fine.FineEntity;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.fine.FineType;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogItemEntity;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.period.ConventPeriodEntity;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AttendanceService {

	private final AttendanceRepository attendance;
	private final AttendanceFineConfigRepository configs;
	private final EventRepository events;
	private final ConventPeriodRepository periods;
	private final FineRepository fines;
	private final FineCatalogRepository catalog;

	@PersistenceContext
	private EntityManager em;

	public AttendanceService(
			AttendanceRepository attendance,
			AttendanceFineConfigRepository configs,
			EventRepository events,
			ConventPeriodRepository periods,
			FineRepository fines,
			FineCatalogRepository catalog
	) {
		this.attendance = attendance;
		this.configs = configs;
		this.events = events;
		this.periods = periods;
		this.fines = fines;
		this.catalog = catalog;
	}

	// --------------------
	// Attendance fine config (per period)
	// --------------------

	@Transactional(readOnly = true)
	public AttendanceFineConfigDto getConfig(UUID periodId, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		periods.findById(periodId).orElseThrow(() -> ApiErrors.badRequest("Period not found"));

		return configs.findById(periodId)
				.map(this::toDto)
				.orElseGet(() -> new AttendanceFineConfigDto(periodId, null, null, null, null, null, null));
	}

	@Transactional
	public AttendanceFineConfigDto setConfig(UUID periodId, SetAttendanceFineConfigRequest req, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		periods.findById(periodId).orElseThrow(() -> ApiErrors.badRequest("Period not found"));

		AttendanceFineConfigEntity cfg = configs.findById(periodId).orElseGet(() -> new AttendanceFineConfigEntity(periodId));

		applyOne("LATE", req.lateCatalogItemId(), req.lateReason(), req.lateAmountCents());
		applyOne("ABSENT", req.absentCatalogItemId(), req.absentReason(), req.absentAmountCents());

		// LATE
		if (req.lateCatalogItemId() != null) {
			catalog.findActiveVisibleById(req.lateCatalogItemId())
					.orElseThrow(() -> ApiErrors.badRequest("Late catalog item not found or inactive"));
			cfg.setLateCatalogItemId(req.lateCatalogItemId());
			cfg.setLateReason(null);
			cfg.setLateAmountCents(null);
		} else if (req.lateReason() != null || req.lateAmountCents() != null) {
			cfg.setLateCatalogItemId(null);
			cfg.setLateReason(req.lateReason() == null ? null : req.lateReason().trim());
			cfg.setLateAmountCents(req.lateAmountCents());
		}

		// ABSENT
		if (req.absentCatalogItemId() != null) {
			catalog.findActiveVisibleById(req.absentCatalogItemId())
					.orElseThrow(() -> ApiErrors.badRequest("Absent catalog item not found or inactive"));
			cfg.setAbsentCatalogItemId(req.absentCatalogItemId());
			cfg.setAbsentReason(null);
			cfg.setAbsentAmountCents(null);
		} else if (req.absentReason() != null || req.absentAmountCents() != null) {
			cfg.setAbsentCatalogItemId(null);
			cfg.setAbsentReason(req.absentReason() == null ? null : req.absentReason().trim());
			cfg.setAbsentAmountCents(req.absentAmountCents());
		}

		configs.save(cfg);
		return toDto(cfg);
	}

	private static void applyOne(String name, UUID catalogId, String reason, Integer amount) {
		if (catalogId != null && (reason != null || amount != null)) {
			throw ApiErrors.badRequest(name + " config: choose either catalogItemId OR custom reason/amount");
		}
		if (catalogId == null) {
			if (reason != null) {
				String r = reason.trim();
				if (r.isBlank()) throw ApiErrors.badRequest(name + " config: reason required");
			}
			if (amount != null && amount < 0) throw ApiErrors.badRequest(name + " config: amount must be >= 0");
		}
	}

	// --------------------
	// Attendance exceptions CRUD (per event)
	// --------------------

	@Transactional(readOnly = true)
	public List<AttendanceDto> listForEvent(UUID eventId, UserEntity actor) {
		// use actor (removes warning) without changing permissions beyond "must be authenticated"
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));
		return attendance.findVisibleByEventId(eventId).stream().map(this::toDto).toList();
	}

	@Transactional
	public AttendanceDto upsert(UUID eventId, UpsertAttendanceRequest req, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));

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
			row.setStatus(status);
			row.setLateMinutes(lateMinutes);
			attendance.save(row);
		}

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

		row.setDeletedAt(OffsetDateTime.now());
		attendance.save(row);
	}

	// --------------------
	// Generate fines from attendance (IDEMPOTENT + CONCURRENCY-SAFE)
	// --------------------

	@Transactional
	public GenerateAttendanceFinesResultDto generateFines(UUID eventId, GenerateAttendanceFinesRequest req, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		EventEntity event = events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		ConventPeriodEntity derivedPeriod = periods.findCovering(event.getStartsAt())
				.orElseThrow(() -> ApiErrors.badRequest("No convent period covers event date"));

		AttendanceFineConfigEntity cfg = configs.findById(derivedPeriod.getId())
				.orElseThrow(() -> ApiErrors.badRequest("Attendance fine config not set for derived period"));

		boolean dryRun = req != null && req.dryRun();

		List<AttendanceEntity> rows = attendance.findVisibleByEventForUpdate(eventId);

		List<UUID> fineIds = new ArrayList<>();

		LocalDate fineDate = event.getStartsAt().toLocalDate();

		for (AttendanceEntity a : rows) {
			FineTemplate tmpl = resolveTemplate(cfg, a.getStatus());
			if (tmpl == null) throw ApiErrors.badRequest("Missing fine config for " + a.getStatus());

			if (dryRun) {
				fineIds.add(a.getFineId() != null ? a.getFineId() : UUID.randomUUID());
				continue;
			}

			if (a.getFineId() == null) {
				UUID fineId = UUID.randomUUID();
				FineEntity f = new FineEntity(
						fineId,
						fineDate,
						actor.getId(),
						tmpl.catalogItemId,
						tmpl.reason,
						tmpl.amountCents,
						tmpl.type
				);
				f.addTarget(a.getUserId());
				fines.save(f);

				a.setFineId(fineId);
				attendance.save(a);

				fineIds.add(fineId);
			} else {
				FineEntity f = fines.findVisibleById(a.getFineId())
						.orElseThrow(() -> ApiErrors.notFound("Linked fine not found"));

				f.setFineDate(fineDate);

				f.setCatalogItemId(tmpl.catalogItemId);
				f.setReason(tmpl.reason);
				f.setAmountCents(tmpl.amountCents);
				f.setType(tmpl.type);

				f.clearTargets();
				f.addTarget(a.getUserId());

				fines.save(f);

				fineIds.add(f.getId());
			}
		}

		em.flush();
		return new GenerateAttendanceFinesResultDto(fineIds.size(), fineIds);
	}

	private FineTemplate resolveTemplate(AttendanceFineConfigEntity cfg, AttendanceStatus status) {
		if (status == AttendanceStatus.LATE) {
			if (cfg.getLateCatalogItemId() != null) {
				FineCatalogItemEntity item = catalog.findActiveVisibleById(cfg.getLateCatalogItemId())
						.orElseThrow(() -> ApiErrors.badRequest("Late catalog item not found or inactive"));
				return new FineTemplate(item.getId(), item.getTitle(), item.getDefaultAmountCents(), FineType.CATALOG);
			}
			if (cfg.getLateReason() != null && cfg.getLateAmountCents() != null) {
				return new FineTemplate(null, cfg.getLateReason(), cfg.getLateAmountCents(), FineType.CUSTOM);
			}
			return null;
		}

		if (cfg.getAbsentCatalogItemId() != null) {
			FineCatalogItemEntity item = catalog.findActiveVisibleById(cfg.getAbsentCatalogItemId())
					.orElseThrow(() -> ApiErrors.badRequest("Absent catalog item not found or inactive"));
			return new FineTemplate(item.getId(), item.getTitle(), item.getDefaultAmountCents(), FineType.CATALOG);
		}
		if (cfg.getAbsentReason() != null && cfg.getAbsentAmountCents() != null) {
			return new FineTemplate(null, cfg.getAbsentReason(), cfg.getAbsentAmountCents(), FineType.CUSTOM);
		}
		return null;
	}

	private static final class FineTemplate {
		final UUID catalogItemId;
		final String reason;
		final int amountCents;
		final FineType type;

		FineTemplate(UUID catalogItemId, String reason, int amountCents, FineType type) {
			this.catalogItemId = catalogItemId;
			this.reason = reason;
			this.amountCents = amountCents;
			this.type = type;
		}
	}

	private void requireSeniorOrHousekeepingOrAdmin(UserEntity actor) {
		if (!(hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING))) {
			throw ApiErrors.forbidden("Forbidden");
		}
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private AttendanceDto toDto(AttendanceEntity a) {
		return new AttendanceDto(
				a.getId(),
				a.getEventId(),
				a.getUserId(),
				a.getStatus(),
				a.getLateMinutes(),
				a.getFineId(),
				a.getCreatedAt()
		);
	}

	private AttendanceFineConfigDto toDto(AttendanceFineConfigEntity c) {
		return new AttendanceFineConfigDto(
				c.getPeriodId(),
				c.getLateCatalogItemId(),
				c.getLateReason(),
				c.getLateAmountCents(),
				c.getAbsentCatalogItemId(),
				c.getAbsentReason(),
				c.getAbsentAmountCents()
		);
	}
}
