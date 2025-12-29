package moe.herz.verhaarmbackend.finecatalog;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.finecatalog.dto.CreateFineCatalogItemRequest;
import moe.herz.verhaarmbackend.finecatalog.dto.FineCatalogItemDto;
import moe.herz.verhaarmbackend.finecatalog.dto.UpdateFineCatalogItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FineCatalogService {

	private final FineCatalogRepository catalog;

	private static final UUID SYS_LATE_ID = FineCatalogRepository.SYS_LATE_ID;
	private static final UUID SYS_ABSENT_ID = FineCatalogRepository.SYS_ABSENT_ID;

	public FineCatalogService(FineCatalogRepository catalog) {
		this.catalog = catalog;
	}

	private static boolean isSystemAttendanceItem(UUID id) {
		return SYS_LATE_ID.equals(id) || SYS_ABSENT_ID.equals(id);
	}

	@Transactional(readOnly = true)
	public List<FineCatalogItemDto> listVisible(boolean activeOnly) {
		var items = activeOnly ? catalog.findAllActiveVisible() : catalog.findAllVisible();
		return items.stream().map(this::toDto).toList();
	}

	/**
	 * Catalog list used for manual fine creation UIs: excludes the attendance system items.
	 */
	@Transactional(readOnly = true)
	public List<FineCatalogItemDto> listForManualFineCreation(boolean activeOnly) {
		var items = activeOnly ? catalog.findAllActiveVisibleForManualCreation() : catalog.findAllVisibleForManualCreation();
		return items.stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public FineCatalogItemDto get(UUID id) {
		var c = catalog.findById(id).orElseThrow(() -> ApiErrors.notFound("Catalog item not found"));
		if (c.isDeleted()) throw ApiErrors.notFound("Catalog item not found");
		return toDto(c);
	}

	@Transactional
	public FineCatalogItemDto create(CreateFineCatalogItemRequest req) {
		String title = req.title().trim();
		if (title.isBlank()) throw ApiErrors.badRequest("Title required");

		int cents = req.defaultAmountCents();
		if (cents < 0) throw ApiErrors.badRequest("Amount must be >= 0");

		boolean active = req.active() == null ? true : req.active();

		var c = new FineCatalogItemEntity(
				UUID.randomUUID(),
				title,
				cents,
				active,
				null
		);

		catalog.save(c);
		return toDto(c);
	}

	@Transactional
	public FineCatalogItemDto update(UUID id, UpdateFineCatalogItemRequest req) {
		var c = catalog.findById(id).orElseThrow(() -> ApiErrors.notFound("Catalog item not found"));
		if (c.isDeleted()) throw ApiErrors.notFound("Catalog item not found");

		boolean sys = isSystemAttendanceItem(id);

		if (req.title() != null) {
			if (sys) {
				throw ApiErrors.badRequest("This catalog item title cannot be changed");
			}
			String title = req.title().trim();
			if (title.isBlank()) throw ApiErrors.badRequest("Title required");
			c.setTitle(title);
		}

		if (req.defaultAmountCents() != null) {
			int cents = req.defaultAmountCents();
			if (cents < 0) throw ApiErrors.badRequest("Amount must be >= 0");
			c.setDefaultAmountCents(cents);
		}

		if (req.active() != null) {
			if (sys) {
				// keep them always active to avoid “no fine generated” states
				throw ApiErrors.badRequest("This catalog item cannot be deactivated");
			}
			c.setActive(req.active());
		}

		catalog.save(c);
		return toDto(c);
	}

	@Transactional
	public void delete(UUID id) {
		if (isSystemAttendanceItem(id)) {
			throw ApiErrors.badRequest("This catalog item cannot be deleted");
		}

		var c = catalog.findById(id).orElseThrow(() -> ApiErrors.notFound("Catalog item not found"));
		if (c.isDeleted()) return; // idempotent
		c.setDeletedAt(OffsetDateTime.now());
		catalog.save(c);
	}

	private FineCatalogItemDto toDto(FineCatalogItemEntity c) {
		return new FineCatalogItemDto(
				c.getId(),
				c.getTitle(),
				c.getDefaultAmountCents(),
				c.isActive()
		);
	}
}
