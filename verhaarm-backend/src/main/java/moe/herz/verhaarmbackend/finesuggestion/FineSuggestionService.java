package moe.herz.verhaarmbackend.finesuggestion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.fine.FineEntity;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.fine.FineType;
import moe.herz.verhaarmbackend.finesuggestion.dto.CreateFineSuggestionRequest;
import moe.herz.verhaarmbackend.finesuggestion.dto.FineSuggestionDto;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogItemEntity;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FineSuggestionService {

	private final FineSuggestionRepository suggestions;
	private final FineRepository fines;
	private final FineCatalogRepository catalog;
	private final AuditLogService audit;

	@PersistenceContext
	private EntityManager em;

	public FineSuggestionService(
			FineSuggestionRepository suggestions,
			FineRepository fines,
			FineCatalogRepository catalog,
			AuditLogService audit
	) {
		this.suggestions = suggestions;
		this.fines = fines;
		this.catalog = catalog;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<FineSuggestionDto> listForActor(UserEntity actor, FineSuggestionStatus statusOrNull, boolean mineOnly) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		boolean staff = hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING);

		List<FineSuggestionEntity> list;

		if (mineOnly || !staff) {
			list = (statusOrNull == null)
					? suggestions.findVisibleByCreator(actor.getId())
					: suggestions.findVisibleByCreatorAndStatus(actor.getId(), statusOrNull);
		} else {
			list = (statusOrNull == null)
					? suggestions.findAllVisible()
					: suggestions.findVisibleByStatus(statusOrNull);
		}

		return list.stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public FineSuggestionDto getForActor(UUID id, UserEntity actor) {
		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		boolean staff = hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING);
		if (staff) return toDto(s);

		if (s.getCreatorUserId().equals(actor.getId())) return toDto(s);

		throw ApiErrors.forbidden("Forbidden");
	}

	@Transactional
	public FineSuggestionDto create(CreateFineSuggestionRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		if (req.targetUserIds() == null || req.targetUserIds().isEmpty()) {
			throw ApiErrors.badRequest("At least one target user is required");
		}

		LocalDate fineDate = req.fineDate();
		if (fineDate == null) throw ApiErrors.badRequest("fineDate required");

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

		FineSuggestionEntity s = new FineSuggestionEntity(
				UUID.randomUUID(),
				fineDate,
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

		FineEntity f = new FineEntity(
				UUID.randomUUID(),
				s.getFineDate(),
				actor.getId(),
				s.getCatalogItemId(),
				s.getReason(),
				s.getAmountCents(),
				s.getType()
		);

		// keep only the useful attribution
		f.setSuggesterUserId(s.getCreatorUserId());

		// do NOT keep bidirectional link; it creates FK headaches
		f.setAcceptedFromSuggestionId(null);

		for (UUID uid : s.getTargetUserIds()) f.addTarget(uid);

		fines.save(f);

		// Hard-delete the suggestion (targets cascade)
		suggestions.delete(s);

		em.flush();
		em.clear();

		// optional: keep audit if you want, otherwise remove audit block entirely
		return new FineDtoAcceptResult(id, f.getId());
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

		// AUDIT: suggestion rejected
		var d = audit.obj();
		audit.put(d, "suggestionId", s.getId());
		audit.put(d, "fineDate", s.getFineDate() == null ? null : s.getFineDate().toString());
		audit.put(d, "suggesterUserId", s.getCreatorUserId());
		audit.log(actor, "fineSuggestion.reject", d);
	}

	public record FineDtoAcceptResult(UUID suggestionId, UUID fineId) {}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private FineSuggestionDto toDto(FineSuggestionEntity s) {
		return new FineSuggestionDto(
				s.getId(),
				s.getFineDate(),
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
