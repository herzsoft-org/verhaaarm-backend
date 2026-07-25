package moe.herz.verhaarmbackend.period;

import moe.herz.verhaarmbackend.common.StructuredApiError;
import moe.herz.verhaarmbackend.event.ConventType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure derivation of Semester/Conventsperioden from the chronological list of Convent-flagged events.
 * Nothing here is persisted: periods and semester labels are recomputed from convent dates/types every
 * time, so moving, adding or removing a Convent never requires a data migration of its own (rules 1-8).
 */
public final class ConventDerivation {

	private ConventDerivation() {}

	/**
	 * DB-safe sentinel bounds for an open-ended range (a null startAt/endAt on a DerivedPeriod).
	 * Postgres' DATE type cannot represent Java's LocalDate.MIN/MAX (year ±999,999,999), so callers
	 * building a date-range query from a period must use these instead.
	 */
	public static final LocalDate DATE_FLOOR = LocalDate.of(1, 1, 1);
	public static final LocalDate DATE_CEIL = LocalDate.of(9999, 12, 31);

	public record ConventRef(UUID id, LocalDate date, ConventType type) {}

	public record DerivedPeriod(
			UUID id,                        // == ending convent id; null only for the trailing OPEN/OPEN_SEMESTER_BREAK period
			LocalDate startAt,               // inclusive; null only for the very first period ever (no predecessor)
			LocalDate endAt,                  // inclusive; null only for the trailing OPEN/OPEN_SEMESTER_BREAK period
			PeriodType periodType,
			ConventType endingConventType,    // null for OPEN/OPEN_SEMESTER_BREAK
			String endingConventLabel,         // null for OPEN/OPEN_SEMESTER_BREAK
			String semester,                    // always present - even mid-Semesterferien this names the upcoming semester
			boolean consistent,
			String warning
	) {}

	/**
	 * Requires convents sorted ascending by date. Always returns one DerivedPeriod per convent
	 * (bounded, ending on that convent) plus exactly one trailing period representing "now, until
	 * the next convent is scheduled": periodType OPEN while a semester is still running, or
	 * OPEN_SEMESTER_BREAK while sitting in Semesterferien with no semester open yet (rule 6) - never
	 * both are "Laufende Conventsperiode" in the UI, only OPEN is. Unless the input is empty, in
	 * which case there is no period at all yet (fresh installs with zero recorded convents).
	 */
	public static List<DerivedPeriod> derive(List<ConventRef> conventsChronological) {
		List<DerivedPeriod> out = new ArrayList<>();

		boolean semesterOpen = false;
		String currentSemesterLabel = null;
		int regularCounter = 0;
		LocalDate prevDate = null;

		for (ConventRef c : conventsChronological) {
			boolean consistent = true;
			String warning = null;
			PeriodType periodType;

			// The list is sorted ascending by date, so a same-day duplicate is always adjacent -
			// flag it (never throw here: derive() only reports, callers decide what to do with it).
			if (prevDate != null && prevDate.equals(c.date())) {
				consistent = false;
				warning = "Shares its date (" + c.date() + ") with another Convent - dates must be unique.";
			}

			switch (c.type()) {
				case ANCONVENT -> {
					if (semesterOpen) {
						consistent = false;
						warning = "Anconvent on " + c.date() + " occurred before the previous semester was closed by an Abconvent. "
								+ "Treated as forcibly starting a new semester - please review.";
					}
					semesterOpen = true;
					regularCounter = 0;
					currentSemesterLabel = computeSemesterLabel(c.date());
					periodType = PeriodType.SEMESTER_BREAK;
				}
				case REGULAR -> {
					if (!semesterOpen) {
						consistent = false;
						warning = "Convent on " + c.date() + " occurred before any Anconvent opened a semester. "
								+ "Semester label is a best-effort guess - please review.";
						currentSemesterLabel = computeSemesterLabel(c.date());
					}
					regularCounter++;
					periodType = PeriodType.CONVENT;
				}
				case ABCONVENT -> {
					if (!semesterOpen) {
						consistent = false;
						warning = "Abconvent on " + c.date() + " occurred before any Anconvent opened a semester. "
								+ "Semester label is a best-effort guess - please review.";
						currentSemesterLabel = computeSemesterLabel(c.date());
					}
					periodType = PeriodType.CONVENT;
					semesterOpen = false;
				}
				default -> throw new IllegalStateException("Unhandled ConventType: " + c.type());
			}

			out.add(new DerivedPeriod(
					c.id(),
					prevDate == null ? null : prevDate.plusDays(1),
					c.date(),
					periodType,
					c.type(),
					label(c.type(), regularCounter),
					currentSemesterLabel,
					consistent,
					warning
			));

			prevDate = c.date();
		}

		if (!conventsChronological.isEmpty()) {
			// Semesters strictly alternate, so even while nothing has opened the next one yet
			// (Semesterferien, rule 6) we can still name it - never leave this null, callers
			// treat "semester" as always-present.
			String openTailSemester = semesterOpen ? currentSemesterLabel : nextSemesterLabel(currentSemesterLabel);

			out.add(new DerivedPeriod(
					null,
					prevDate.plusDays(1),
					null,
					semesterOpen ? PeriodType.OPEN : PeriodType.OPEN_SEMESTER_BREAK,
					null,
					null,
					openTailSemester,
					true,
					null
			));
		}

		return out;
	}

