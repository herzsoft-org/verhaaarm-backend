package moe.herz.verhaarmbackend.fine;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.fine.dto.CreateFineRequest;
import moe.herz.verhaarmbackend.fine.dto.FineDto;
import moe.herz.verhaarmbackend.fine.dto.UpdateFineRequest;
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
public class FineService {

	private final FineRepository fines;
	private final ConventPeriodRepository periods;
	private final FineCatalogRepository catalog;
	private final AuditLogService audit;

	@PersistenceContext
	private EntityManager em;

	public FineService(FineRepository fines, ConventPeriodRepository periods, FineCatalogRepository catalog, AuditLogService audit) {
		this.fines = fines;
		this.periods = periods;
		this.catalog = catalog;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<FineDto> listForActor(UserEntity actor) {
		if (hasRole(actor, UserRole.MEMBER) && actor.getRoles().size() == 1) {
			return fines.findVisibleForTarget(actor.getId()).stream().map(this::toDto).toList();
		}
		return fines.findAllVisible().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public FineDto getForActor(UUID id, UserEntity actor) {
		var f = fines.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Fine not found"));

		if (isMemberOnly(actor) && !f.getTargetUserIds().contains(actor.getId())) {
			throw ApiErrors.forbidden("Forbidden");
		}

		return toDto(f);
	}

	@Transactional
	public FineDto create(CreateFineRequest req, UserEntity actor) {
		if (!(hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING))) {
			throw ApiErrors.forbidden("Forbidden");
		}

		if (req.targetUserIds() == null || req.targetUserIds().isEmpty()) {
			throw ApiErrors.badRequest("At least one target user is required");
		}

		var period = periods.findById(req.periodId()).orElseThrow(() -> ApiErrors.badRequest("Period not found"));

		if (period.isLocked() && !(hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR))) {
			throw ApiErrors.badRequest("Cannot create fines in locked period");
		}

		UUID catalogItemId = req.catalogItemId();

		String reason;
		int amountCents;
		FineType type;

		if (catalogItemId != null) {
			FineCatalogItemEntity item = catalog.findActiveVisibleById(catalogItemId)
					.orElseThrow(() -> ApiErrors.badRequest("Catalog item not found or inactive"));

			reason = (req.reason() == null || req.reason().trim().isBlank())
					? item.getTitle()
					: req.reason().trim();

			Integer reqAmount = req.amountCents();
			amountCents = (reqAmount == null) ? item.getDefaultAmountCents() : reqAmount;

			type = FineType.CATALOG;
		} else {
			reason = (req.reason() == null) ? "" : req.reason().trim();
			if (reason.isBlank()) throw ApiErrors.badRequest("Reason required");

			if (req.amountCents() == null) throw ApiErrors.badRequest("Amount required");
			amountCents = req.amountCents();

			type = FineType.CUSTOM;
		}

		if (amountCents < 0) throw ApiErrors.badRequest("Amount must be >= 0");

		var f = new FineEntity(
				UUID.randomUUID(),
				req.periodId(),
				actor.getId(),
				catalogItemId,
				reason,
				amountCents,
				type
		);

		for (UUID uid : req.targetUserIds()) {
			f.addTarget(uid);
		}

		fines.save(f);

		em.flush();
		em.clear();

		var reloaded = fines.findVisibleById(f.getId())
				.orElseThrow(() -> ApiErrors.notFound("Fine not found"));

		// AUDIT: fine created
		var d = audit.obj();
		audit.put(d, "fineId", reloaded.getId());
		audit.put(d, "periodId", reloaded.getPeriodId());
		audit.put(d, "creatorUserId", reloaded.getCreatorUserId());
		audit.put(d, "catalogItemId", reloaded.getCatalogItemId());
		audit.put(d, "reason", reloaded.getReason());
		audit.put(d, "amountCents", reloaded.getAmountCents());
		audit.put(d, "type", reloaded.getType() == null ? null : reloaded.getType().name());
		audit.putUuidArray(d, "targetUserIds", reloaded.getTargetUserIds());
		audit.log(actor, "fine.create", d);

		return toDto(reloaded);
	}

	@Transactional
	public FineDto update(UUID id, UpdateFineRequest req, UserEntity actor) {
		var f = fines.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Fine not found"));

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		if (!(isAdmin || isSenior || isHousekeeping)) {
			throw ApiErrors.forbidden("Forbidden");
		}

		var currentPeriod = periods.findById(f.getPeriodId()).orElseThrow(() -> ApiErrors.badRequest("Period not found"));
		if (currentPeriod.isLocked() && !(isAdmin || isSenior)) {
			throw ApiErrors.forbidden("Cannot edit fines in locked period");
		}

		if (!isAdmin && !isSenior) {
			if (!f.getCreatorUserId().equals(actor.getId())) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only edit own fines");
			}
		}

