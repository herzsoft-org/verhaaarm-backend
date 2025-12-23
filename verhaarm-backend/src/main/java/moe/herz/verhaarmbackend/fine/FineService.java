package moe.herz.verhaarmbackend.fine;

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

	@PersistenceContext
	private EntityManager em;

	public FineService(FineRepository fines, ConventPeriodRepository periods, FineCatalogRepository catalog) {
		this.fines = fines;
		this.periods = periods;
		this.catalog = catalog;
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
			// only allow active + not deleted catalog items
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

		// Ensure DB-generated columns (created_at) are available
		em.flush();
		em.clear();

		var reloaded = fines.findVisibleById(f.getId())
				.orElseThrow(() -> ApiErrors.notFound("Fine not found"));
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
			// validate catalog item
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
