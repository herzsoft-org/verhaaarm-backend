package moe.herz.verhaarmbackend.user;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.user.dto.CreateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UpdateUserRequest;
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
	private final UserRoleRepository rolesRepo;
	private final PasswordEncoder encoder;

	public UserService(UserRepository users, UserRoleRepository rolesRepo, PasswordEncoder encoder) {
		this.users = users;
		this.rolesRepo = rolesRepo;
		this.encoder = encoder;
	}

	@Transactional(readOnly = true)
	public List<UserDto> listAll() {
		// roles are needed for DTO => use fetch-join query
		return users.findAllWithRoles().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public List<UserPickerDto> picker(boolean activeOnly, String query) {
		// picker does not need roles; keep it cheap
		String q = query == null ? "" : UsernameNormalizer.normalize(query);
		return users.findAll().stream()
				.filter(u -> !activeOnly || !u.isDisabled())
				.filter(u -> q.isBlank()
						|| u.getUsernameNormalized().contains(q)
						|| UsernameNormalizer.normalize(u.getDisplayName()).contains(q))
				.sorted(Comparator.comparing(UserEntity::getUsernameNormalized))
				.map(u -> new UserPickerDto(u.getId(), u.getUsername(), u.getDisplayName()))
				.toList();
	}

	@Transactional
	public UserDto createUser(CreateUserRequest req) {
		String username = req.username();
		String normalized = UsernameNormalizer.normalize(username);

		// ensure case/spacing-insensitive uniqueness as well
		if (users.existsByUsername(username) || users.existsByUsernameNormalized(normalized)) {
			throw ApiErrors.badRequest("Username already exists");
		}

		Set<UserRole> parsedRoles = parseRoles(req.roles());
		if (parsedRoles.isEmpty()) parsedRoles = Set.of(UserRole.MEMBER);

		// constraints apply to enabled users; new user is enabled by default
		validateRoleConstraintsOnChange(null, false, parsedRoles, false);

		UserEntity u = new UserEntity(
				UUID.randomUUID(),
				username,
				req.displayName(),
				encoder.encode(req.password()),
				false
		);

		u.clearRoles();
		for (UserRole r : parsedRoles) u.addRole(r);

		users.save(u);

		// ensure roles are initialized for DTO (they should be in-memory already, but keep consistent)
		return toDto(u);
	}

	@Transactional
	public UserDto updateUser(UUID id, UpdateUserRequest req, boolean actorIsAdmin) {
		// We call toDto() and touch roles => load with roles
		UserEntity u = users.findByIdWithRoles(id).orElseThrow(() -> ApiErrors.notFound("User not found"));

		Set<UserRole> newRoles = parseRoles(req.roles());
		if (newRoles.isEmpty()) newRoles = Set.of(UserRole.MEMBER);

		// SENIOR may not assign ADMIN
		if (!actorIsAdmin && newRoles.contains(UserRole.ADMIN)) {
			throw ApiErrors.forbidden("SENIOR cannot assign ADMIN role");
		}

		boolean newDisabled = req.disabled() != null ? req.disabled() : u.isDisabled();

		validateRoleConstraintsOnChange(u, u.isDisabled(), newRoles, newDisabled);

		if (req.displayName() != null && !req.displayName().isBlank()) {
			u.setDisplayName(req.displayName());
		}

		u.setDisabled(newDisabled);

		// replace roles
		u.clearRoles();
		for (UserRole r : newRoles) u.addRole(r);

		users.save(u);
		return toDto(u);
	}

	private void validateRoleConstraintsOnChange(
			UserEntity targetOrNull,
			boolean oldDisabled,
			Set<UserRole> newRoles,
			boolean newDisabled
	) {
		// Constraints among enabled users only:
		// Exactly one SENIOR, at least one HOUSEKEEPING, at least one TREASURER.

		// Need roles for counting => load all with roles
		List<UserEntity> all = users.findAllWithRoles();

		long enabledSenior = all.stream()
				.filter(u -> !u.isDisabled())
				.filter(u -> hasRole(u, UserRole.SENIOR))
				.count();

		long enabledHouse = all.stream()
				.filter(u -> !u.isDisabled())
				.filter(u -> hasRole(u, UserRole.HOUSEKEEPING))
				.count();

		long enabledTreas = all.stream()
				.filter(u -> !u.isDisabled())
				.filter(u -> hasRole(u, UserRole.TREASURER))
				.count();

		// Subtract old target contribution (if target exists and was enabled)
		if (targetOrNull != null && !oldDisabled) {
			if (hasRole(targetOrNull, UserRole.SENIOR)) enabledSenior--;
			if (hasRole(targetOrNull, UserRole.HOUSEKEEPING)) enabledHouse--;
			if (hasRole(targetOrNull, UserRole.TREASURER)) enabledTreas--;
		}

		// Add new contribution (if target will be enabled)
		if (!newDisabled) {
			if (newRoles.contains(UserRole.SENIOR)) enabledSenior++;
			if (newRoles.contains(UserRole.HOUSEKEEPING)) enabledHouse++;
			if (newRoles.contains(UserRole.TREASURER)) enabledTreas++;
		}

		if (enabledSenior != 1) {
			throw ApiErrors.badRequest("Role constraint violated: exactly one SENIOR must exist (enabled users)");
		}
		if (enabledHouse < 1) {
			throw ApiErrors.badRequest("Role constraint violated: at least one HOUSEKEEPING must exist (enabled users)");
		}
		if (enabledTreas < 1) {
			throw ApiErrors.badRequest("Role constraint violated: at least one TREASURER must exist (enabled users)");
		}
	}

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