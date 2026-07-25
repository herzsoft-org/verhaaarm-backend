package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-time conversion from the old manually-managed convent_periods (free-text semester label +
 * manual start/end date) to the new model where a Conventsperiode is derived from Convent-flagged
 * events (see moe.herz.verhaarmbackend.period.ConventDerivation).
 * <p>
 * For every legacy period we need a Convent event dated on its end_date (rule 5: a period ends on
 * the day of the convent that closes it). Type (Anconvent/Convent/Abconvent) is inferred from the
 * period's position within its legacy semester-label group (first/last/middle; a group of exactly
 * one is typed Abconvent - see AGENT_CHAT.md / user Q&A for why singleton semesters could not be
 * confirmed as genuinely combined An-/Abconvente). The resulting global chronological sequence is
 * NOT trusted blindly: whatever comes out of this best-effort mapping is re-validated on every read
 * by ConventDerivation, which flags anything that doesn't resolve into clean semester blocks
 * (consistent=false + a warning) rather than silently presenting wrong data.
 * <p>
 * If an existing (non-deleted) event already sits on a legacy period's end_date AND its title
 * credibly looks like a Convent (contains "convent", case-insensitive), that real event is tagged
 * instead of creating a duplicate - its id becomes the new "period id" and any protocol PDF for
 * that legacy period is repointed (DB row + best-effort on-disk directory rename) to it. An
 * unrelated same-day event (e.g. a Kneipe) is never guessed at; if there is no credible single
 * match, a placeholder Convent event is synthesized instead, reusing the legacy period's own id so
 * existing protocol rows/directories need no change at all.
 */
public class V49__migrate_convent_periods_to_convents extends BaseJavaMigration {

	private static final Log LOG = LogFactory.getLog(V49__migrate_convent_periods_to_convents.class);
	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private record LegacyPeriod(UUID id, String semester, LocalDate endDate) {}

	@Override
	public void migrate(Context context) throws Exception {
		Connection conn = context.getConnection();

		List<LegacyPeriod> periods = loadLegacyPeriods(conn);

		dropProtocolFkToConventPeriods(conn);

		if (!periods.isEmpty()) {
			UUID fallbackCreator = findFallbackCreatorUserId(conn);
			Path baseDir = resolveUploadsBaseDir();

			Map<String, List<LegacyPeriod>> bySemester = new LinkedHashMap<>();
			for (LegacyPeriod p : periods) {
				bySemester.computeIfAbsent(p.semester(), k -> new ArrayList<>()).add(p);
			}
			for (List<LegacyPeriod> group : bySemester.values()) {
				group.sort(Comparator.comparing(LegacyPeriod::endDate));
			}

			for (List<LegacyPeriod> group : bySemester.values()) {
				for (int i = 0; i < group.size(); i++) {
					LegacyPeriod p = group.get(i);
					String type;
					if (group.size() == 1) {
						type = "ABCONVENT";
					} else if (i == 0) {
						type = "ANCONVENT";
					} else if (i == group.size() - 1) {
						type = "ABCONVENT";
					} else {
						type = "REGULAR";
					}

					convertOnePeriod(conn, p, type, fallbackCreator, baseDir);
				}
			}
		}

		dropConventPeriodsTable(conn);
		addProtocolFkToEvents(conn);
	}

	private void convertOnePeriod(Connection conn, LegacyPeriod p, String type, UUID fallbackCreator, Path baseDir) throws Exception {
		UUID conventId = findMatchingVisibleEvent(conn, p.endDate());

		if (conventId == null) {
			conventId = p.id();
			insertSyntheticConventEvent(conn, conventId, p.endDate(), type, fallbackCreator);
			LOG.info("Convent migration: synthesized " + type + " event " + conventId + " on " + p.endDate()
					+ " for legacy period " + p.id());
		} else {
			setConventType(conn, conventId, type);
			LOG.info("Convent migration: tagged existing event " + conventId + " on " + p.endDate()
					+ " as " + type + " for legacy period " + p.id());

			if (!conventId.equals(p.id())) {
				repointProtocol(conn, p.id(), conventId, baseDir);
			}
		}
	}

	// --------------------
	// Reads
	// --------------------

	private List<LegacyPeriod> loadLegacyPeriods(Connection conn) throws Exception {
		List<LegacyPeriod> out = new ArrayList<>();
		if (!tableExists(conn, "convent_periods")) return out;

		try (Statement st = conn.createStatement();
			 ResultSet rs = st.executeQuery("select id, semester, end_date from convent_periods order by end_date asc")) {
			while (rs.next()) {
				out.add(new LegacyPeriod(
						UUID.fromString(rs.getString("id")),
						rs.getString("semester"),
						rs.getObject("end_date", LocalDate.class)
				));
			}
		}
		return out;
	}

	private UUID findMatchingVisibleEvent(Connection conn, LocalDate date) throws Exception {
		// Only trust an existing same-day event if its title is credibly a Convent - grabbing an
		// arbitrary unrelated event that merely happens to fall on the same day (e.g. a Kneipe)
		// would silently mislabel it. Ambiguous (multiple credible matches) also falls back to
		// synthesizing a placeholder rather than guessing which one is right.
		//
		// convent_type IS NULL excludes events already claimed earlier in this same migration run
		// (or genuinely pre-existing convents) - the legacy schema never enforced a unique end_date
		// on convent_periods, so two legacy rows can share a date; without this guard the second row
		// would silently re-tag/overwrite the first row's already-converted event instead of getting
		// its own. Two distinct legacy rows always end up as two distinct (flagged-inconsistent,
		// same-day) convent events - never silently merged.
		String sql = """
			select id, title from events
			where deleted_at is null
			  and convent_type is null
			  and (starts_at at time zone 'Europe/Berlin')::date = ?
			order by starts_at asc
		""";

		UUID credibleMatch = null;
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, date);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String title = rs.getString("title");
					if (title == null || !title.toLowerCase(java.util.Locale.ROOT).contains("convent")) continue;

