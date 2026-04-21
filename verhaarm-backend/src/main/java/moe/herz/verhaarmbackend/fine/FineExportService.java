package moe.herz.verhaarmbackend.fine;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.period.ConventPeriodEntity;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FineExportService {

	private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private final FineRepository fines;
	private final ConventPeriodRepository periods;
	private final UserRepository users;

	public FineExportService(FineRepository fines, ConventPeriodRepository periods, UserRepository users) {
		this.fines = fines;
		this.periods = periods;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public ExportResult exportCsv(UUID periodIdOrNull, boolean includeDeleted, UserEntity actor) {
		requireExportRole(actor);

		ConventPeriodEntity period = resolvePeriod(periodIdOrNull);

		LocalDate fromDate = period.getStartAt();
		LocalDate toDateExclusive = period.getEndAt().plusDays(1);

		List<FineEntity> rows = includeDeleted
				? fines.findAllIncludingDeletedInDateRangeWithTargets(fromDate, toDateExclusive)
				: fines.findVisibleInDateRangeWithTargets(fromDate, toDateExclusive);

		Set<UUID> userIds = new HashSet<>();
		for (FineEntity f : rows) {
			if (f.getCreatorUserId() != null) {
				userIds.add(f.getCreatorUserId());
			}
			userIds.addAll(f.getTargetUserIds());
		}

		Map<UUID, UserEntity> userById = users.findAllById(userIds).stream()
				.collect(Collectors.toMap(UserEntity::getId, u -> u));

		StringBuilder sb = new StringBuilder(64 * 1024);

		sb.append('\uFEFF'); // UTF-8 BOM for Excel

		sb.append("Semester;Datum;ErstelltAm;ErstelltVon;Betrag;Grund;BetroffeneUsernames;BetroffeneNamen")
				.append("\r\n");

		for (FineEntity f : rows) {
			UserEntity creator = userById.get(f.getCreatorUserId());

			List<UserEntity> targets = f.getTargetUserIds().stream()
					.map(userById::get)
					.filter(Objects::nonNull)
					.sorted(Comparator.comparing(UserEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
					.toList();

			String targetUsernames = targets.stream()
					.map(UserEntity::getUsername)
					.collect(Collectors.joining(","));

			String targetDisplayNames = targets.stream()
					.map(UserEntity::getDisplayName)
					.collect(Collectors.joining(","));

			sb.append(esc(period.getSemester())).append(';');
			sb.append(f.getFineDate() == null ? "" : f.getFineDate().toString()).append(';');
			sb.append(f.getCreatedAt() == null ? "" : TS.format(f.getCreatedAt())).append(';');
			sb.append(creator == null ? "" : esc(creator.getDisplayName())).append(';');
			sb.append(formatEuro(f.getAmountCents())).append(';');
			sb.append(esc(f.getReason())).append(';');
			sb.append(esc(targetUsernames)).append(';');
			sb.append(esc(targetDisplayNames)).append("\r\n");
		}

		byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

		String filename = "verhaarm-fines-" + period.getSemester().replace("/", "-") + ".csv";
		return new ExportResult(filename, bytes);
	}

	private ConventPeriodEntity resolvePeriod(UUID periodIdOrNull) {
		if (periodIdOrNull != null) {
			return periods.findById(periodIdOrNull).orElseThrow(() -> ApiErrors.badRequest("Period not found"));
		}
		LocalDate today = LocalDate.now(ZONE_BERLIN);
		return periods.findCovering(today).orElseThrow(() -> ApiErrors.notFound("No active period for today"));
	}

	private static void requireExportRole(UserEntity actor) {
		boolean ok =
				hasRole(actor, UserRole.ADMIN) ||
						hasRole(actor, UserRole.SENIOR) ||
						hasRole(actor, UserRole.TREASURER) ||
						hasRole(actor, UserRole.HOUSEKEEPING);

		if (!ok) throw ApiErrors.forbidden("Forbidden");
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private static String formatEuro(int cents) {
		BigDecimal eur = BigDecimal.valueOf(cents)
				.divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
		return eur.toPlainString().replace('.', ',');
	}

	private static String esc(String s) {
		if (s == null) return "";
		String v = s.replace("\r", " ").replace("\n", " ").trim();
		boolean mustQuote = v.contains(";") || v.contains("\"") || v.contains(",");
		if (!mustQuote) return v;
		return "\"" + v.replace("\"", "\"\"") + "\"";
	}

	public record ExportResult(String filename, byte[] bytes) {}
}