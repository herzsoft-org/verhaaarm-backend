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

	public FineCatalogService(FineCatalogRepository catalog) {
		this.catalog = catalog;
	}

	@Transactional(readOnly = true)
	public List<FineCatalogItemDto> listVisible(boolean activeOnly) {
		var items = activeOnly ? catalog.findAllActiveVisible() : catalog.findAllVisible();
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

		if (req.title() != null) {
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
			c.setActive(req.active());
		}

		catalog.save(c);
		return toDto(c);
	}

	@Transactional
	public void delete(UUID id) {
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