					if (credibleMatch != null) {
						LOG.warn("Convent migration: multiple candidate events titled like a Convent on " + date
								+ " - synthesizing a placeholder instead of guessing");
						return null;
					}
					credibleMatch = UUID.fromString(rs.getString("id"));
				}
			}
		}
		return credibleMatch;
	}

	private UUID findFallbackCreatorUserId(Connection conn) throws Exception {
		UUID admin = findFirstUserWithRole(conn, "ADMIN");
		if (admin != null) return admin;

		UUID senior = findFirstUserWithRole(conn, "SENIOR");
		if (senior != null) return senior;

		try (Statement st = conn.createStatement();
			 ResultSet rs = st.executeQuery("select id from users order by created_at asc limit 1")) {
			if (rs.next()) return UUID.fromString(rs.getString("id"));
		}

		throw new IllegalStateException(
				"Cannot migrate legacy convent_periods: no user exists to use as the creator of synthesized Convent events");
	}

	private UUID findFirstUserWithRole(Connection conn, String role) throws Exception {
		try (PreparedStatement ps = conn.prepareStatement(
				"select user_id from user_roles where role = ? order by user_id asc limit 1")) {
			ps.setString(1, role);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? UUID.fromString(rs.getString("user_id")) : null;
			}
		}
	}

	// --------------------
	// Writes
	// --------------------

	private void setConventType(Connection conn, UUID eventId, String type) throws Exception {
		try (PreparedStatement ps = conn.prepareStatement("update events set convent_type = ? where id = ?")) {
			ps.setString(1, type);
			ps.setObject(2, eventId);
			ps.executeUpdate();
		}
	}

	private void insertSyntheticConventEvent(Connection conn, UUID id, LocalDate date, String type, UUID creatorUserId) throws Exception {
		ZonedDateTime zdt = date.atTime(LocalTime.of(19, 0)).atZone(ZONE_BERLIN);
		String title = switch (type) {
			case "ANCONVENT" -> "Anconvent";
			case "ABCONVENT" -> "Abconvent";
			default -> "Convent";
		};

		String sql = """
			insert into events (id, creator_user_id, title, starts_at, mandatory, event_kind, owner_type, convent_type)
			values (?, ?, ?, ?, true, 'MAIN', 'SENIOR', ?)
		""";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, id);
			ps.setObject(2, creatorUserId);
			ps.setString(3, title);
			ps.setTimestamp(4, Timestamp.from(zdt.toInstant()));
			ps.setString(5, type);
			ps.executeUpdate();
		}
	}

	private void repointProtocol(Connection conn, UUID oldPeriodId, UUID newConventId, Path baseDir) throws Exception {
		int updated;
		try (PreparedStatement ps = conn.prepareStatement(
				"update convent_period_protocols set period_id = ? where period_id = ?")) {
			ps.setObject(1, newConventId);
			ps.setObject(2, oldPeriodId);
			updated = ps.executeUpdate();
		}

		if (updated == 0) return;

		try {
			Path from = baseDir.resolve(oldPeriodId.toString()).normalize();
			Path to = baseDir.resolve(newConventId.toString()).normalize();
			if (Files.exists(from)) {
				Files.createDirectories(baseDir);
				Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
				LOG.info("Convent migration: moved protocol upload dir " + from + " -> " + to);
			}
		} catch (IOException e) {
			// Best-effort, same philosophy as ConventPeriodProtocolService's own disk cleanup:
			// the DB is the source of truth, a stale/missing directory just needs an ops follow-up.
			LOG.warn("Convent migration: could not move protocol upload dir for " + oldPeriodId
					+ " -> " + newConventId + ": " + e.getMessage());
		}
	}

	private Path resolveUploadsBaseDir() {
		String configured = System.getProperty("verhaarm.uploads.period-protocols.dir");
		if (configured == null || configured.isBlank()) {
			configured = System.getenv("VERHAARM_UPLOADS_PERIOD_PROTOCOLS_DIR");
		}
		if (configured == null || configured.isBlank()) {
			configured = "/var/lib/verhaarm/uploads/period-protocols";
		}
		return Paths.get(configured).toAbsolutePath().normalize();
	}

	// --------------------
	// Schema
	// --------------------

	private void dropProtocolFkToConventPeriods(Connection conn) throws Exception {
		String findConstraint = """
			select tc.constraint_name
			from information_schema.table_constraints tc
			join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
			where tc.table_name = 'convent_period_protocols'
			  and tc.constraint_type = 'FOREIGN KEY'
			  and kcu.column_name = 'period_id'
		""";

		String constraintName = null;
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(findConstraint)) {
			if (rs.next()) constraintName = rs.getString("constraint_name");
		}

		if (constraintName != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("alter table convent_period_protocols drop constraint \"" + constraintName + "\"");
			}
		}
	}

	private void addProtocolFkToEvents(Connection conn) throws Exception {
		try (Statement st = conn.createStatement()) {
			st.execute("""
				alter table convent_period_protocols
					add constraint fk_convent_period_protocols_event
					foreign key (period_id) references events(id) on delete cascade
			""");
		}
	}

	private void dropConventPeriodsTable(Connection conn) throws Exception {
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists convent_periods");
		}
	}

	private boolean tableExists(Connection conn, String tableName) throws Exception {
		try (PreparedStatement ps = conn.prepareStatement(
				"select 1 from information_schema.tables where table_name = ?")) {
			ps.setString(1, tableName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}
}
