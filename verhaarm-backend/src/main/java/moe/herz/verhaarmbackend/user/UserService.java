package moe.herz.verhaarmbackend.user;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.user.dto.CreateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UpdateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UserBalanceDto;
import moe.herz.verhaarmbackend.user.dto.UserDto;
import moe.herz.verhaarmbackend.user.dto.UserPickerDto;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

	private final UserRepository users;
	private final FineRepository fines;
	private final PasswordEncoder encoder;
	private final AuditLogService audit;

	public UserService(UserRepository users, FineRepository fines, PasswordEncoder encoder, AuditLogService audit) {
		this.users = users;
		this.fines = fines;
		this.encoder = encoder;
		this.audit = audit;
	}

	// --------------------
	// READ
	// --------------------

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
		if (!activeOnly) {
			throw ApiErrors.badRequest("Only active=true is supported");
		}

		String raw = query == null ? "" : query.trim();
		String qNorm = UsernameNormalizer.normalize(raw);
		String qLower = raw.toLowerCase(Locale.ROOT);

		return users.searchActiveForPicker(qNorm, qLower)
				.stream()
				.map(u -> new UserPickerDto(u.getId(), u.getUsername(), u.getDisplayName()))
				.toList();
	}

	// --------------------
	// BALANCE
	// --------------------

	/**
	 * Balance = sum(amount_cents) of all non-deleted fines where target user is included.
	 * Suggestions do not count unless accepted (accepted suggestions are real fines already).
	 *
	 * Access:
	 *  - MEMBER-only: may view only own balance
	 *  - ADMIN/SENIOR/HOUSEKEEPING/TREASURER: may view any user's balance
	 */
	@Transactional(readOnly = true)
	public UserBalanceDto getBalance(UUID targetUserId, UUID periodIdOrNull, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		// ensure target exists (keeps API consistent)
		users.findById(targetUserId).orElseThrow(() -> ApiErrors.notFound("User not found"));

		boolean isSelf = actor.getId() != null && actor.getId().equals(targetUserId);

		if (!isSelf && !isStaff(actor.getId())) {
			throw ApiErrors.forbidden("Forbidden");
		}

		long cents = (periodIdOrNull == null)
				? fines.sumVisibleAmountCentsForTarget(targetUserId)
				: fines.sumVisibleAmountCentsForTargetInPeriod(targetUserId, periodIdOrNull);

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
	public UserDto createUser(CreateUserRequest req) {
		String username = req.username();
		String normalized = UsernameNormalizer.normalize(username);

		if (users.existsByUsername(username) || users.existsByUsernameNormalized(normalized)) {
			throw ApiErrors.badRequest("Username already exists");
		}

		Set<UserRole> newRoles = parseRoles(req.roles());
		if (newRoles.isEmpty()) newRoles = Set.of(UserRole.MEMBER);

		validateRoleConstraintsOnChange(null, false, newRoles, false);

		UserEntity u = new UserEntity(
				UUID.randomUUID(),
				username,
				req.displayName(),
				encoder.encode(req.password()),
				false
		);

		u.clearRoles();
		for (UserRole r : newRoles) u.addRole(r);

		users.save(u);
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

		Set<UserRole> newRoles = parseRoles(req.roles());
		if (newRoles.isEmpty()) newRoles = Set.of(UserRole.MEMBER);

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

		validateRoleConstraintsOnChange(u, u.isDisabled(), newRoles, newDisabled);

		if (req.displayName() != null && !req.displayName().isBlank()) {
			u.setDisplayName(req.displayName());
		}

		u.setDisabled(newDisabled);

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

		// optional: display name changes
		if (req.displayName() != null && !req.displayName().isBlank() && !Objects.equals(beforeDisplayName, u.getDisplayName())) {
			var d = audit.obj();
			audit.put(d, "targetUserId", u.getId());
			audit.put(d, "targetUsername", u.getUsername());
			audit.put(d, "beforeDisplayName", beforeDisplayName);
			audit.put(d, "afterDisplayName", u.getDisplayName());
			audit.log(actor, "user.displayNameChanged", d);
		}

		return toDto(u);
	}

	@Transactional
	public void setPassword(UUID id, String password) {
		if (password == null || password.isBlank()) {
			throw ApiErrors.badRequest("Password required");
		}

		UserEntity u = users.findById(id)
				.orElseThrow(() -> ApiErrors.notFound("User not found"));

		u.setPasswordHash(encoder.encode(password));
		users.save(u);
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
		long treasurer = enabled.stream().filter(u -> hasRole(u, UserRole.TREASURER)).count();

		if (targetOrNull != null && !oldDisabled) {
			if (hasRole(targetOrNull, UserRole.SENIOR)) seniors--;
			if (hasRole(targetOrNull, UserRole.HOUSEKEEPING)) housekeeping--;
			if (hasRole(targetOrNull, UserRole.TREASURER)) treasurer--;
		}

		if (!newDisabled) {
			if (newRoles.contains(UserRole.SENIOR)) seniors++;
			if (newRoles.contains(UserRole.HOUSEKEEPING)) housekeeping++;
			if (newRoles.contains(UserRole.TREASURER)) treasurer++;
		}

		if (seniors != 1)
			throw ApiErrors.badRequest("Exactly one SENIOR must exist (enabled users)");
		if (housekeeping < 1)
			throw ApiErrors.badRequest("At least one HOUSEKEEPING must exist (enabled users)");
		if (treasurer < 1)
			throw ApiErrors.badRequest("At least one TREASURER must exist (enabled users)");
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

	private UserDto toDto(UserEntity u) {
		Set<String> roles = u.getRoles().stream()
				.map(UserRoleEntity::getRole)
				.map(Enum::name)
				.collect(Collectors.toSet());

		return new UserDto(
				u.getId(),
				u.getUsername(),
				u.getDisplayName(),
				u.isDisabled(),
				roles
		);
	}
}