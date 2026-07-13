package moe.herz.verhaarmbackend.ferienvertreter;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.ferienvertreter.dto.CreateFerienvertreterRequest;
import moe.herz.verhaarmbackend.ferienvertreter.dto.FerienvertreterDto;
import moe.herz.verhaarmbackend.ferienvertreter.dto.UpdateFerienvertreterRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ferienvertreter")
public class FerienvertreterController {

	private final FerienvertreterService ferienvertreter;
	private final UserRepository userRepo;

	public FerienvertreterController(FerienvertreterService ferienvertreter, UserRepository userRepo) {
		this.ferienvertreter = ferienvertreter;
		this.userRepo = userRepo;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<FerienvertreterDto> list() {
		return ferienvertreter.list();
	}

	// Anyone currently holding some Amt (auto or manual) may manage entries; enforced in
	// FerienvertreterService since it needs the actor, not a static role.
	@PostMapping
	@PreAuthorize("isAuthenticated()")
	public FerienvertreterDto create(@Valid @RequestBody CreateFerienvertreterRequest req, Authentication auth) {
		return ferienvertreter.create(req, resolveActor(auth));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public FerienvertreterDto update(
			@PathVariable UUID id,
			@RequestBody UpdateFerienvertreterRequest req,
			Authentication auth
	) {
		return ferienvertreter.update(id, req, resolveActor(auth));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
		ferienvertreter.delete(id, resolveActor(auth));
		return ResponseEntity.noContent().build();
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		return userRepo.findByUsername(auth.getName()).orElse(null);
	}
}
