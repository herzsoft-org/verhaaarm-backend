package moe.herz.verhaarmbackend.finesuggestion;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.finesuggestion.dto.CreateFineSuggestionRequest;
import moe.herz.verhaarmbackend.finesuggestion.dto.FineSuggestionDto;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fine-suggestions")
public class FineSuggestionController {

	private final FineSuggestionService fineSuggestions;
	private final UserRepository users;

	public FineSuggestionController(FineSuggestionService fineSuggestions, UserRepository users) {
		this.fineSuggestions = fineSuggestions;
		this.users = users;
	}

	@PostMapping
	public FineSuggestionDto create(@RequestBody @Valid CreateFineSuggestionRequest req, Authentication auth) {
		UserEntity actor = actor(auth);
		return fineSuggestions.create(req, actor);
	}

	@GetMapping
	public List<FineSuggestionDto> list(
			@RequestParam(required = false) String status,
			@RequestParam(required = false, defaultValue = "false") boolean mine,
			Authentication auth
	) {
		UserEntity actor = actor(auth);

		FineSuggestionStatus parsed = null;
		if (status != null && !status.isBlank()) {
			try {
				parsed = FineSuggestionStatus.valueOf(status.trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				throw moe.herz.verhaarmbackend.common.ApiErrors.badRequest("Unknown status: " + status);
			}
		}

		return fineSuggestions.listForActor(actor, parsed, mine);
	}


	@GetMapping("/{id}")
	public FineSuggestionDto get(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = actor(auth);
		return fineSuggestions.getForActor(id, actor);
	}

	@PostMapping("/{id}/accept")
	public FineSuggestionService.FineDtoAcceptResult accept(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = actor(auth);
		return fineSuggestions.accept(id, actor);
	}

	@PostMapping("/{id}/reject")
	public void reject(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = actor(auth);
		fineSuggestions.reject(id, actor);
	}

	private UserEntity actor(Authentication auth) {
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> moe.herz.verhaarmbackend.common.ApiErrors.unauthorized("Unauthorized"));
	}
}
