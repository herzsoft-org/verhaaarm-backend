package moe.herz.verhaarmbackend.user;

import moe.herz.verhaarmbackend.attendance.AttendanceRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.auth.RefreshTokenRepository;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.common.StructuredApiError;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.finephoto.FinePhotoService;
import moe.herz.verhaarmbackend.period.ConventDerivation;
import moe.herz.verhaarmbackend.period.ConventPeriodService;
import moe.herz.verhaarmbackend.period.dto.ConventPeriodDto;
import moe.herz.verhaarmbackend.paukstunde.PaukstundeRepository;
import moe.herz.verhaarmbackend.push.PushDeviceRepository;
import moe.herz.verhaarmbackend.task.TaskAssigneeEntity;
import moe.herz.verhaarmbackend.task.TaskAssigneeRepository;
import moe.herz.verhaarmbackend.task.TaskRepository;
import moe.herz.verhaarmbackend.user.dto.CreateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UpdateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UserBalanceDto;
import moe.herz.verhaarmbackend.user.dto.UserDto;
import moe.herz.verhaarmbackend.user.dto.UserPickerDto;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

	private final UserRepository users;
	private final FineRepository fines;
	private final ConventPeriodService periods;
	private final PasswordEncoder encoder;
	private final AuditLogService audit;

	// hard delete dependencies
	private final RefreshTokenRepository refreshTokens;
	private final PushDeviceRepository pushDevices;
	private final TaskRepository tasks;
	private final TaskAssigneeRepository taskAssignees;
	private final AttendanceRepository attendance;
	private final UserRoleRepository userRoles;
	private final FinePhotoService finePhotos;
	private final PaukstundeRepository paukstunden;

	public UserService(
			UserRepository users,
			FineRepository fines,
			ConventPeriodService periods,
			PasswordEncoder encoder,
			AuditLogService audit,
			RefreshTokenRepository refreshTokens,
			PushDeviceRepository pushDevices,
			TaskRepository tasks,
			TaskAssigneeRepository taskAssignees,
			AttendanceRepository attendance,
			UserRoleRepository userRoles,
			FinePhotoService finePhotos,
			PaukstundeRepository paukstunden
	) {
		this.users = users;
		this.fines = fines;
		this.periods = periods;
		this.encoder = encoder;
		this.audit = audit;

		this.refreshTokens = refreshTokens;
		this.pushDevices = pushDevices;
		this.tasks = tasks;
		this.taskAssignees = taskAssignees;
		this.attendance = attendance;
		this.userRoles = userRoles;
		this.finePhotos = finePhotos;
		this.paukstunden = paukstunden;
	}

	// --------------------
	// READ
	// --------------------

	@Transactional(readOnly = true)
	public List<UserDto> listOnline(String range) {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime since;

		String normalized = range == null ? "" : range.trim().toLowerCase(Locale.ROOT);

		switch (normalized) {
			case "week" -> since = now.minusDays(7);
			case "month" -> since = now.minusMonths(1);
			default -> throw ApiErrors.badRequest("online must be one of: week, month");
		}

		return users.findAllWithRolesOnlineSince(since)
				.stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<UserDto> listAll() {
		return users.findAllWithRolesOrdered()
				.stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public UserDto getUser(UUID id) {
		UserEntity u = users.findByIdWithRoles(id)
				.orElseThrow(() -> ApiErrors.notFound("User not found"));
		return toDto(u);
	}

	@Transactional(readOnly = true)
	public List<UserPickerDto> picker(boolean activeOnly, String query) {
		String raw = query == null ? "" : query.trim();
		String qNorm = UsernameNormalizer.normalize(raw);
		String qLower = raw.toLowerCase(Locale.ROOT);

		List<UserEntity> found = activeOnly
				? users.searchActiveForPicker(qNorm, qLower)
				: users.searchAllForPicker(qNorm, qLower);

		return found.stream()
				.map(u -> {
					UserMemberStatus status = safeMemberStatus(u);

					return new UserPickerDto(
							u.getId(),
							u.getUsername(),
							u.getDisplayName(),
							status.name(),
							status.isAktivitas(),
							u.isDisabled()
					);
				})
				.toList();
	}

	// --------------------
	// BALANCE
	// --------------------

	@Transactional(readOnly = true)
	public UserBalanceDto getBalance(UUID targetUserId, UUID periodIdOrNull, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		// ensure target exists (keeps API consistent)
		users.findById(targetUserId).orElseThrow(() -> ApiErrors.notFound("User not found"));

		boolean isSelf = actor.getId() != null && actor.getId().equals(targetUserId);

		if (!isSelf && !isStaff(actor.getId())) {
			throw ApiErrors.forbidden("Forbidden");
		}

		ConventPeriodDto period = periodIdOrNull != null ? periods.get(periodIdOrNull) : periods.getActive();

		LocalDate fromDate = period.startAt() == null ? ConventDerivation.DATE_FLOOR : period.startAt();
		LocalDate toDate = period.endAt() == null ? ConventDerivation.DATE_CEIL : period.endAt();

		long cents = fines.sumVisibleAmountCentsForTargetInPeriod(targetUserId, fromDate, toDate);

		return new UserBalanceDto(targetUserId, cents, formatEurFromCents(cents));
	}

	private boolean isStaff(UUID actorId) {
		if (actorId == null) return false;
		return users.hasRole(actorId, UserRole.ADMIN)
				|| users.hasRole(actorId, UserRole.SENIOR)
				|| users.hasRole(actorId, UserRole.HOUSEKEEPING)
				|| users.hasRole(actorId, UserRole.TREASURER);
	}

	private static String formatEurFromCents(long cents) {
		long abs = Math.abs(cents);
		long euros = abs / 100;
		long rem = abs % 100;
		String sign = cents < 0 ? "-" : "";
		return sign + euros + "," + (rem < 10 ? "0" + rem : Long.toString(rem)) + " €";
	}

	// --------------------
	// CREATE
	// --------------------

	@Transactional
	public UserDto createUser(CreateUserRequest req, UserEntity actor) {
		// Only ADMIN can create users (controller already enforces, but keep it safe here)
		if (actor == null || !users.hasRole(actor.getId(), UserRole.ADMIN)) {
			throw ApiErrors.forbidden("Forbidden");
		}

		String username = req.username();
		if (username == null) throw ApiErrors.badRequest("Username required");

		String normalized = UsernameNormalizer.normalize(username);

		// username must already be in allowed form
		if (!username.equals(normalized) || normalized.isBlank()) {
			throw ApiErrors.badRequest("Username must match [a-z0-9-] (and use ae/oe/ue/ss instead of umlauts)");
		}

		if (users.existsByUsername(username) || users.existsByUsernameNormalized(normalized)) {
			throw ApiErrors.badRequest("Username already exists");
		}

		Set<UserRole> newRoles = normalizeRoles(parseRoles(req.roles()));
		UserMemberStatus memberStatus = parseMemberStatusOrDefault(req.memberStatus());

		validateRoleConstraintsOnChange(null, false, newRoles, false);

		UserEntity u = new UserEntity(
				UUID.randomUUID(),
				username,
				req.displayName(),
				encoder.encode(req.password()),
				false
		);

		u.setMemberStatus(memberStatus);

		u.clearRoles();
		for (UserRole r : newRoles) u.addRole(r);

		users.save(u);

		// AUDIT: user created
		var d = audit.obj();
		audit.put(d, "targetUserId", u.getId());
		audit.put(d, "targetUsername", u.getUsername());
		audit.put(d, "targetDisplayName", u.getDisplayName());
		audit.put(d, "memberStatus", memberStatus.name());
		audit.putStringArray(d, "roles", newRoles.stream().map(Enum::name).sorted().toList());
		audit.log(actor, "user.create", d);

		return toDto(u);
	}

	// --------------------
	// UPDATE
	// --------------------

	@Transactional
	public UserDto updateUser(UUID id, UpdateUserRequest req, UserEntity actor) {
		UserEntity u = users.findByIdWithRoles(id)
				.orElseThrow(() -> ApiErrors.notFound("User not found"));

		UUID actorId = actor != null ? actor.getId() : null;
		boolean actorIsAdmin = actorId != null && users.hasRole(actorId, UserRole.ADMIN);

		// PATCH semantics for roles:
		// - roles == null  => keep current roles
		// - roles == []    => set to MEMBER (default)
		// - roles provided => parse + apply (if parse result empty => MEMBER)
		Set<UserRole> newRoles;
		if (req.roles() == null) {
			newRoles = u.roleSet();
		} else {
			Set<UserRole> parsed = parseRoles(req.roles());
			newRoles = parsed.isEmpty() ? Set.of(UserRole.MEMBER) : parsed;
		}
		newRoles = normalizeRoles(newRoles);

		// SENIOR cannot assign ADMIN
		if (!actorIsAdmin && newRoles.contains(UserRole.ADMIN)) {
			throw ApiErrors.forbidden("SENIOR cannot assign ADMIN role");
		}

		boolean newDisabled = req.disabled() != null ? req.disabled() : u.isDisabled();

		// Prevent disabling last enabled ADMIN
		if (newDisabled && !u.isDisabled() && hasRole(u, UserRole.ADMIN)) {
			if (users.countEnabledAdmins() <= 1) {
				throw ApiErrors.badRequest("Cannot disable last enabled ADMIN");
			}
		}

		// Prevent removing ADMIN from last enabled ADMIN
		if (hasRole(u, UserRole.ADMIN) && !newRoles.contains(UserRole.ADMIN) && !newDisabled) {
			if (users.countEnabledAdmins() <= 1) {
				throw ApiErrors.badRequest("Cannot remove ADMIN from last enabled ADMIN");
			}
		}

		// snapshots before changes
		boolean beforeDisabled = u.isDisabled();
		Set<UserRole> beforeRoles = u.roleSet();
		String beforeDisplayName = u.getDisplayName();
		UserMemberStatus beforeMemberStatus = safeMemberStatus(u);

		UserMemberStatus newMemberStatus = beforeMemberStatus;
		if (req.memberStatus() != null) {
			if (!actorIsAdmin) {
				throw ApiErrors.forbidden("Only ADMIN may change member status");
			}

			newMemberStatus = parseMemberStatus(req.memberStatus());
		}

		validateRoleConstraintsOnChange(u, u.isDisabled(), newRoles, newDisabled);

		if (req.displayName() != null && !req.displayName().isBlank()) {
			u.setDisplayName(req.displayName());
		}

		u.setDisabled(newDisabled);
		u.setMemberStatus(newMemberStatus);

		u.clearRoles();
		for (UserRole r : newRoles) u.addRole(r);

		users.save(u);

		// AUDIT: role change
		if (!beforeRoles.equals(newRoles)) {
			var d = audit.obj();
			audit.put(d, "targetUserId", u.getId());
			audit.put(d, "targetUsername", u.getUsername());
			audit.putStringArray(d, "beforeRoles", beforeRoles.stream().map(Enum::name).sorted().toList());
			audit.putStringArray(d, "afterRoles", newRoles.stream().map(Enum::name).sorted().toList());
			audit.log(actor, "user.rolesChanged", d);
		}

		// AUDIT: disabled change
		if (beforeDisabled != newDisabled) {
			var d = audit.obj();
			audit.put(d, "targetUserId", u.getId());
			audit.put(d, "targetUsername", u.getUsername());
			audit.put(d, "beforeDisabled", beforeDisabled);
			audit.put(d, "afterDisabled", newDisabled);
			audit.log(actor, "user.disabledChanged", d);
		}

		// AUDIT: member status change
		if (beforeMemberStatus != newMemberStatus) {
			var d = audit.obj();
			audit.put(d, "targetUserId", u.getId());
			audit.put(d, "targetUsername", u.getUsername());
			audit.put(d, "beforeMemberStatus", beforeMemberStatus.name());
			audit.put(d, "afterMemberStatus", newMemberStatus.name());
			audit.log(actor, "user.memberStatusChanged", d);
		}

		// optional: display name changes
		if (req.displayName() != null
				&& !req.displayName().isBlank()
				&& !Objects.equals(beforeDisplayName, u.getDisplayName())) {
			var d = audit.obj();
			audit.put(d, "targetUserId", u.getId());
			audit.put(d, "targetUsername", u.getUsername());
			audit.put(d, "beforeDisplayName", beforeDisplayName);
			audit.put(d, "afterDisplayName", u.getDisplayName());
			audit.log(actor, "user.displayNameChanged", d);
		}

		return toDto(u);
	}

	/**
	 * Bulk-replaces who currently holds {@code role} (used for the 4 Ämter that are
	 * derived from user roles: Sprecher/Fechtwart/Schmuckwart/Kassenwart). Callers are
	 * responsible for the ADMIN/SENIOR permission gate (mirrors {@code PATCH /users/{id}}).
	 */
	@Transactional
	public List<UserEntity> setRoleHolders(UserRole role, List<UUID> userIds, UserEntity actor) {
		if (role == UserRole.ADMIN) {
			throw ApiErrors.badRequest("Use user administration to manage the ADMIN role");
		}

		List<UUID> uniqueIds = userIds == null
				? List.of()
				: userIds.stream().filter(Objects::nonNull).distinct().toList();

		List<UserEntity> targets = uniqueIds.isEmpty() ? List.of() : users.findAllById(uniqueIds);
		Map<UUID, UserEntity> targetById = targets.stream().collect(Collectors.toMap(UserEntity::getId, u -> u));
		for (UUID id : uniqueIds) {
			if (!targetById.containsKey(id)) throw ApiErrors.badRequest("User not found: " + id);
		}

		long enabledTargetCount = targets.stream().filter(u -> !u.isDisabled()).count();
		if (enabledTargetCount < 1) throw requiredRoleMissing(role);

		List<UserEntity> currentHolders = users.findAllEnabledByRole(role);
		Set<UUID> newIdSet = new HashSet<>(uniqueIds);

		for (UserEntity current : currentHolders) {
			if (!newIdSet.contains(current.getId())) {
				current.removeRole(role);
				users.save(current);
			}
		}
		for (UserEntity target : targets) {
			if (!target.hasRole(role)) {
				target.addRole(role);
				users.save(target);
			}
		}

		var d = audit.obj();
		audit.put(d, "role", role.name());
		audit.putStringArray(d, "beforeUserIds", currentHolders.stream().map(u -> u.getId().toString()).sorted().toList());
		audit.putStringArray(d, "afterUserIds", uniqueIds.stream().map(UUID::toString).sorted().toList());
		audit.log(actor, "user.setRoleHolders", d);

		return uniqueIds.stream().map(targetById::get).toList();
	}

	/**
	 * Password change policy:
	 *  - actor may change own password
	 *  - only ADMIN may change another user's password
	 */
	@Transactional
	public void setPassword(UUID targetUserId, String password, UserEntity actor) {
		if (actor == null || actor.getId() == null) throw ApiErrors.forbidden("Forbidden");

		if (password == null || password.isBlank()) {
			throw ApiErrors.badRequest("Password required");
		}

		boolean self = actor.getId().equals(targetUserId);
		boolean admin = users.hasRole(actor.getId(), UserRole.ADMIN);

		if (!self && !admin) {
			throw ApiErrors.forbidden("Only ADMIN may change another user's password");
		}

		UserEntity u = users.findById(targetUserId)
				.orElseThrow(() -> ApiErrors.notFound("User not found"));

		u.setPasswordHash(encoder.encode(password));
		users.save(u);

		// AUDIT: password changed (no password logged)
		var d = audit.obj();
		audit.put(d, "targetUserId", u.getId());
		audit.put(d, "targetUsername", u.getUsername());
		audit.put(d, "self", self);
		audit.log(actor, "user.passwordChanged", d);
	}

	// --------------------
	// HARD DELETE USER
	// --------------------

	@Transactional
	public void hardDeleteUser(UUID targetUserId, UserEntity actor) {
		if (actor == null || actor.getId() == null || !users.hasRole(actor.getId(), UserRole.ADMIN)) {
			throw ApiErrors.forbidden("Forbidden");
		}
		if (targetUserId == null) throw ApiErrors.badRequest("User id required");

		// Optional: prevent self delete
		if (targetUserId.equals(actor.getId())) {
			throw ApiErrors.badRequest("Cannot delete self");
		}

		// Load target (and roles for last-admin checks)
		UserEntity target = users.findByIdWithRoles(targetUserId)
				.orElseThrow(() -> ApiErrors.notFound("User not found"));

		// Prevent deleting last enabled ADMIN
		if (!target.isDisabled() && target.hasRole(UserRole.ADMIN)) {
			if (users.countEnabledAdmins() <= 1) {
				throw ApiErrors.badRequest("Cannot delete last enabled ADMIN");
			}
		}
		validateRoleConstraintsOnChange(target, target.isDisabled(), Set.of(), true);

		// 1) Auth / push
		refreshTokens.deleteAllForUser(targetUserId);
		pushDevices.deleteAllForUser(targetUserId);

		// 2) Attendance rows referencing the user
		attendance.hardDeleteAllForUser(targetUserId);

		// 3) Tasks:
		//    - remove this user from task assignees
		//    - if a task has no assignees left afterwards, hard delete it
		List<TaskAssigneeEntity> links = taskAssignees.findAllByUserIdWithTask(targetUserId);
		for (TaskAssigneeEntity link : links) {
			UUID taskId = link.getTask().getId();

			taskAssignees.deleteOne(targetUserId, taskId);

			if (taskAssignees.countAssignees(taskId) == 0) {
				taskAssignees.deleteAllForTask(taskId);
				tasks.hardDeleteById(taskId);
			}
		}

		// 4) Remove this user from all fine target mappings.
		//    This handles both normal fines and attendance-generated late/absent fines.
		fines.deleteTargetsForUser(targetUserId);
		fines.flush();

		// 5) Delete any fines that now have no targets left.
		//    Also clean up their upload directories first.
		List<UUID> orphanFineIds = fines.findFineIdsWithNoTargets();
		if (!orphanFineIds.isEmpty()) {
			for (UUID fineId : orphanFineIds) {
				finePhotos.deleteFineDirectoryBestEffort(fineId);
			}
			fines.deleteAllByIdInBatch(orphanFineIds);
			fines.flush();
		}

		// 6) Paukstunden: remove owned entries and participant links
		paukstunden.deleteCreatedByUser(targetUserId);
		paukstunden.deleteParticipantsForUser(targetUserId);
		List<UUID> orphanPaukstundeIds = paukstunden.findIdsWithNoParticipants();
		if (!orphanPaukstundeIds.isEmpty()) {
			paukstunden.deleteAllByIdInBatch(orphanPaukstundeIds);
			paukstunden.flush();
		}

		// 7) Roles: clean up before deleting user row
		userRoles.deleteAllForUser(targetUserId);

		// 8) Finally delete the user
		users.deleteById(targetUserId);
		users.flush();

		// AUDIT
		var d = audit.obj();
		audit.put(d, "targetUserId", targetUserId);
		audit.put(d, "targetUsername", target.getUsername());
		audit.put(d, "targetDisplayName", target.getDisplayName());
		audit.put(d, "memberStatus", safeMemberStatus(target).name());
		audit.log(actor, "user.hard_delete", d);
	}

	// --------------------
	// CONSTRAINTS
	// --------------------

	private void validateRoleConstraintsOnChange(
			UserEntity targetOrNull,
			boolean oldDisabled,
			Set<UserRole> newRoles,
			boolean newDisabled
	) {
		List<UserEntity> enabled = users.findAllEnabledWithRoles();

		long seniors = enabled.stream().filter(u -> hasRole(u, UserRole.SENIOR)).count();
		long housekeeping = enabled.stream().filter(u -> hasRole(u, UserRole.HOUSEKEEPING)).count();
		long fechtwart = enabled.stream().filter(u -> hasRole(u, UserRole.FECHTWART)).count();
		long treasurer = enabled.stream().filter(u -> hasRole(u, UserRole.TREASURER)).count();

		if (targetOrNull != null && !oldDisabled) {
			if (hasRole(targetOrNull, UserRole.SENIOR)) seniors--;
			if (hasRole(targetOrNull, UserRole.HOUSEKEEPING)) housekeeping--;
			if (hasRole(targetOrNull, UserRole.FECHTWART)) fechtwart--;
			if (hasRole(targetOrNull, UserRole.TREASURER)) treasurer--;
		}

		if (!newDisabled) {
			if (newRoles.contains(UserRole.SENIOR)) seniors++;
			if (newRoles.contains(UserRole.HOUSEKEEPING)) housekeeping++;
			if (newRoles.contains(UserRole.FECHTWART)) fechtwart++;
			if (newRoles.contains(UserRole.TREASURER)) treasurer++;
		}

		if (seniors < 1)
			throw requiredRoleMissing(UserRole.SENIOR);
		if (housekeeping < 1)
			throw requiredRoleMissing(UserRole.HOUSEKEEPING);
		if (fechtwart < 1)
			throw requiredRoleMissing(UserRole.FECHTWART);
		if (treasurer < 1)
			throw requiredRoleMissing(UserRole.TREASURER);
	}

	// --------------------
	// HELPERS
	// --------------------

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private static Set<UserRole> parseRoles(Set<String> roles) {
		if (roles == null) return Set.of();

		Set<UserRole> out = new HashSet<>();
		for (String r : roles) {
			if (r == null || r.isBlank()) continue;

			try {
				out.add(UserRole.valueOf(r.trim().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				throw ApiErrors.badRequest("Unknown role: " + r);
			}
		}

		return out;
	}

	private static Set<UserRole> normalizeRoles(Set<UserRole> roles) {
		if (roles == null || roles.isEmpty()) return Set.of(UserRole.MEMBER);
		Set<UserRole> out = new HashSet<>(roles);
		if (out.size() > 1) out.remove(UserRole.MEMBER);
		return Set.copyOf(out);
	}

	private static RuntimeException requiredRoleMissing(UserRole role) {
		String message = "You can't remove the only " + role.name() + ". Select another user to receive this role first.";
		return StructuredApiError.badRequest(
				"REQUIRED_ROLE_MISSING",
				message,
				StructuredApiError.details(
						"role", role.name(),
						"suggestedAction", "Select another enabled user to receive " + role.name() + " before removing it."
				)
		);
	}

	private static UserMemberStatus parseMemberStatusOrDefault(String raw) {
		if (raw == null || raw.isBlank()) {
			return UserMemberStatus.BURSCH;
		}

		return parseMemberStatus(raw);
	}

	private static UserMemberStatus parseMemberStatus(String raw) {
		if (raw == null || raw.isBlank()) {
			throw ApiErrors.badRequest("memberStatus required");
		}

		String normalized = raw.trim().toUpperCase(Locale.ROOT);

		try {
			return UserMemberStatus.valueOf(normalized);
		} catch (IllegalArgumentException e) {
			throw ApiErrors.badRequest("memberStatus must be one of: FUX, SCHUELERFUX, KONKNEIPANT, BURSCH, INAKTIVER, PHILISTER");
		}
	}

	private static UserMemberStatus safeMemberStatus(UserEntity u) {
		if (u == null || u.getMemberStatus() == null) {
			return UserMemberStatus.BURSCH;
		}

		return u.getMemberStatus();
	}

	private UserDto toDto(UserEntity u) {
		Set<String> roles = u.getRoles().stream()
				.map(UserRoleEntity::getRole)
				.map(Enum::name)
				.collect(Collectors.toSet());

		UserMemberStatus status = safeMemberStatus(u);

		return new UserDto(
				u.getId(),
				u.getUsername(),
				u.getDisplayName(),
				u.isDisabled(),
				roles,
				status.name(),
				status.isAktivitas(),
				u.getLastOnlineAt(),
				u.getUpdatedAt()
		);
	}
}
