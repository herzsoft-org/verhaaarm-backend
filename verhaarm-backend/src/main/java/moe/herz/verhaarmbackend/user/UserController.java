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
	private final UserRepository userRepo;

	public UserController(UserService users, UserRepository userRepo) {
		this.users = users;
		this.userRepo = userRepo;
	}

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

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public List<UserDto> listUsers() {
		return users.listAll();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public UserDto get(@PathVariable UUID id) {
		return users.getUser(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public UserDto create(@Valid @RequestBody CreateUserRequest req) {
		return users.createUser(req);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req, Authentication auth) {
		UserEntity actor = resolveActor(auth);
		return users.updateUser(id, req, actor);
	}

	@PatchMapping("/{id}/password")
	@PreAuthorize("hasAnyRole('ADMIN','SENIOR')")
	public ResponseEntity<Void> setPassword(@PathVariable UUID id, @RequestBody Map<String, String> body) {
		users.setPassword(id, body.get("password"));
		return ResponseEntity.noContent().build();
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		// JwtAuthFilter typically sets auth.setName(username)
		return userRepo.findByUsername(auth.getName()).orElse(null);
	}
}
