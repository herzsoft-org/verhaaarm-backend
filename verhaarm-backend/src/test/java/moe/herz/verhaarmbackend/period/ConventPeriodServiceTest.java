package moe.herz.verhaarmbackend.period;

import moe.herz.verhaarmbackend.event.ConventType;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventKind;
import moe.herz.verhaarmbackend.event.EventOwnerType;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.period.dto.ConventPeriodDto;
import moe.herz.verhaarmbackend.periodprotocol.ConventPeriodProtocolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConventPeriodServiceTest {

	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private final EventRepository events = mock(EventRepository.class);
	private final ConventPeriodProtocolRepository protocols = mock(ConventPeriodProtocolRepository.class);
	private final ConventPeriodService service = new ConventPeriodService(events, protocols);

	private static EventEntity conventEvent(LocalDate date, ConventType type) {
		OffsetDateTime startsAt = date.atStartOfDay(ZONE_BERLIN).toOffsetDateTime();
		EventEntity e = new EventEntity(UUID.randomUUID(), UUID.randomUUID(), "Convent", "adH", startsAt, true, EventKind.MAIN, EventOwnerType.SENIOR);
		e.setConventType(type);
		return e;
	}

	/**
	 * Regression test for the reported "Laufende Semesterferien - Seit 31.01.2027 - Ende noch offen"
	 * mislabel (AGENT_CHAT.md follow-up, 2026-07-25): offices commonly enter a whole semester's
	 * Convente in advance, including its closing Abconvent, well before that semester has even
	 * started. The far-future trailing OPEN_SEMESTER_BREAK projection after that pre-scheduled
	 * Abconvent must never be reported active while "today" actually falls in an earlier, bounded
	 * gap - confirming this part of the bug is not a backend defect.
	 */
	@Test
	void activePeriodIsTheCurrentGapNotTheFarFutureTrailingProjection() {
		LocalDate today = LocalDate.now(ZONE_BERLIN);

		EventEntity ssAb = conventEvent(today.minusMonths(2), ConventType.ABCONVENT); // closed the current summer semester
		EventEntity wsAn = conventEvent(today.plusMonths(2), ConventType.ANCONVENT); // next winter semester, not open yet
		EventEntity wsAb = conventEvent(today.plusMonths(8), ConventType.ABCONVENT); // whole winter semester pre-scheduled, incl. its close

		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(ssAb, wsAn, wsAb));

		List<ConventPeriodDto> all = service.listAll();

		ConventPeriodDto breakBeforeWs = all.stream().filter(p -> wsAn.getId().equals(p.id())).findFirst().orElseThrow();
		ConventPeriodDto wsSemesterItself = all.stream().filter(p -> wsAb.getId().equals(p.id())).findFirst().orElseThrow();
		ConventPeriodDto trailingTail = all.stream().filter(p -> p.id() == null).findFirst().orElseThrow();

		assertTrue(breakBeforeWs.active(), "the gap between SS's Abconvent and WS's Anconvent is where 'today' actually falls");
		assertFalse(wsSemesterItself.active(), "the WS semester itself hasn't started yet");
		assertFalse(trailingTail.active(), "the trailing projection after the far-future pre-scheduled Abconvent must not be reported active");

		assertEquals(PeriodType.SEMESTER_BREAK, breakBeforeWs.periodType());
		assertEquals(PeriodType.OPEN_SEMESTER_BREAK, trailingTail.periodType());

		ConventPeriodDto active = service.getActive();
		assertEquals(breakBeforeWs.id(), active.id());
	}
}
