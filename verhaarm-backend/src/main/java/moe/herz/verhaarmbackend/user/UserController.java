package moe.herz.verhaarmbackend.user;

import moe.herz.verhaarmbackend.user.dto.CreateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UpdateUserRequest;
import moe.herz.verhaarmbackend.user.dto.UserDto;
import moe.herz.verhaarmbackend.user.dto.UserPickerDto;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class UserController {

	private final UserService users;

	public UserController(UserService users) {
		this.users = users;
	}

	// ADMIN+SENIOR (protected by SecurityConfig)
	@GetMapping("/users")
	public List<UserDto> listUsers() {
		return users.listAll();
	}

	// ADMIN only (protected by SecurityConfig)
	@PostMapping("/users")
	public UserDto create(@Valid @RequestBody CreateUserRequest req) {
		return users.createUser(req);
	}

	// ADMIN+SENIOR (protected by SecurityConfig)
	@PatchMapping("/users/{id}")
	public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req, Authentication auth) {
		boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
		return users.updateUser(id, req, isAdmin);
	}

	// any authenticated
	@GetMapping("/users/picker")
	public List<UserPickerDto> picker(
			@RequestParam(defaultValue = "true") boolean active,
			@RequestParam(required = false) String query
	) {
		return users.picker(active, query);
	}
}
