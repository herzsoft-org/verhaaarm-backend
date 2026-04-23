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
import moe.herz.verhaarmbackend.finesuggestion.dto.UpdateFineSuggestionRequest;
import moe.herz.verhaarmbackend.finesuggestionphoto.FineSuggestionPhotoService;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogItemEntity;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FineSuggestionService {

	private final FineSuggestionRepository suggestions;
	private final FineRepository fines;
	private final FineCatalogRepository catalog;
	private final AuditLogService audit;
	private final FineSuggestionPhotoService suggestionPhotos;

	@PersistenceContext
	private EntityManager em;

	public FineSuggestionService(
			FineSuggestionRepository suggestions,
			FineRepository fines,
			FineCatalogRepository catalog,
			AuditLogService audit,
			FineSuggestionPhotoService suggestionPhotos
	) {
		this.suggestions = suggestions;
		this.fines = fines;
		this.catalog = catalog;
		this.audit = audit;
		this.suggestionPhotos = suggestionPhotos;
	}

	@Transactional(readOnly = true)
	public List<FineSuggestionDto> listForActor(UserEntity actor, FineSuggestionStatus statusOrNull, boolean mineOnly) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		boolean staff = isStaff(actor);

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
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		if (isStaff(actor)) return toDto(s);
		if (isCreator(actor, s)) return toDto(s);

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
	public FineSuggestionDto update(UUID id, UpdateFineSuggestionRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		if (!canManageSuggestion(actor, s)) {
			throw ApiErrors.forbidden("Forbidden");
		}

		if (s.getStatus() != FineSuggestionStatus.PENDING) {
			throw ApiErrors.badRequest("Only pending suggestions can be edited");
		}

		LocalDate beforeFineDate = s.getFineDate();
		String beforeReason = s.getReason();
		Integer beforeAmount = s.getAmountCents();
		UUID beforeCatalogItemId = s.getCatalogItemId();
		FineType beforeType = s.getType();
		Set<UUID> beforeTargets = Set.copyOf(s.getTargetUserIds());

		if (req.fineDate() != null) {
			s.setFineDate(req.fineDate());
		}

		if (req.catalogItemId() != null) {
			FineCatalogItemEntity item = catalog.findActiveVisibleById(req.catalogItemId())
					.orElseThrow(() -> ApiErrors.badRequest("Catalog item not found or inactive"));

			s.setCatalogItemId(req.catalogItemId());
			s.setReason((req.reason() == null || req.reason().trim().isBlank()) ? item.getTitle() : req.reason().trim());

			Integer reqAmount = req.amountCents();
			s.setAmountCents(reqAmount == null ? item.getDefaultAmountCents() : reqAmount);
			s.setType(FineType.CATALOG);
		} else {
			if (req.reason() != null) {
				String reason = req.reason().trim();
				if (reason.isBlank()) throw ApiErrors.badRequest("Reason required");
				s.setReason(reason);
			}

			if (req.amountCents() != null) {
				if (req.amountCents() < 0) throw ApiErrors.badRequest("Amount must be >= 0");
				s.setAmountCents(req.amountCents());
			}

			if (req.reason() != null || req.amountCents() != null) {
				s.setType(FineType.CUSTOM);
				s.setCatalogItemId(null);
			}
		}

		if (req.targetUserIds() != null) {
			if (req.targetUserIds().isEmpty()) throw ApiErrors.badRequest("At least one target user is required");
			s.clearTargets();
			for (UUID uid : req.targetUserIds()) s.addTarget(uid);
		}

		suggestions.save(s);

		em.flush();
		em.clear();

		FineSuggestionEntity reloaded = suggestions.findVisibleById(s.getId())
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		var d = audit.obj();
		audit.put(d, "suggestionId", reloaded.getId());

		var before = audit.obj();
		audit.put(before, "fineDate", beforeFineDate == null ? null : beforeFineDate.toString());
		audit.put(before, "reason", beforeReason);
		audit.put(before, "amountCents", beforeAmount);
		audit.put(before, "catalogItemId", beforeCatalogItemId);
		audit.put(before, "type", beforeType == null ? null : beforeType.name());
		audit.putUuidArray(before, "targetUserIds", beforeTargets);

		var after = audit.obj();
		audit.put(after, "fineDate", reloaded.getFineDate() == null ? null : reloaded.getFineDate().toString());
		audit.put(after, "reason", reloaded.getReason());
		audit.put(after, "amountCents", reloaded.getAmountCents());
		audit.put(after, "catalogItemId", reloaded.getCatalogItemId());
		audit.put(after, "type", reloaded.getType() == null ? null : reloaded.getType().name());
		audit.putUuidArray(after, "targetUserIds", reloaded.getTargetUserIds());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "fineSuggestion.update", d);

		return toDto(reloaded);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		if (!canManageSuggestion(actor, s)) {
			throw ApiErrors.forbidden("Forbidden");
		}

		if (s.getStatus() != FineSuggestionStatus.PENDING) {
			throw ApiErrors.badRequest("Only pending suggestions can be deleted");
		}

		var d = audit.obj();
		audit.put(d, "suggestionId", s.getId());
		audit.put(d, "fineDate", s.getFineDate() == null ? null : s.getFineDate().toString());
		audit.put(d, "deletedAt", OffsetDateTime.now().toString());
		audit.log(actor, "fineSuggestion.delete", d);

		suggestionPhotos.deleteSuggestionDirectoryBestEffort(id);
		suggestions.delete(s);

		em.flush();
	}

	@Transactional
	public FineDtoAcceptResult accept(UUID id, UserEntity actor) {
		boolean canDecide = isStaff(actor);
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

		f.setSuggesterUserId(s.getCreatorUserId());
		f.setAcceptedFromSuggestionId(null);

		for (UUID uid : s.getTargetUserIds()) {
			f.addTarget(uid);
		}

		fines.save(f);
		em.flush();

		suggestionPhotos.transferSuggestionPhotosToFine(s.getId(), f.getId());
		suggestionPhotos.deleteSuggestionDirectoryBestEffort(s.getId());

		suggestions.delete(s);

		em.flush();
		em.clear();

		var d = audit.obj();
		audit.put(d, "suggestionId", id);
		audit.put(d, "fineId", f.getId());
		audit.put(d, "suggesterUserId", s.getCreatorUserId());
		audit.log(actor, "fineSuggestion.accept", d);

		return new FineDtoAcceptResult(id, f.getId());
	}

	@Transactional
	public void reject(UUID id, UserEntity actor) {
		boolean canDecide = isStaff(actor);
		if (!canDecide) throw ApiErrors.forbidden("Forbidden");

		FineSuggestionEntity s = suggestions.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		if (s.getStatus() != FineSuggestionStatus.PENDING) {
			throw ApiErrors.badRequest("Suggestion is not pending");
		}

		s.markRejected(actor.getId());
		suggestions.save(s);

		var d = audit.obj();
		audit.put(d, "suggestionId", s.getId());
		audit.put(d, "fineDate", s.getFineDate() == null ? null : s.getFineDate().toString());
		audit.put(d, "suggesterUserId", s.getCreatorUserId());
		audit.log(actor, "fineSuggestion.reject", d);
	}

	public record FineDtoAcceptResult(UUID suggestionId, UUID fineId) {}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u != null && u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private static boolean isStaff(UserEntity u) {
		return hasRole(u, UserRole.ADMIN)
				|| hasRole(u, UserRole.SENIOR)
				|| hasRole(u, UserRole.HOUSEKEEPING);
	}

	private static boolean isCreator(UserEntity actor, FineSuggestionEntity s) {
		return actor != null
				&& actor.getId() != null
				&& s.getCreatorUserId() != null
				&& s.getCreatorUserId().equals(actor.getId());
	}

	private static boolean canManageSuggestion(UserEntity actor, FineSuggestionEntity s) {
		return isStaff(actor) || isCreator(actor, s);
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