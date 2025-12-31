package moe.herz.verhaarmbackend.period;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.period.dto.ConventPeriodDto;
import moe.herz.verhaarmbackend.period.dto.CreateConventPeriodRequest;
import moe.herz.verhaarmbackend.period.dto.UpdateConventPeriodRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ConventPeriodService {

	private static final Pattern SEMESTER_PATTERN = Pattern.compile("^(WS\\d{2}/\\d{2}|SS\\d{2})$");
	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private final ConventPeriodRepository periods;

	public ConventPeriodService(ConventPeriodRepository periods) {
		this.periods = periods;
	}

	@Transactional(readOnly = true)
	public List<ConventPeriodDto> listAll() {
		return periods.findAllOrdered().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public ConventPeriodDto get(UUID id) {
		var p = periods.findById(id).orElseThrow(() -> ApiErrors.notFound("Period not found"));
		return toDto(p);
	}

	@Transactional(readOnly = true)
	public ConventPeriodDto getActive() {
		LocalDate today = LocalDate.now(ZONE_BERLIN);
		var p = periods.findCovering(today).orElseThrow(() -> ApiErrors.notFound("No active period for today"));
		return toDto(p);
	}

	@Transactional
	public ConventPeriodDto create(CreateConventPeriodRequest req) {
		String semester = normalizeAndValidateSemester(req.semester());
		validateDates(req.startAt(), req.endAt());

		var p = new ConventPeriodEntity(
				UUID.randomUUID(),
				semester,
				req.startAt(),
				req.endAt(),
				false
		);

		periods.save(p);
		return toDto(p);
	}

	@Transactional
	public ConventPeriodDto update(UUID id, UpdateConventPeriodRequest req) {
		var p = periods.findById(id).orElseThrow(() -> ApiErrors.notFound("Period not found"));

		if (req.semester() != null && !req.semester().isBlank()) {
			String semester = normalizeAndValidateSemester(req.semester());
			p.setSemester(semester);
		}

		LocalDate startAt = req.startAt() != null ? req.startAt() : p.getStartAt();
		LocalDate endAt = req.endAt() != null ? req.endAt() : p.getEndAt();
		validateDates(startAt, endAt);

		p.setStartAt(startAt);
		p.setEndAt(endAt);

		if (req.locked() != null) {
			p.setLocked(req.locked());
		}

		try {
			periods.save(p);
		} catch (DataIntegrityViolationException e) {
			throw ApiErrors.badRequest("Constraint violation (invalid data)");
		}

		return toDto(p);
	}

	// Active is automatic now; keep the endpoint but make it explicit.
	@Transactional
	public ConventPeriodDto activate(UUID id) {
		throw ApiErrors.badRequest("Active period is determined automatically by current date; manual activation is disabled");
	}

	@Transactional
	public ConventPeriodDto lock(UUID id) {
		var p = periods.findById(id).orElseThrow(() -> ApiErrors.notFound("Period not found"));
		p.setLocked(true);
		periods.save(p);
		return toDto(p);
	}

	@Transactional
	public void delete(UUID id) {
		var p = periods.findById(id).orElseThrow(() -> ApiErrors.notFound("Period not found"));

		// Deletion is allowed; no "active" invariant anymore.
		try {
			periods.delete(p);
			periods.flush();
		} catch (DataIntegrityViolationException e) {
			throw ApiErrors.badRequest("Cannot delete period due to existing references");
		}
	}

	private static void validateDates(LocalDate startAt, LocalDate endAt) {
		if (startAt == null || endAt == null) {
			throw ApiErrors.badRequest("startAt and endAt are required");
		}
		if (!startAt.isBefore(endAt)) {
			throw ApiErrors.badRequest("startAt must be before endAt");
		}
	}

	private static String normalizeAndValidateSemester(String semester) {
		if (semester == null) {
			throw ApiErrors.badRequest("semester is required");
		}
		String s = semester.trim().toUpperCase(Locale.ROOT);

		if (!SEMESTER_PATTERN.matcher(s).matches()) {
			throw ApiErrors.badRequest("Invalid semester format. Use WS24/25 or SS25");
		}

		return s;
	}

	private ConventPeriodDto toDto(ConventPeriodEntity p) {
		return new ConventPeriodDto(
				p.getId(),
				p.getSemester(),
				p.getStartAt(),
				p.getEndAt(),
				// active is computed: "true if it covers today"
				isActiveToday(p),
				p.isLocked()
		);
	}

	private boolean isActiveToday(ConventPeriodEntity p) {
		LocalDate today = LocalDate.now(ZONE_BERLIN);
		return !p.getStartAt().isAfter(today) && !p.getEndAt().isBefore(today);
	}
}
