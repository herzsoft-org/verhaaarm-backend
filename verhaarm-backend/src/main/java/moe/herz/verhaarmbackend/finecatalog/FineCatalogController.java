package moe.herz.verhaarmbackend.finecatalog;

import moe.herz.verhaarmbackend.finecatalog.dto.CreateFineCatalogItemRequest;
import moe.herz.verhaarmbackend.finecatalog.dto.FineCatalogItemDto;
import moe.herz.verhaarmbackend.finecatalog.dto.UpdateFineCatalogItemRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fine-catalog")
public class FineCatalogController {

	private final FineCatalogService catalog;

	public FineCatalogController(FineCatalogService catalog) {
		this.catalog = catalog;
	}

	// Any authenticated user can read the catalog
	// Optional:
	// - /fine-catalog?active=true to only show active items
	// - /fine-catalog?forCreation=true to hide attendance system items (Absent/Late)
	// - can be combined: /fine-catalog?active=true&forCreation=true
	@GetMapping
	public List<FineCatalogItemDto> list(
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Boolean forCreation
	) {
		boolean activeOnly = active != null && active;
		boolean creation = forCreation != null && forCreation;

		return creation
				? catalog.listForManualFineCreation(activeOnly)
				: catalog.listVisible(activeOnly);
	}

	@GetMapping("/{id}")
	public FineCatalogItemDto get(@PathVariable UUID id) {
		return catalog.get(id);
	}

	// ADMIN-only management
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public FineCatalogItemDto create(@RequestBody @Valid CreateFineCatalogItemRequest req) {
		return catalog.create(req);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public FineCatalogItemDto update(@PathVariable UUID id, @RequestBody UpdateFineCatalogItemRequest req) {
		return catalog.update(id, req);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public void delete(@PathVariable UUID id) {
		catalog.delete(id);
	}
}
