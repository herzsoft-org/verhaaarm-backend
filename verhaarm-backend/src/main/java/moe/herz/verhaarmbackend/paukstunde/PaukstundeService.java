package moe.herz.verhaarmbackend.paukstunde;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.common.StructuredApiError;
import moe.herz.verhaarmbackend.paukstunde.dto.*;
import moe.herz.verhaarmbackend.period.ConventPeriodEntity;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserMemberStatus;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaukstundeService {
	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private final PaukstundeRepository paukstunden;
	private final UserRepository users;
	private final ConventPeriodRepository periods;
	private final AuditLogService audit;

	@PersistenceContext
	private EntityManager em;

	public PaukstundeService(
			PaukstundeRepository paukstunden,
			UserRepository users,
			ConventPeriodRepository periods,
			AuditLogService audit
	) {
		this.paukstunden = paukstunden;
		this.users = users;
		this.periods = periods;
		this.audit = audit;
	}

	@Transactional
	public PaukstundeDto create(CreatePaukstundeRequest req, UserEntity actor) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");

		Set<UUID> participantIds = normalizeIds(req.participantUserIds());
		requireActorParticipantForNonStaff(actor, participantIds);
		List<UserEntity> participants = users.findAllEnabledByIdIn(participantIds);
		PaukstundeValidator.validate(req.date(), req.hours(), participantIds, participants);

		var p = new PaukstundeEntity(UUID.randomUUID(), req.date(), req.hours(), actor.getId());
		for (UUID userId : participantIds) p.addParticipant(userId);

		paukstunden.save(p);
		em.flush();
		em.clear();

		var reloaded = paukstunden.findById(p.getId()).orElseThrow(() -> ApiErrors.notFound("Paukstunde not found"));

		var d = audit.obj();
		audit.put(d, "paukstundeId", reloaded.getId());
		audit.put(d, "date", reloaded.getDate().toString());
		audit.put(d, "hours", reloaded.getHours());
		audit.putUuidArray(d, "participantUserIds", reloaded.getParticipantUserIds());
		audit.log(actor, "paukstunde.create", d);

		return toDto(reloaded, true);
	}

	@Transactional(readOnly = true)
	public List<PaukstundeDto> listCurrentConventsperiode(UserEntity actor) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");
		return listForPeriod(currentPeriod(), actor);
	}

	@Transactional(readOnly = true)
	public List<PaukstundeDto> listForConventsperiode(UUID periodId, UserEntity actor) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");
		return listForPeriod(periodOrThrow(periodId), actor);
	}

	private List<PaukstundeDto> listForPeriod(ConventPeriodEntity period, UserEntity actor) {
		List<PaukstundeEntity> entries = isStaff(actor)
				? paukstunden.findInDateRangeWithParticipants(period.getStartAt(), period.getEndAt())
				: paukstunden.findForParticipantInDateRange(actor.getId(), period.getStartAt(), period.getEndAt());

		return entries.stream()
				.map(p -> toDto(p, true))
				.filter(p -> !p.participants().isEmpty())
				.toList();
	}

	@Transactional(readOnly = true)
	public PaukstundeUserTotalDto myCurrentTotal(UserEntity actor) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");
		return userCurrentTotal(actor.getId(), actor);
	}

	@Transactional(readOnly = true)
	public PaukstundeUserTotalDto userCurrentTotal(UUID userId, UserEntity actor) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");
		boolean self = actor.getId().equals(userId);
		if (!self) requireStaff(actor);

		UserEntity user = users.findById(userId).orElseThrow(() -> ApiErrors.notFound("User not found"));
		if (user.isDisabled()) {
			return new PaukstundeUserTotalDto(user.getId(), user.getUsername(), user.getDisplayName(), safeStatus(user).name(), 0, 0, Map.of());
		}

		ConventPeriodEntity period = currentPeriod();
		List<PaukstundeEntity> entries = paukstunden.findForParticipantInDateRange(userId, period.getStartAt(), period.getEndAt());
		return totalForUser(user, entries);
	}

	@Transactional(readOnly = true)
	public List<PaukstundeUserTotalDto> summaryCurrentConventsperiode(UserEntity actor) {
		requireStaff(actor);
		return summaryForPeriod(currentPeriod());
	}

	@Transactional(readOnly = true)
	public List<PaukstundeUserTotalDto> summaryForConventsperiode(UUID periodId, UserEntity actor) {
		requireStaff(actor);
		return summaryForPeriod(periodOrThrow(periodId));
	}

	private List<PaukstundeUserTotalDto> summaryForPeriod(ConventPeriodEntity period) {
		List<PaukstundeEntity> entries = paukstunden.findInDateRangeWithParticipants(period.getStartAt(), period.getEndAt());
		Map<UUID, UserEntity> enabledUsers = users.findAllEnabledWithRoles().stream()
				.collect(Collectors.toMap(UserEntity::getId, Function.identity()));

		Map<UUID, List<PaukstundeEntity>> byUser = new HashMap<>();
		for (PaukstundeEntity entry : entries) {
			for (UUID userId : entry.getParticipantUserIds()) {
				if (!enabledUsers.containsKey(userId)) continue;
				byUser.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(entry);
			}
		}

		return byUser.entrySet().stream()
				.map(e -> totalForUser(enabledUsers.get(e.getKey()), e.getValue()))
				.sorted(Comparator.comparing(PaukstundeUserTotalDto::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	@Transactional
	public PaukstundeDto update(UUID id, UpdatePaukstundeRequest req, UserEntity actor) {
		var p = paukstunden.findById(id).orElseThrow(() -> ApiErrors.notFound("Paukstunde not found"));
		requireCanModify(p, actor);

		LocalDate date = req.date() != null ? req.date() : p.getDate();
		Integer hours = req.hours() != null ? req.hours() : p.getHours();
		Set<UUID> participantIds = req.participantUserIds() != null ? normalizeIds(req.participantUserIds()) : Set.copyOf(p.getParticipantUserIds());
		requireActorParticipantForNonStaff(actor, participantIds);
		List<UserEntity> participants = users.findAllEnabledByIdIn(participantIds);

		PaukstundeValidator.validate(date, hours, participantIds, participants);

		p.setDate(date);
		p.setHours(hours);
		if (req.participantUserIds() != null) {
			p.clearParticipants();
			for (UUID userId : participantIds) p.addParticipant(userId);
		}
		paukstunden.save(p);

		var d = audit.obj();
		audit.put(d, "paukstundeId", p.getId());
		audit.put(d, "date", p.getDate().toString());
		audit.put(d, "hours", p.getHours());
		audit.putUuidArray(d, "participantUserIds", p.getParticipantUserIds());
		audit.log(actor, "paukstunde.update", d);

		return toDto(p, true);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		var p = paukstunden.findById(id).orElseThrow(() -> ApiErrors.notFound("Paukstunde not found"));
		requireCanModify(p, actor);

		var d = audit.obj();
		audit.put(d, "paukstundeId", p.getId());
		audit.log(actor, "paukstunde.delete", d);

		paukstunden.delete(p);
	}

	private ConventPeriodEntity currentPeriod() {
		LocalDate today = LocalDate.now(ZONE_BERLIN);
		return periods.findCovering(today)
				.orElseThrow(() -> StructuredApiError.notFound(
						"CURRENT_CONVENTSPERIODE_NOT_FOUND",
						"No active period for today",
						Map.of("date", today.toString())
				));
	}

	private ConventPeriodEntity periodOrThrow(UUID periodId) {
		return periods.findById(periodId)
				.orElseThrow(() -> ApiErrors.notFound("Conventsperiode not found"));
	}

	private void requireCanModify(PaukstundeEntity p, UserEntity actor) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");
		boolean isParticipant = p.getParticipantUserIds().contains(actor.getId());
		if (!(isParticipant || isStaff(actor))) throw ApiErrors.forbidden("Forbidden");
	}

	private void requireActorParticipantForNonStaff(UserEntity actor, Set<UUID> participantIds) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");
		if (!isStaff(actor) && !participantIds.contains(actor.getId())) {
			throw ApiErrors.forbidden("Non-staff users must be participants");
		}
	}

	private void requireStaff(UserEntity actor) {
		if (!isStaff(actor)) throw ApiErrors.forbidden("Forbidden");
	}

	private static boolean isStaff(UserEntity actor) {
		return actor != null && (
				hasRole(actor, UserRole.ADMIN) ||
				hasRole(actor, UserRole.SENIOR) ||
				hasRole(actor, UserRole.FECHTWART)
		);
	}

	private static boolean hasRole(UserEntity user, UserRole role) {
		return user.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private PaukstundeDto toDto(PaukstundeEntity p, boolean enabledParticipantsOnly) {
		Set<UUID> ids = new HashSet<>(p.getParticipantUserIds());
		if (p.getCreatedByUserId() != null) ids.add(p.getCreatedByUserId());

		Map<UUID, UserEntity> userById = users.findAllById(ids).stream()
				.collect(Collectors.toMap(UserEntity::getId, Function.identity()));

		List<PaukstundeParticipantDto> participants = p.getParticipantUserIds().stream()
				.map(userById::get)
				.filter(Objects::nonNull)
				.filter(u -> !enabledParticipantsOnly || !u.isDisabled())
				.sorted(Comparator.comparing(UserEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
				.map(u -> new PaukstundeParticipantDto(u.getId(), u.getUsername(), u.getDisplayName(), safeStatus(u).name()))
				.toList();

		UserEntity creator = userById.get(p.getCreatedByUserId());
		return new PaukstundeDto(
				p.getId(),
				p.getDate(),
				p.getHours(),
				participants,
				p.getCreatedByUserId(),
				creator == null ? null : creator.getDisplayName(),
				p.getCreatedAt(),
				p.getUpdatedAt()
		);
	}

	private static PaukstundeUserTotalDto totalForUser(UserEntity user, List<PaukstundeEntity> entries) {
		Map<String, Integer> byDate = new TreeMap<>(Comparator.reverseOrder());
		int total = 0;
		for (PaukstundeEntity entry : entries) {
			total += entry.getHours();
			byDate.merge(entry.getDate().toString(), entry.getHours(), Integer::sum);
		}
		return new PaukstundeUserTotalDto(
				user.getId(),
				user.getUsername(),
				user.getDisplayName(),
				safeStatus(user).name(),
				total,
				entries.size(),
				byDate
		);
	}

	private static Set<UUID> normalizeIds(Set<UUID> ids) {
		if (ids == null) return Set.of();
		return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
	}

	private static UserMemberStatus safeStatus(UserEntity user) {
		return user.getMemberStatus() == null ? UserMemberStatus.BURSCH : user.getMemberStatus();
	}
}
