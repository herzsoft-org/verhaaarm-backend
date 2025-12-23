package moe.herz.verhaarmbackend.attendance;

import moe.herz.verhaarmbackend.attendance.dto.*;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventOwnerType;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.fine.FineEntity;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.fine.FineType;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogItemEntity;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
		// Anyone can view events; but editing attendance is restricted.
		events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));
		return attendance.findVisibleByEventId(eventId).stream().map(this::toDto).toList();
	}

	@Transactional
	public AttendanceDto upsert(UUID eventId, UpsertAttendanceRequest req, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		EventEntity event = events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		// SENIOR/HOUSEKEEPING can add late/absent fines to any event regardless of creator:
		// => attendance modification is allowed for SENIOR/HOUSEKEEPING/ADMIN for all events.
		UUID userId = req.userId();

		AttendanceStatus status = req.status();
		Integer lateMinutes = req.lateMinutes();

		if (status == AttendanceStatus.LATE) {
			if (lateMinutes == null) throw ApiErrors.badRequest("lateMinutes required for LATE");
			if (lateMinutes < 0) throw ApiErrors.badRequest("lateMinutes must be >= 0");
		} else {
			lateMinutes = null;
		}

		AttendanceEntity row = attendance.findVisibleByEventAndUser(eventId, userId).orElse(null);
		if (row == null) {
			row = new AttendanceEntity(UUID.randomUUID(), eventId, event.getPeriodId(), userId, status, lateMinutes);
			attendance.save(row);
		} else {
			row.setStatus(status);
			row.setLateMinutes(lateMinutes);
			attendance.save(row);
		}

		em.flush();
		em.clear();

		AttendanceEntity reloaded = attendance.findById(row.getId()).orElseThrow(() -> ApiErrors.notFound("Attendance not found"));
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
	// Generate fines from attendance
	// --------------------

	@Transactional
	public GenerateAttendanceFinesResultDto generateFines(UUID eventId, GenerateAttendanceFinesRequest req, UserEntity actor) {
		requireSeniorOrHousekeepingOrAdmin(actor);

		EventEntity event = events.findVisibleById(eventId).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		AttendanceFineConfigEntity cfg = configs.findById(event.getPeriodId())
				.orElseThrow(() -> ApiErrors.badRequest("Attendance fine config not set for period"));

		List<AttendanceEntity> rows = attendance.findVisibleByEventWithoutFine(eventId);

		boolean dryRun = req != null && req.dryRun();

		List<UUID> fineIds = new ArrayList<>();

		for (AttendanceEntity a : rows) {
			FineTemplate tmpl = resolveTemplate(cfg, a.getStatus());

			if (tmpl == null) {
				throw ApiErrors.badRequest("Missing fine config for " + a.getStatus());
			}

			if (dryRun) {
				// simulate uuid without writing
				fineIds.add(UUID.randomUUID());
				continue;
			}

			FineEntity f = new FineEntity(
					UUID.randomUUID(),
					event.getPeriodId(),
					actor.getId(),
					tmpl.catalogItemId,
					tmpl.reason,
					tmpl.amountCents,
					tmpl.type
			);

			// attendance fines target exactly one user
			f.addTarget(a.getUserId());

			// store suggester_user_id = null; acceptedFromSuggestionId is not involved

			fines.save(f);
			em.flush(); // so fine ID exists for FK use below

			a.setFineId(f.getId());
			attendance.save(a);

			fineIds.add(f.getId());
		}

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

		// ABSENT
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
				a.getPeriodId(),
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