	/**
	 * Compares the full timeline before/after a proposed change and rejects the change if it
	 * would make any Convent that was previously consistent become inconsistent (or leave the
	 * newly created/edited Convent itself inconsistent). Pre-existing inconsistencies (e.g. from
	 * best-effort legacy migration) are left alone and can never block unrelated future writes -
	 * they can only be reduced (a genuine repair) or stay the same, never worsened.
	 *
	 * @param targetId the id of the convent being created/moved/retyped, or null for a delete/unmark
	 */
	public static void validateNoRegression(List<ConventRef> before, List<ConventRef> after, UUID targetId) {
		Map<UUID, Boolean> beforeConsistency = new HashMap<>();
		for (DerivedPeriod p : derive(before)) {
			if (p.id() != null) beforeConsistency.put(p.id(), p.consistent());
		}

		for (DerivedPeriod p : derive(after)) {
			if (p.id() == null) continue; // OPEN tail has no identity, always consistent=true
			if (p.consistent()) continue;

			if (p.id().equals(targetId)) {
				throw StructuredApiError.badRequest(
						"CONVENT_SEQUENCE_INVALID",
						"This would leave the Convent itself in an inconsistent sequence"
								+ (p.warning() != null ? ": " + p.warning() : ""),
						StructuredApiError.details("conventId", p.id(), "isTarget", true)
				);
			}

			Boolean wasConsistent = beforeConsistency.get(p.id());
			if (wasConsistent == null || wasConsistent) {
				throw StructuredApiError.badRequest(
						"CONVENT_SEQUENCE_INVALID",
						"This change would make another Convent's period inconsistent"
								+ (p.warning() != null ? ": " + p.warning() : ""),
						StructuredApiError.details("conventId", p.id(), "isTarget", false)
				);
			}
			// else: already inconsistent before (pre-existing legacy data) and still is - not a regression.
		}
	}

	/**
	 * Every convent id in periodIdsToPreserve must keep the exact same derived date range, type,
	 * label and semester before and after the change - otherwise an already-uploaded Protokoll
	 * would silently start covering the wrong period (e.g. inserting/moving a neighboring Convent
	 * shifts this one's start date even though this convent itself was not touched).
	 */
	public static void validateProtocolsUnaffected(
			List<ConventRef> before,
			List<ConventRef> after,
			Set<UUID> periodIdsToPreserve
	) {
		if (periodIdsToPreserve.isEmpty()) return;

		Map<UUID, DerivedPeriod> beforeById = new HashMap<>();
		for (DerivedPeriod p : derive(before)) {
			if (p.id() != null) beforeById.put(p.id(), p);
		}

		Map<UUID, DerivedPeriod> afterById = new HashMap<>();
		for (DerivedPeriod p : derive(after)) {
			if (p.id() != null) afterById.put(p.id(), p);
		}

		for (UUID id : periodIdsToPreserve) {
			DerivedPeriod b = beforeById.get(id);
			DerivedPeriod a = afterById.get(id);
			if (b == null || !sameRange(b, a)) {
				throw StructuredApiError.badRequest(
						"CONVENT_PROTOCOL_RANGE_WOULD_CHANGE",
						"This change would alter the date range or label of a Convent that already has an "
								+ "uploaded Protokoll (id=" + id + "). Remove that Protokoll first if this change is intentional.",
						StructuredApiError.details("conventId", id)
				);
			}
		}
	}

	private static boolean sameRange(DerivedPeriod a, DerivedPeriod b) {
		if (b == null) return false;
		return Objects.equals(a.startAt(), b.startAt())
				&& Objects.equals(a.endAt(), b.endAt())
				&& a.periodType() == b.periodType()
				&& a.endingConventType() == b.endingConventType()
				&& Objects.equals(a.endingConventLabel(), b.endingConventLabel())
				&& Objects.equals(a.semester(), b.semester());
	}

	/** "WS25/26" -> "SS26", "SS26" -> "WS26/27". Semesters strictly alternate (rule 6). */
	static String nextSemesterLabel(String label) {
		if (label == null) return null;

		if (label.startsWith("SS")) {
			int yy = Integer.parseInt(label.substring(2, 4));
			return "WS" + twoDigit(yy) + "/" + twoDigit((yy + 1) % 100);
		}

		// WSyy/yy+1 -> the next semester is Summer of the *second* year in the label.
		int slash = label.indexOf('/');
		int endYy = Integer.parseInt(label.substring(slash + 1, slash + 3));
		return "SS" + twoDigit(endYy);
	}

	/**
	 * Jan-Jun => Sommersemester, Jul-Dec => Wintersemester, based on the Anconvent's date.
	 * Deliberately far from the nominal Apr/Oct semester-start months so an Anconvent that
	 * drifts a few weeks earlier or later (as the user described) still classifies correctly -
	 * the cutover sits in the middle of each Semesterferien gap instead of at a semester boundary.
	 */
	static String computeSemesterLabel(LocalDate anconventDate) {
		int y = anconventDate.getYear();
		int m = anconventDate.getMonthValue();

		if (m >= 1 && m <= 6) {
			return "SS" + twoDigit(y % 100);
		}

		int a = y % 100;
		int b = (y + 1) % 100;
		return "WS" + twoDigit(a) + "/" + twoDigit(b);
	}

	private static String twoDigit(int v) {
		return String.format(Locale.ROOT, "%02d", v);
	}

	private static String label(ConventType type, int regularCounter) {
		return switch (type) {
			case ANCONVENT -> "Anconvent";
			case ABCONVENT -> "Abconvent";
			case REGULAR -> regularCounter + ". Convent";
		};
	}
}
