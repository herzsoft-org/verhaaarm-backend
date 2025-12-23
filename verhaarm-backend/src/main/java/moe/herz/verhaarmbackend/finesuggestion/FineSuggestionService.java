package moe.herz.verhaarmbackend.finesuggestion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.fine.FineEntity;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.fine.FineType;
import moe.herz.verhaarmbackend.finesuggestion.dto.CreateFineSuggestionRequest;
import moe.herz.verhaarmbackend.finesuggestion.dto.FineSuggestionDto;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogItemEntity;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FineSuggestionService {

	private final FineSuggestionRepository suggestions;
	private final FineRepository fines;
	private final ConventPeriodRepository periods;
	private final FineCatalogRepository catalog;

	@PersistenceContext
	private EntityManager em;

	public FineSuggestionService(
			FineSuggestionRepository suggestions,
			FineRepository fines,
			ConventPeriodRepository periods,
			FineCatalogRepository catalog
	) {
		this.suggestions = suggestions;
		this.fines = fines;
		this.periods = periods;
		this.catalog = catalog;
	}

	@Transactional(readOnly = true)
	public List<FineSuggestionDto> listForActor(UserEntity actor, FineSuggestionStatus statusOrNull) {
		if (!(hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING))) {
			// MEMBERS are not allowed to see the global suggestions list
			throw ApiErrors.forbidden("Forbidden");
		}

		List<FineSuggestionEntity> list = (statusOrNull == null)
				? suggestions.findAllVisible()
				: suggestions.findVisibleByStatus(statusOrNull);

		return list.stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public FineSuggestionDto getForActor(UUID id, UserEntity actor) {
		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		boolean staff = hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING);
		if (staff) return toDto(s);

		// creator can see own suggestion
		if (s.getCreatorUserId().equals(actor.getId())) return toDto(s);

		throw ApiErrors.forbidden("Forbidden");
	}

	@Transactional
	public FineSuggestionDto create(CreateFineSuggestionRequest req, UserEntity actor) {
		if (req.targetUserIds() == null || req.targetUserIds().isEmpty()) {
			throw ApiErrors.badRequest("At least one target user is required");
		}

		var period = periods.findById(req.periodId())
				.orElseThrow(() -> ApiErrors.badRequest("Period not found"));

		UUID catalogItemId = req.catalogItemId();

		String reason;
		int amountCents;
		FineType type;

		if (catalogItemId != null) {
			// For suggestion creation we validate active/visible catalog item (same as fine create UI)
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

		// Note: suggestions are allowed even in locked periods (they do not affect balances).
		// Acceptance will enforce the official fine creation rules.

		FineSuggestionEntity s = new FineSuggestionEntity(
				UUID.randomUUID(),
				period.getId(),
				actor.getId(),
				catalogItemId,
				reason,
				amountCents,
				type
		);

		for (UUID uid : req.targetUserIds()) {
			s.addTarget(uid);
		}

		suggestions.save(s);

		em.flush();
		em.clear();

		FineSuggestionEntity reloaded = suggestions.findVisibleById(s.getId())
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));
		return toDto(reloaded);
	}

	@Transactional
	public FineDtoAcceptResult accept(UUID id, UserEntity actor) {
		boolean canDecide = hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING);
		if (!canDecide) throw ApiErrors.forbidden("Forbidden");

		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		if (s.getStatus() != FineSuggestionStatus.PENDING) {
			throw ApiErrors.badRequest("Suggestion is not pending");
		}

		var period = periods.findById(s.getPeriodId())
				.orElseThrow(() -> ApiErrors.badRequest("Period not found"));

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		// Acceptance creates an OFFICIAL fine, so we enforce the same locked-period rules:
		if (period.isLocked() && !(isAdmin || isSenior)) {
			throw ApiErrors.forbidden("Cannot accept suggestions into locked period");
		}

		// Create official fine directly (so we can preserve the suggestion snapshot).
		FineEntity f = new FineEntity(
				UUID.randomUUID(),
				s.getPeriodId(),
				actor.getId(),              // accepting person becomes the creator
				s.getCatalogItemId(),
				s.getReason(),
				s.getAmountCents(),
				s.getType()
		);

		// Store suggester metadata on the fine (binding requirement)
		f.setSuggesterUserId(s.getCreatorUserId());
		f.setAcceptedFromSuggestionId(s.getId());

		for (UUID uid : s.getTargetUserIds()) {
			f.addTarget(uid);
		}

		fines.save(f);

		// Mark suggestion accepted
		s.markAccepted(actor.getId(), f.getId());
		suggestions.save(s);

		em.flush();
		em.clear();

		return new FineDtoAcceptResult(s.getId(), f.getId());
	}

	@Transactional
	public void reject(UUID id, UserEntity actor) {
		boolean canDecide = hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING);
		if (!canDecide) throw ApiErrors.forbidden("Forbidden");

		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		if (s.getStatus() != FineSuggestionStatus.PENDING) {
			throw ApiErrors.badRequest("Suggestion is not pending");
		}

		s.markRejected(actor.getId());
		suggestions.save(s);
	}

	public record FineDtoAcceptResult(UUID suggestionId, UUID fineId) {}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private FineSuggestionDto toDto(FineSuggestionEntity s) {
		return new FineSuggestionDto(
				s.getId(),
				s.getPeriodId(),
				s.getCreatorUserId(),
				s.getCatalogItemId(),
				s.getReason(),
				s.getAmountCents(),
				s.getType(),
				s.getStatus(),
				s.getDecidedByUserId(),
				s.getDecidedAt(),
				s.getAcceptedFineId(),
				Set.copyOf(s.getTargetUserIds()),
				s.getCreatedAt()
		);
	}
}
