package moe.herz.verhaarmbackend.period;

import moe.herz.verhaarmbackend.common.ApiValidationException;
import moe.herz.verhaarmbackend.event.ConventType;
import moe.herz.verhaarmbackend.period.ConventDerivation.ConventRef;
import moe.herz.verhaarmbackend.period.ConventDerivation.DerivedPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConventDerivationTest {

	private static ConventRef ref(LocalDate date, ConventType type) {
		return new ConventRef(UUID.randomUUID(), date, type);
	}

	@Test
	void emptyInputYieldsNoPeriods() {
		assertTrue(ConventDerivation.derive(List.of()).isEmpty());
	}

	@Test
	void singleSemesterBlockProducesExpectedPeriodsAndLabels() {
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef c1 = ref(LocalDate.of(2025, 11, 3), ConventType.REGULAR);
		ConventRef c2 = ref(LocalDate.of(2025, 12, 1), ConventType.REGULAR);
		ConventRef ab = ref(LocalDate.of(2026, 1, 26), ConventType.ABCONVENT);

		List<DerivedPeriod> periods = ConventDerivation.derive(List.of(an, c1, c2, ab));

		// 4 bounded periods + 1 trailing OPEN period
		assertEquals(5, periods.size());

		DerivedPeriod anPeriod = periods.get(0);
		assertNull(anPeriod.startAt(), "very first period ever has no predecessor");
		assertEquals(an.date(), anPeriod.endAt());
		assertEquals(PeriodType.SEMESTER_BREAK, anPeriod.periodType());
		assertEquals("Anconvent", anPeriod.endingConventLabel());
		assertEquals("WS25/26", anPeriod.semester());
		assertTrue(anPeriod.consistent());

		DerivedPeriod c1Period = periods.get(1);
		assertEquals(an.date().plusDays(1), c1Period.startAt());
		assertEquals(c1.date(), c1Period.endAt());
		assertEquals(PeriodType.CONVENT, c1Period.periodType());
		assertEquals("1. Convent", c1Period.endingConventLabel());
		assertEquals("WS25/26", c1Period.semester());

		DerivedPeriod c2Period = periods.get(2);
		assertEquals("2. Convent", c2Period.endingConventLabel());

		DerivedPeriod abPeriod = periods.get(3);
		assertEquals("Abconvent", abPeriod.endingConventLabel());
		assertEquals(PeriodType.CONVENT, abPeriod.periodType());
		assertEquals("WS25/26", abPeriod.semester());

		DerivedPeriod open = periods.get(4);
		assertNull(open.id());
		assertEquals(ab.date().plusDays(1), open.startAt());
		assertNull(open.endAt());
		assertEquals(
				PeriodType.OPEN_SEMESTER_BREAK,
				open.periodType(),
				"an Abconvent just closed the semester, so the open tail is Semesterferien, not a running Conventsperiode"
		);
		assertEquals(
				"SS26",
				open.semester(),
				"semester is never null - Semesterferien after WS25/26 predicts the upcoming SS26"
		);
		assertTrue(open.consistent());
	}

	@Test
	void openPeriodInsideSemesterCarriesTheOpenSemesterLabel() {
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef c1 = ref(LocalDate.of(2025, 11, 3), ConventType.REGULAR);

		List<DerivedPeriod> periods = ConventDerivation.derive(List.of(an, c1));
		DerivedPeriod open = periods.getLast();

		assertEquals(PeriodType.OPEN, open.periodType());
		assertEquals("WS25/26", open.semester());
	}

	@Test
	void movedConventJustShiftsAdjacentPeriodBoundaries() {
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef c1 = ref(LocalDate.of(2025, 11, 3), ConventType.REGULAR);
		ConventRef ab = ref(LocalDate.of(2026, 1, 26), ConventType.ABCONVENT);

		List<DerivedPeriod> before = ConventDerivation.derive(List.of(an, c1, ab));

		ConventRef c1Moved = new ConventRef(c1.id(), LocalDate.of(2025, 11, 10), ConventType.REGULAR);
		List<DerivedPeriod> after = ConventDerivation.derive(List.of(an, c1Moved, ab));

		assertEquals(before.size(), after.size());
		DerivedPeriod movedPeriod = after.get(1);
		assertEquals(an.date().plusDays(1), movedPeriod.startAt());
		assertEquals(c1Moved.date(), movedPeriod.endAt());

		DerivedPeriod abPeriodAfter = after.get(2);
		assertEquals(c1Moved.date().plusDays(1), abPeriodAfter.startAt(), "Abconvent's period start follows the moved convent");
	}

	@Test
	void regularConventBeforeAnyAnconventIsFlaggedInconsistent() {
		ConventRef orphan = ref(LocalDate.of(2025, 11, 3), ConventType.REGULAR);

		List<DerivedPeriod> periods = ConventDerivation.derive(List.of(orphan));

		DerivedPeriod p = periods.getFirst();
		assertFalse(p.consistent());
		assertNotNull(p.warning());
		assertNotNull(p.semester(), "best-effort label is still computed, not left null");
	}

	@Test
	void secondAnconventWithoutClosingAbconventIsFlaggedInconsistentButRecovers() {
		ConventRef an1 = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef an2 = ref(LocalDate.of(2025, 11, 3), ConventType.ANCONVENT);
		ConventRef ab = ref(LocalDate.of(2026, 1, 26), ConventType.ABCONVENT);

		List<DerivedPeriod> periods = ConventDerivation.derive(List.of(an1, an2, ab));

		assertTrue(periods.get(0).consistent());
		assertFalse(periods.get(1).consistent(), "second Anconvent opened before the first semester was closed");
		assertNotNull(periods.get(1).warning());
		assertTrue(periods.get(2).consistent(), "Abconvent correctly closes the (forcibly re-opened) semester");
	}

	@Test
	void deriveFlagsTwoConventsSharingADateAsInconsistentRatherThanThrowing() {
		LocalDate day = LocalDate.of(2025, 11, 3);
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef first = ref(day, ConventType.REGULAR);
		ConventRef second = ref(day, ConventType.ABCONVENT);

		List<DerivedPeriod> periods = assertDoesNotThrow(() -> ConventDerivation.derive(List.of(an, first, second)));

		DerivedPeriod firstPeriod = periods.stream().filter(p -> first.id().equals(p.id())).findFirst().orElseThrow();
		DerivedPeriod secondPeriod = periods.stream().filter(p -> second.id().equals(p.id())).findFirst().orElseThrow();

		assertFalse(secondPeriod.consistent(), "the later of the two same-day convents is flagged");
		assertNotNull(secondPeriod.warning());
		assertTrue(firstPeriod.consistent(), "the earlier one is unaffected on its own");
	}

	@Test
	void validateNoRegressionRejectsCreatingANewConventOnAnAlreadyUsedDate() {
		ConventRef existing = ref(LocalDate.of(2025, 11, 3), ConventType.ANCONVENT);
		List<ConventRef> before = List.of(existing);

		UUID newId = UUID.randomUUID();
		ConventRef duplicate = new ConventRef(newId, existing.date(), ConventType.REGULAR);
		List<ConventRef> after = List.of(existing, duplicate);

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> ConventDerivation.validateNoRegression(before, after, newId));
		assertEquals("CONVENT_SEQUENCE_INVALID", ex.getCode());
	}

	@Test
	void validateNoRegressionGrandfathersAPreExistingDuplicateDateFromLegacyData() {
		// Two legacy rows that ended up sharing a date (the old schema never enforced uniqueness on
		// end_date) must not brick every future unrelated write forever.
		LocalDate sharedDay = LocalDate.of(2020, 3, 1);
		ConventRef legacy1 = ref(sharedDay, ConventType.REGULAR);
		ConventRef legacy2 = ref(sharedDay, ConventType.ABCONVENT);
		List<ConventRef> before = List.of(legacy1, legacy2);

		UUID newAnId = UUID.randomUUID();
		ConventRef newAn = new ConventRef(newAnId, LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		List<ConventRef> after = List.of(legacy1, legacy2, newAn);

		assertDoesNotThrow(() -> ConventDerivation.validateNoRegression(before, after, newAnId));
	}

	@Test
	void validateNoRegressionAcceptsACleanNewBlock() {
		List<ConventRef> before = List.of();
		UUID anId = UUID.randomUUID();
		List<ConventRef> after = List.of(new ConventRef(anId, LocalDate.of(2025, 10, 6), ConventType.ANCONVENT));

		assertDoesNotThrow(() -> ConventDerivation.validateNoRegression(before, after, anId));
	}

	@Test
	void validateNoRegressionRejectsARegularConventBeforeAnyAnconvent() {
		UUID id = UUID.randomUUID();
		List<ConventRef> after = List.of(new ConventRef(id, LocalDate.of(2025, 11, 3), ConventType.REGULAR));

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> ConventDerivation.validateNoRegression(List.of(), after, id));
		assertEquals(400, ex.getStatus().value());
		assertEquals("CONVENT_SEQUENCE_INVALID", ex.getCode());
	}

	@Test
	void validateNoRegressionRejectsDeletingAnAnconventThatWouldOrphanALaterAbconvent() {
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef ab = ref(LocalDate.of(2026, 1, 26), ConventType.ABCONVENT);
		List<ConventRef> before = List.of(an, ab);
		List<ConventRef> after = List.of(ab); // an deleted, target=null

		assertThrows(ApiValidationException.class,
				() -> ConventDerivation.validateNoRegression(before, after, (UUID) null));
	}

	@Test
	void validateNoRegressionDoesNotBlockAnUnrelatedCleanWriteJustBecauseSomeLegacyDataIsInconsistent() {
		// Reproduces the deadlock bug: a leftover inconsistent legacy orphan (e.g. from migration)
		// must never block an otherwise perfectly clean new AN..AB block elsewhere on the timeline.
		ConventRef legacyOrphan = ref(LocalDate.of(2020, 3, 1), ConventType.ABCONVENT); // no preceding Anconvent, forever inconsistent

		UUID newAnId = UUID.randomUUID();
		ConventRef newAn = new ConventRef(newAnId, LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);

		List<ConventRef> before = List.of(legacyOrphan);
		List<ConventRef> after = List.of(legacyOrphan, newAn);

		assertDoesNotThrow(() -> ConventDerivation.validateNoRegression(before, after, newAnId));
	}

	@Test
	void validateNoRegressionAllowsARepairThatFixesALegacyOrphan() {
		ConventRef legacyOrphan = ref(LocalDate.of(2025, 1, 26), ConventType.ABCONVENT); // no preceding Anconvent

		UUID repairAnId = UUID.randomUUID();
		ConventRef repairAn = new ConventRef(repairAnId, LocalDate.of(2024, 10, 6), ConventType.ANCONVENT);

		List<ConventRef> before = List.of(legacyOrphan);
		List<ConventRef> after = List.of(repairAn, legacyOrphan);

		assertDoesNotThrow(() -> ConventDerivation.validateNoRegression(before, after, repairAnId));

		// and the orphan really did become consistent as a side effect
		var afterPeriods = ConventDerivation.derive(after);
		assertTrue(afterPeriods.stream().filter(p -> legacyOrphan.id().equals(p.id())).findFirst().orElseThrow().consistent());
	}

	@Test
	void validateNoRegressionRejectsBreakingAPreviouslyConsistentNeighbor() {
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef ab = ref(LocalDate.of(2026, 1, 26), ConventType.ABCONVENT);
		List<ConventRef> before = List.of(an, ab);

		// Retyping the Abconvent into a second Anconvent breaks the previously-valid block.
		ConventRef retyped = new ConventRef(ab.id(), ab.date(), ConventType.ANCONVENT);
		List<ConventRef> after = List.of(an, retyped);

		assertThrows(ApiValidationException.class,
				() -> ConventDerivation.validateNoRegression(before, after, ab.id()));
	}

	@Test
	void validateProtocolsUnaffectedRejectsInsertingAConventBeforeAnAlreadyProtocolledOne() {
		// Reproduces the "indirect neighbor" bug: inserting C0 between AN and C1 shifts C1's
		// derived start date even though C1 itself was never touched.
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef c1 = ref(LocalDate.of(2025, 11, 20), ConventType.REGULAR);
		List<ConventRef> before = List.of(an, c1);

		ConventRef c0 = ref(LocalDate.of(2025, 11, 3), ConventType.REGULAR);
		List<ConventRef> after = List.of(an, c0, c1);

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> ConventDerivation.validateProtocolsUnaffected(before, after, Set.of(c1.id())));
		assertEquals("CONVENT_PROTOCOL_RANGE_WOULD_CHANGE", ex.getCode());
	}

	@Test
	void validateProtocolsUnaffectedAllowsChangesThatDoNotTouchTheProtocolledPeriod() {
		ConventRef an = ref(LocalDate.of(2025, 10, 6), ConventType.ANCONVENT);
		ConventRef c1 = ref(LocalDate.of(2025, 11, 20), ConventType.REGULAR);
		List<ConventRef> before = List.of(an, c1);

		ConventRef c2 = ref(LocalDate.of(2025, 12, 15), ConventType.REGULAR);
		List<ConventRef> after = List.of(an, c1, c2);

		assertDoesNotThrow(() -> ConventDerivation.validateProtocolsUnaffected(before, after, Set.of(c1.id())));
	}

	@Test
	void nextSemesterLabelAlternatesWinterAndSummer() {
		assertEquals("SS26", ConventDerivation.nextSemesterLabel("WS25/26"));
		assertEquals("WS26/27", ConventDerivation.nextSemesterLabel("SS26"));
	}

	@Test
	void computeSemesterLabelUsesJanJuneJulyDecemberCutover() {
		assertEquals("SS25", ConventDerivation.computeSemesterLabel(LocalDate.of(2025, 1, 1)));
		assertEquals("SS25", ConventDerivation.computeSemesterLabel(LocalDate.of(2025, 6, 30)));
		assertEquals("WS25/26", ConventDerivation.computeSemesterLabel(LocalDate.of(2025, 7, 1)));
		assertEquals("WS25/26", ConventDerivation.computeSemesterLabel(LocalDate.of(2025, 12, 31)));
		// An Anconvent drifting a few weeks earlier than the nominal October start still resolves to WS.
		assertEquals("WS25/26", ConventDerivation.computeSemesterLabel(LocalDate.of(2025, 9, 15)));
	}
}
