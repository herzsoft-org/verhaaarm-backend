package moe.herz.verhaarmbackend.period;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.period.dto.ConventPeriodDto;
import moe.herz.verhaarmbackend.period.dto.CreateConventPeriodRequest;
import moe.herz.verhaarmbackend.period.dto.UpdateConventPeriodRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/periods")
public class ConventPeriodController {

	private final ConventPeriodService periods;

	public ConventPeriodController(ConventPeriodService periods) {
		this.periods = periods;
	}

	@GetMapping
	public List<ConventPeriodDto> listAll() {
		return periods.listAll();
	}

	@GetMapping("/{id}")
	public ConventPeriodDto get(@PathVariable UUID id) {
		return periods.get(id);
	}

	@GetMapping("/active")
	public ConventPeriodDto getActive() {
		return periods.getActive();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ConventPeriodDto create(@RequestBody @Valid CreateConventPeriodRequest req) {
		return periods.create(req);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN') or hasRole('SENIOR')")
	public ConventPeriodDto update(@PathVariable UUID id, @RequestBody UpdateConventPeriodRequest req) {
		return periods.update(id, req);
	}

	// kept for compatibility, but it will now return 400 with an explicit message
	@PostMapping("/{id}/activate")
	@PreAuthorize("hasRole('ADMIN') or hasRole('SENIOR')")
	public ConventPeriodDto activate(@PathVariable UUID id) {
		return periods.activate(id);
	}

	@PostMapping("/{id}/lock")
	@PreAuthorize("hasRole('ADMIN') or hasRole('SENIOR')")
	public ConventPeriodDto lock(@PathVariable UUID id) {
		return periods.lock(id);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN') or hasRole('SENIOR')")
	public void delete(@PathVariable UUID id) {
		periods.delete(id);
	}
}
