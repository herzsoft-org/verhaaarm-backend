package moe.herz.verhaarmbackend.period;

import moe.herz.verhaarmbackend.period.dto.ConventPeriodDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Read-only: Conventsperioden are fully derived from Convent-flagged events (see ConventDerivation).
 * To change a period, create/move/retype/delete the underlying Convent via /events instead.
 */
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
}
