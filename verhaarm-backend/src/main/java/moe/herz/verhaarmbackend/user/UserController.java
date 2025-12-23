package moe.herz.verhaarmbackend.user;

import moe.herz.verhaarmbackend.user.dto.CreateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UpdateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UserDto;
import moe.herz.verhaarmbackend.user.dto.UserPickerDto;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService users;

	public UserController(UserService users) {
		this.users = users;
	}

	// Any authenticated user: member picker UX
	// Matches requirement: GET /users?active=true&query=...
	@GetMapping(params = "active")
	public List<UserPickerDto> picker(
			@RequestParam boolean active,
			@RequestParam(required = false) String query
	) {
		if (!active) {
			throw new IllegalArgumentException("Only active=true is supported");
		}
		return users.picker(true, query);
	}

	// ADMIN+SENIOR: user management list
	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public List<UserDto> listUsers() {
		return users.listAll();
	}

	// ADMIN+SENIOR: detail
	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public UserDto get(@PathVariable UUID id) {
		return users.getUser(id);
	}

	// ADMIN only: create
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public UserDto create(@Valid @RequestBody CreateUserRequest req) {
		return users.createUser(req);
	}

	// ADMIN+SENIOR: update
	@PatchMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req, Authentication auth) {
		boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
		return users.updateUser(id, req, isAdmin);
	}

	// ADMIN+SENIOR: set password
	@PatchMapping("/{id}/password")
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public ResponseEntity<Void> setPassword(@PathVariable UUID id, @RequestBody Map<String, String> body) {
		users.setPassword(id, body.get("password"));
		return ResponseEntity.noContent().build();
	}
}
