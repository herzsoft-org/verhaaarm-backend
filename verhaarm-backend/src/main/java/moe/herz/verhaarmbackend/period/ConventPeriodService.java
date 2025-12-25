package moe.herz.verhaarmbackend.period;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.period.dto.ConventPeriodDto;
import moe.herz.verhaarmbackend.period.dto.CreateConventPeriodRequest;
import moe.herz.verhaarmbackend.period.dto.UpdateConventPeriodRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ConventPeriodService {

	private static final Pattern SEMESTER_PATTERN = Pattern.compile("^(WS\\d{2}/\\d{2}|SS\\d{2})$");

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
		var p = periods.findActive().orElseThrow(() -> ApiErrors.notFound("No active period"));
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
				false,
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

		OffsetDateTime startAt = req.startAt() != null ? req.startAt() : p.getStartAt();
		OffsetDateTime endAt = req.endAt() != null ? req.endAt() : p.getEndAt();
		validateDates(startAt, endAt);

		p.setStartAt(startAt);
		p.setEndAt(endAt);

		if (req.locked() != null) {
			if (req.locked() && p.isActive()) {
				throw ApiErrors.badRequest("Cannot lock the active period");
			}
			p.setLocked(req.locked());
		}

		if (req.active() != null) {
			if (req.active()) {
				activateInternal(p);
			} else {
				throw ApiErrors.badRequest("Exactly one active period must exist; activate another period instead");
			}
		}

		try {
			periods.save(p);
		} catch (DataIntegrityViolationException e) {
			// Covers: single-active partial unique index and other DB constraints
			throw ApiErrors.badRequest("Constraint violation (single active period or invalid data)");
		}

		return toDto(p);
	}

	@Transactional
	public ConventPeriodDto activate(UUID id) {
		var p = periods.findById(id).orElseThrow(() -> ApiErrors.notFound("Period not found"));

		activateInternal(p);

		try {
			periods.save(p);
		} catch (DataIntegrityViolationException e) {
			// covers the "single active" partial unique index
			throw ApiErrors.badRequest("Exactly one active period must exist");
		}

		return toDto(p);
	}

	@Transactional
	public ConventPeriodDto lock(UUID id) {
		var p = periods.findById(id).orElseThrow(() -> ApiErrors.notFound("Period not found"));

		if (p.isActive()) {
			throw ApiErrors.badRequest("Cannot lock the active period");
		}

		p.setLocked(true);
		periods.save(p);
		return toDto(p);
	}

	private void activateInternal(ConventPeriodEntity target) {
		if (target.isLocked()) {
			throw ApiErrors.badRequest("Cannot activate a locked period");
		}

		// DB-side bulk update: deactivate any other active period in one statement
		periods.deactivateAllExcept(target.getId());

		target.setActive(true);
	}

	private static void validateDates(OffsetDateTime startAt, OffsetDateTime endAt) {
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

		// Enforce your intended format: WS24/25 or SS25
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
				p.isActive(),
				p.isLocked()
		);
	}
}