		// snapshot before
		UUID beforePeriodId = f.getPeriodId();
		String beforeReason = f.getReason();
		Integer beforeAmount = f.getAmountCents();
		UUID beforeCatalogItemId = f.getCatalogItemId();
		FineType beforeType = f.getType();
		Set<UUID> beforeTargets = Set.copyOf(f.getTargetUserIds());

		UUID newPeriodId = req.periodId() != null ? req.periodId() : f.getPeriodId();
		if (!newPeriodId.equals(f.getPeriodId())) {
			var newPeriod = periods.findById(newPeriodId).orElseThrow(() -> ApiErrors.badRequest("Period not found"));
			if (newPeriod.isLocked() && !(isAdmin || isSenior)) {
				throw ApiErrors.forbidden("Cannot move fine into locked period");
			}
			f.setPeriodId(newPeriodId);
		}

		if (req.reason() != null) {
			String reason = req.reason().trim();
			if (reason.isBlank()) throw ApiErrors.badRequest("Reason required");
			f.setReason(reason);
		}

		if (req.amountCents() != null) {
			if (req.amountCents() < 0) throw ApiErrors.badRequest("Amount must be >= 0");
			f.setAmountCents(req.amountCents());
		}

		if (req.catalogItemId() != null) {
			catalog.findActiveVisibleById(req.catalogItemId())
					.orElseThrow(() -> ApiErrors.badRequest("Catalog item not found or inactive"));

			f.setCatalogItemId(req.catalogItemId());
			f.setType(FineType.CATALOG);
		}

		if (req.targetUserIds() != null) {
			if (req.targetUserIds().isEmpty()) throw ApiErrors.badRequest("At least one target user is required");
			f.clearTargets();
			for (UUID uid : req.targetUserIds()) f.addTarget(uid);
		}

		fines.save(f);

		em.flush();
		em.clear();

		var reloaded = fines.findVisibleById(f.getId())
				.orElseThrow(() -> ApiErrors.notFound("Fine not found"));

		// AUDIT: fine updated (before/after)
		var d = audit.obj();
		audit.put(d, "fineId", reloaded.getId());

		var before = audit.obj();
		audit.put(before, "periodId", beforePeriodId);
		audit.put(before, "reason", beforeReason);
		audit.put(before, "amountCents", beforeAmount);
		audit.put(before, "catalogItemId", beforeCatalogItemId);
		audit.put(before, "type", beforeType == null ? null : beforeType.name());
		audit.putUuidArray(before, "targetUserIds", beforeTargets);

		var after = audit.obj();
		audit.put(after, "periodId", reloaded.getPeriodId());
		audit.put(after, "reason", reloaded.getReason());
		audit.put(after, "amountCents", reloaded.getAmountCents());
		audit.put(after, "catalogItemId", reloaded.getCatalogItemId());
		audit.put(after, "type", reloaded.getType() == null ? null : reloaded.getType().name());
		audit.putUuidArray(after, "targetUserIds", reloaded.getTargetUserIds());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "fine.update", d);

		return toDto(reloaded);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		var f = fines.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Fine not found"));

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		if (!(isAdmin || isSenior || isHousekeeping)) {
			throw ApiErrors.forbidden("Forbidden");
		}

		var period = periods.findById(f.getPeriodId()).orElseThrow(() -> ApiErrors.badRequest("Period not found"));
		if (period.isLocked() && !(isAdmin || isSenior)) {
			throw ApiErrors.forbidden("Cannot delete fines in locked period");
		}

		if (!isAdmin && !isSenior) {
			if (!f.getCreatorUserId().equals(actor.getId())) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only delete own fines");
			}
		}

		f.setDeletedAt(OffsetDateTime.now());
		fines.save(f);

		// AUDIT: fine deleted (soft delete)
		var d = audit.obj();
		audit.put(d, "fineId", f.getId());
		audit.put(d, "periodId", f.getPeriodId());
		audit.put(d, "deletedAt", f.getDeletedAt() == null ? null : f.getDeletedAt().toString());
		audit.log(actor, "fine.delete", d);
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private static boolean isMemberOnly(UserEntity u) {
		boolean hasMember = hasRole(u, UserRole.MEMBER);
		if (!hasMember) return false;
		return u.getRoles().stream().map(r -> r.getRole()).allMatch(r -> r == UserRole.MEMBER);
	}

	private FineDto toDto(FineEntity f) {
		return new FineDto(
				f.getId(),
				f.getPeriodId(),
				f.getCreatorUserId(),
				f.getCatalogItemId(),
				f.getReason(),
				f.getAmountCents(),
				f.getType(),
				Set.copyOf(f.getTargetUserIds()),
				f.getCreatedAt(),
				f.getSuggesterUserId(),
				f.getAcceptedFromSuggestionId()
		);
	}
}
