package moe.herz.verhaarmbackend.period;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.period.dto.ConventPeriodDto;
import moe.herz.verhaarmbackend.periodprotocol.ConventPeriodProtocolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Conventsperioden and Semester are fully derived from the chronological list of Convent-flagged
 * events (see ConventDerivation) - there is nothing to create/update/lock here anymore, only to read.
 */
@Service
public class ConventPeriodService {

	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private final EventRepository events;
	private final ConventPeriodProtocolRepository protocols;

	public ConventPeriodService(EventRepository events, ConventPeriodProtocolRepository protocols) {
		this.events = events;
		this.protocols = protocols;
	}

	@Transactional(readOnly = true)
	public List<ConventPeriodDto> listAll() {
		return derivePeriods().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public ConventPeriodDto get(UUID id) {
		return derivePeriods().stream()
				.filter(p -> id.equals(p.id()))
				.findFirst()
				.map(this::toDto)
				.orElseThrow(() -> ApiErrors.notFound("Period not found"));
	}

	@Transactional(readOnly = true)
	public ConventPeriodDto getActive() {
		LocalDate today = LocalDate.now(ZONE_BERLIN);

		return derivePeriods().stream()
				.filter(p -> covers(p, today))
				.findFirst()
				.map(this::toDto)
				.orElseThrow(() -> ApiErrors.notFound("No active period for today"));
	}

	private List<ConventDerivation.DerivedPeriod> derivePeriods() {
		List<ConventDerivation.ConventRef> refs = events.findAllConventsOrderedVisible().stream()
				.map(ConventPeriodService::toRef)
				.toList();
		return ConventDerivation.derive(refs);
	}

	private static ConventDerivation.ConventRef toRef(EventEntity e) {
		return new ConventDerivation.ConventRef(
				e.getId(),
				e.getStartsAt().atZoneSameInstant(ZONE_BERLIN).toLocalDate(),
				e.getConventType()
		);
	}

	private static boolean covers(ConventDerivation.DerivedPeriod p, LocalDate d) {
		boolean afterOrEqualStart = p.startAt() == null || !p.startAt().isAfter(d);
		boolean beforeOrEqualEnd = p.endAt() == null || !p.endAt().isBefore(d);
		return afterOrEqualStart && beforeOrEqualEnd;
	}

	private ConventPeriodDto toDto(ConventDerivation.DerivedPeriod p) {
		LocalDate today = LocalDate.now(ZONE_BERLIN);
		boolean hasProtocol = p.id() != null && protocols.existsByPeriodId(p.id());

		return new ConventPeriodDto(
				p.id(),
				p.semester(),
				p.startAt(),
				p.endAt(),
				covers(p, today),
				hasProtocol,
				p.periodType(),
				p.endingConventType(),
				p.endingConventLabel(),
				p.consistent(),
				p.warning()
		);
	}
}
