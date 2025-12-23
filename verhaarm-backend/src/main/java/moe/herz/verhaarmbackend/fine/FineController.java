package moe.herz.verhaarmbackend.fine;

import moe.herz.verhaarmbackend.fine.dto.CreateFineRequest;
import moe.herz.verhaarmbackend.fine.dto.FineDto;
import moe.herz.verhaarmbackend.fine.dto.UpdateFineRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fines")
public class FineController {

	private final FineService fines;
	private final UserRepository users;

	public FineController(FineService fines, UserRepository users) {
		this.fines = fines;
		this.users = users;
	}

	@GetMapping
	public List<FineDto> list(Authentication auth) {
		UserEntity actor = actor(auth);
		return fines.listForActor(actor);
	}

	@GetMapping("/{id}")
	public FineDto get(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = actor(auth);
		return fines.getForActor(id, actor);
	}

	@PostMapping
	public FineDto create(@RequestBody @Valid CreateFineRequest req, Authentication auth) {
		UserEntity actor = actor(auth);
		return fines.create(req, actor);
	}

	@PatchMapping("/{id}")
	public FineDto update(@PathVariable UUID id, @RequestBody UpdateFineRequest req, Authentication auth) {
		UserEntity actor = actor(auth);
		return fines.update(id, req, actor);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = actor(auth);
		fines.delete(id, actor);
	}

	private UserEntity actor(Authentication auth) {
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> moe.herz.verhaarmbackend.common.ApiErrors.unauthorized("Unauthorized"));
	}
}
